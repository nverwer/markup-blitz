package de.bottlecaps.markup.blitz.parser;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Stack;

import de.bottlecaps.markup.blitz.character.RangeSet;
import de.bottlecaps.markup.blitz.transform.CompressedMap;

public class Parser
{
  public static class ParseException extends RuntimeException
  {
    private static final long serialVersionUID = 1L;
    private int begin, end, offending, expected, state;
    private boolean ambiguousInput;
    private ParseTreeBuilder ambiguityDescriptor;

    public ParseException(int b, int e, int s, int o, int x)
    {
      begin = b;
      end = e;
      state = s;
      offending = o;
      expected = x;
      ambiguousInput = false;
    }

    public ParseException(int b, int e, ParseTreeBuilder ambiguityDescriptor)
    {
      this(b, e, 1, -1, -1);
      ambiguousInput = true;
      this.ambiguityDescriptor = ambiguityDescriptor;
    }

    @Override
    public String getMessage()
    {
      return ambiguousInput
           ? "ambiguous input"
           : offending < 0
           ? "lexical analysis failed"
           : "syntax error";
    }

    public void serialize(EventHandler eventHandler)
    {
      ambiguityDescriptor.serialize(eventHandler);
    }

    public int getBegin() {return begin;}
    public int getEnd() {return end;}
    public int getState() {return state;}
    public int getOffending() {return offending;}
    public int getExpected() {return expected;}
    public boolean isAmbiguousInput() {return ambiguousInput;}
  }

  public interface EventHandler
  {
    public void reset(String string);
    public void startNonterminal(String name, int begin);
    public void endNonterminal(String name, int end);
    public void terminal(int begin, int end);
    public void whitespace(int begin, int end);
  }

  public static abstract class Symbol
  {
    public int begin;
    public int end;

    protected Symbol(int begin, int end)
    {
      this.begin = begin;
      this.end = end;
    }

    public abstract void send(EventHandler e);
  }

  public static class Terminal extends Symbol
  {
    public Terminal(int begin, int end)
    {
      super(begin, end);
    }

    @Override
    public void send(EventHandler e)
    {
      e.terminal(begin, end);
    }
  }

  public static class Nonterminal extends Symbol
  {
    public String name;
    public  Symbol[] attributes;
    public Symbol[] children;

    public Nonterminal(String name, int begin, int end, Symbol[] attributes, Symbol[] children)
    {
      super(begin, end);
      this.name = name;
      this.attributes = attributes;
      this.children = children;
    }

    @Override
    public void send(EventHandler e)
    {
      e.startNonterminal(name, begin);
      int pos = begin;
      for (Symbol c : children)
      {
        if (pos < c.begin) e.whitespace(pos, c.begin);
        c.send(e);
        pos = c.end;
      }
      if (pos < end) e.whitespace(pos, end);
      e.endNonterminal(name, end);
    }
  }

  public interface BottomUpEventHandler
  {
    public void reset(String string);
    public void nonterminal(String name, int begin, int end, int count);
    public void terminal(int begin, int end);
  }

  public static class XmlSerializer implements EventHandler
  {
    private String input;
    private String delayedTag;
    private Writer out;
    private boolean indent;
    private boolean hasChildElement;
    private int depth;

    public XmlSerializer(Writer w, boolean indent)
    {
      input = null;
      delayedTag = null;
      out = w;
      this.indent = indent;
    }

    @Override
    public void reset(String string)
    {
      writeOutput("<?xml version=\"1.0\" encoding=\"UTF-8\"?" + ">");
      input = string;
      delayedTag = null;
      hasChildElement = false;
      depth = 0;
    }

    @Override
    public void startNonterminal(String name, int begin)
    {
      if (delayedTag != null)
      {
        writeOutput("<");
        writeOutput(delayedTag);
        writeOutput(">");
      }
      delayedTag = name;
      if (indent)
      {
        writeOutput("\n");
        for (int i = 0; i < depth; ++i)
        {
          writeOutput("  ");
        }
      }
      hasChildElement = false;
      ++depth;
    }

    @Override
    public void endNonterminal(String name, int end)
    {
      --depth;
      if (delayedTag != null)
      {
        delayedTag = null;
        writeOutput("<");
        writeOutput(name);
        writeOutput("/>");
      }
      else
      {
        if (indent)
        {
          if (hasChildElement)
          {
            writeOutput("\n");
            for (int i = 0; i < depth; ++i)
            {
              writeOutput("  ");
            }
          }
        }
        writeOutput("</");
        writeOutput(name);
        writeOutput(">");
      }
      hasChildElement = true;
    }

    @Override
    public void terminal(int begin, int end)
    {
      String name = "TOKEN";
      startNonterminal(name, begin);
      characters(begin, end);
      endNonterminal(name, end);
    }

    @Override
    public void whitespace(int begin, int end)
    {
      characters(begin, end);
    }

    private void characters(int begin, int end)
    {
      if (begin < end)
      {
        if (delayedTag != null)
        {
          writeOutput("<");
          writeOutput(delayedTag);
          writeOutput(">");
          delayedTag = null;
        }
        writeOutput(input.subSequence(begin, end)
                         .toString()
                         .replace("&", "&amp;")
                         .replace("<", "&lt;")
                         .replace(">", "&gt;"));
      }
    }

    public void writeOutput(String content)
    {
      try
      {
        out.write(content);
      }
      catch (IOException e)
      {
        throw new RuntimeException(e);
      }
    }
  }

  public static class ParseTreeBuilder implements BottomUpEventHandler {
    private String input;
    public Symbol[] stack = new Symbol[64];
    public int top = -1;

    @Override
    public void reset(String input) {
      this.input = input;
      top = -1;
    }

    @Override
    public void nonterminal(String name, int begin, int end, int count) {
      top -= count;
      int from = top + 1;
      int to = top + count + 1;

      Symbol[] children = Arrays.copyOfRange(stack, from, to);
      push(new Nonterminal(name, begin, end, null, children));
    }

    @Override
    public void terminal(int begin, int end) {
      push(new Terminal(begin, end));
    }

    public void serialize(EventHandler e) {
      e.reset(input);
      for (int i = 0; i <= top; ++i) {
        stack[i].send(e);
      }
    }

    public void push(Symbol s)
    {
      if (++top >= stack.length)
      {
        stack = Arrays.copyOf(stack, stack.length << 1);
      }
      stack[top] = s;
    }
  }

  public Parser(int[] asciiMap, CompressedMap bmpMap, int[] smpMap,
      CompressedMap terminalTransitions, int numberOfTokens,
      CompressedMap nonterminalTransitions, int numberOfNonterminals,
      ReduceArgument[] reduceArguments,
      String[] nonterminal,
      RangeSet[] terminal,
      int[] forks)
  {
    this.asciiMap = asciiMap;
    this.bmpMap = bmpMap;
    this.smpMap = smpMap;
    this.terminalTransitions = terminalTransitions;
    this.numberOfTokens = numberOfTokens;
    this.nonterminalTransitions = nonterminalTransitions;
    this.numberOfNonterminals = numberOfNonterminals;
    this.reduceArguments = reduceArguments;
    this.nonterminal = nonterminal;
    this.terminal = terminal;
    this.forks = forks;
  }

  public void initialize(String source, BottomUpEventHandler parsingEventHandler)
  {
    eventHandler = parsingEventHandler;
    input = source;
    size = source.length();
    maxId = 0;
    thread = new ParsingThread();
    thread.reset(0, 0, 0);
  }

  public RangeSet getOffendingToken(ParseException e) {
    return e.getOffending() < 0 ? null : terminal[e.getOffending()];
  }

  public String[] getExpectedTokenSet(ParseException e) {
    String[] expected = {};
    if (e.getExpected() >= 0) {
      expected = new String[]{terminal[e.getExpected()].shortName()};
    }
    else if (! e.isAmbiguousInput()) {
      expected = getTokenSet(- e.getState());
    }
    return expected;
  }

  public String getErrorMessage(ParseException e) {
    String message = e.getMessage();
    if (e.isAmbiguousInput()) {
      message += "\n";
    }
    else {
      String[] tokenSet = getExpectedTokenSet(e);
      String found = e.getOffending() < 0
                   ? null
                   : terminal[e.getOffending()].shortName();
      int size = e.getEnd() - e.getBegin();
      message += (found == null ? "" : ", found " + found)
              + "\nwhile expecting "
              + (tokenSet.length == 1 ? tokenSet[0] : Arrays.toString(tokenSet))
              + "\n"
              + (size == 0 || found != null ? "" : "after successfully scanning " + size + " characters beginning ");
    }
    String prefix = input.subSequence(0, e.getBegin()).toString();
    int line = prefix.replaceAll("[^\n]", "").length() + 1;
    int column = prefix.length() - prefix.lastIndexOf('\n');
    return message
         + "at line " + line + ", column " + column + ":\n..."
         + input.subSequence(e.getBegin(), Math.min(input.length(), e.getBegin() + 64))
         + "...";
  }

  public Nonterminal parse(String string) throws IOException {
    boolean indent = true;
    Writer w = new OutputStreamWriter(System.out, StandardCharsets.UTF_8);
    XmlSerializer s = new XmlSerializer(w, indent);
    ParseTreeBuilder b = new ParseTreeBuilder();

    initialize(string, b);
    try {
      writeTrace("<?xml version=\"1.0\" encoding=\"UTF-8\"?" + ">\n<trace>\n");
      parse_json();
      b.serialize(s);
      return (Nonterminal) b.stack[0];
    }
    catch (ParseException pe) {
      if (pe.isAmbiguousInput()) {
        pe.serialize(s);
        w.write("\n");
        w.flush();
      }
      throw new RuntimeException("ParseException:\n" + getErrorMessage(pe));
    }
    finally {
      writeTrace("</trace>\n");
      flushTrace();
      w.close();
    }
  }

  public void parse_json() {
    thread = parse(0, eventHandler, thread);
  }

  private static class StackNode {
    public int state;
    public int pos;
    public StackNode link;

    public StackNode(int state, int pos, StackNode link) {
      this.state = state;
      this.pos = pos;
      this.link = link;
    }

    @Override
    public boolean equals(Object obj) {
      StackNode lhs = this;
      StackNode rhs = (StackNode) obj;
      while (lhs != null && rhs != null) {
        if (lhs == rhs) return true;
        if (lhs.state != rhs.state) return false;
        if (lhs.pos != rhs.pos) return false;
        lhs = lhs.link;
        rhs = rhs.link;
      }
      return lhs == rhs;
    }
  }

  private abstract static class DeferredEvent {
    public DeferredEvent link;
    public int begin;
    public int end;

    public DeferredEvent(DeferredEvent link, int begin, int end) {
      this.link = link;
      this.begin = begin;
      this.end = end;
    }

    public abstract void execute(BottomUpEventHandler eventHandler);

    public void release(BottomUpEventHandler eventHandler) {
      DeferredEvent current = this;
      DeferredEvent predecessor = current.link;
      current.link = null;
      while (predecessor != null) {
        DeferredEvent next = predecessor.link;
        predecessor.link = current;
        current = predecessor;
        predecessor = next;
      }
      do {
        current.execute(eventHandler);
        current = current.link;
      }
      while (current != null);
    }

    public void show(BottomUpEventHandler eventHandler) {
      Stack<DeferredEvent> stack = new Stack<>();
      for (DeferredEvent current = this; current != null; current = current.link) {
        stack.push(current);
      }
      while (! stack.isEmpty()) {
        stack.pop().execute(eventHandler);
      }
    }
  }

  public static class TerminalEvent extends DeferredEvent {
    public TerminalEvent(DeferredEvent link, int begin, int end) {
      super(link, begin, end);
    }

    @Override
    public void execute(BottomUpEventHandler eventHandler) {
      eventHandler.terminal(begin, end);
    }

    @Override
    public String toString() {
      return "terminal(" + begin + ", " + end + ")";
    }
  }

  public static class NonterminalEvent extends DeferredEvent {
    public String name;
    public int count;

    public NonterminalEvent(DeferredEvent link, String name, int begin, int end, int count) {
      super(link, begin, end);
      this.name = name;
      this.count = count;
    }

    @Override
    public void execute(BottomUpEventHandler eventHandler) {
      eventHandler.nonterminal(name, begin, end, count);
    }

    @Override
    public String toString() {
      return "nonterminal(" + name + ", " + begin + ", " + end + ", " + count + ")";
    }
  }

  private static final int PARSING = 0;
  private static final int ACCEPTED = 1;
  private static final int ERROR = 2;

  private ParsingThread parse(int target, BottomUpEventHandler eventHandler, ParsingThread thread) {
    PriorityQueue<ParsingThread> threads = thread.open(0, eventHandler, target);
    for (;;) {
      thread = threads.poll();
      if (thread.accepted) {
        ParsingThread other = null;
        while (! threads.isEmpty()) {
          other = threads.poll();
          if (thread.e0 < other.e0)
          {
            thread = other;
            other = null;
          }
        }
        if (other != null) {
          rejectAmbiguity(thread.stack.pos, thread.e0, thread.deferredEvent, other.deferredEvent);
        }
        if (thread.deferredEvent != null) {
          thread.deferredEvent.release(eventHandler);
          thread.deferredEvent = null;
        }
        return thread;
      }

      if (! threads.isEmpty()) {
        if (threads.peek().equals(thread)) {
          rejectAmbiguity(thread.stack.pos, thread.e0, thread.deferredEvent, threads.peek().deferredEvent);
        }
      }
      else {
        if (thread.deferredEvent != null) {
          thread.deferredEvent.release(eventHandler);
          thread.deferredEvent = null;
        }
      }

      int status;
      for (;;) {
        if ((status = thread.parse()) != PARSING) break;
        if (! threads.isEmpty()) break;
      }

      if (status != ERROR) {
        threads.offer(thread);
      }
      else if (threads.isEmpty()) {
        throw new ParseException(thread.b1,
                                 thread.e1,
                                 TOKENSET[thread.state] + 1,
                                 thread.l1,
                                 -1
                                );
      }
    }
  }

  private void rejectAmbiguity(int begin, int end, DeferredEvent first, DeferredEvent second) {
    throw new UnsupportedOperationException();
//    ParseTreeBuilder treeBuilder = new ParseTreeBuilder();
//    treeBuilder.reset(input);
//    second.show(treeBuilder);
//    treeBuilder.nonterminal("ALTERNATIVE", treeBuilder.stack[0].begin, treeBuilder.stack[treeBuilder.top].end, treeBuilder.top + 1);
//    Symbol secondTree = treeBuilder.pop(1)[0];
//    first.show(treeBuilder);
//    treeBuilder.nonterminal("ALTERNATIVE", treeBuilder.stack[0].begin, treeBuilder.stack[treeBuilder.top].end, treeBuilder.top + 1);
//    treeBuilder.push(secondTree);
//    treeBuilder.nonterminal("AMBIGUOUS", treeBuilder.stack[0].begin, treeBuilder.stack[treeBuilder.top].end, 2);
//    throw new ParseException(begin, end, treeBuilder);
  }

  private ParsingThread thread = new ParsingThread();
  private BottomUpEventHandler eventHandler;
  private String input = null;
  private int size = 0;
  private int maxId = 0;
  private Writer err = new OutputStreamWriter(System.err, StandardCharsets.UTF_8);

  private class ParsingThread implements Comparable<ParsingThread> {
    public PriorityQueue<ParsingThread> threads;
    public boolean accepted;
    public StackNode stack;
    public int state;
    public int action;
    public int target;
    public DeferredEvent deferredEvent;
    public int id;

    public PriorityQueue<ParsingThread> open(int initialState, BottomUpEventHandler eh, int t) {
      accepted = false;
      target = t;
      eventHandler = eh;
      if (eventHandler != null) {
        eventHandler.reset(input);
      }
      deferredEvent = null;
      stack = new StackNode(-1, e0, null);
      state = initialState;
      action = predict(initialState);
      bw = e0;
      bs = e0;
      es = e0;
      threads = new PriorityQueue<>();
      threads.offer(this);
      return threads;
    }

    public ParsingThread copy(ParsingThread other, int action) {
      this.action = action;
      accepted = other.accepted;
      target = other.target;
      bs = other.bs;
      es = other.es;
      bw = other.bw;
      eventHandler = other.eventHandler;
      deferredEvent = other.deferredEvent;
      id = ++maxId;
      threads = other.threads;
      state = other.state;
      stack = other.stack;
      b0 = other.b0;
      e0 = other.e0;
      l1 = other.l1;
      b1 = other.b1;
      e1 = other.e1;
      end = other.end;
      return this;
    }

    @Override
    public int compareTo(ParsingThread other) {
      if (accepted != other.accepted)
        return accepted ? 1 : -1;
      int comp = e0 - other.e0;
      return comp == 0 ? id - other.id : comp;
    }

    @Override
    public boolean equals(Object obj) {
      ParsingThread other = (ParsingThread) obj;
      if (accepted != other.accepted) return false;
      if (b1 != other.b1) return false;
      if (e1 != other.e1) return false;
      if (l1 != other.l1) return false;
      if (state != other.state) return false;
      if (action != other.action) return false;
      if (! stack.equals(other.stack)) return false;
      return true;
    }

    public int parse() {
      int nonterminalId = -1;
      for (;;) {
        writeTrace("  <parse thread=\"" + id + "\" offset=\"" + e0 + "\" state=\"" + state + "\" input=\"");
        if (nonterminalId >= 0) {
          writeTrace(xmlEscape(nonterminal[nonterminalId]));
          if (l1 != 0)
            writeTrace(" ");
        }
        writeTrace(xmlEscape(lookaheadString()) + "\" action=\"");
        int argument = action >> Action.Type.BITS;
        int shift = -1;
        int reduce = -1;
        switch (action & ((1 << Action.Type.BITS) - 1)) {
        case 1: // SHIFT
          shift = argument;
          break;

        case 2: // SHIFT+REDUCE
          shift = state;
          // fall through

        case 3: // REDUCE
          reduce = argument;
          break;

        case 4: // FORK
          writeTrace("fork\"/>\n");
          threads.offer(new ParsingThread().copy(this, forks[argument]));
          action = forks[argument + 1];
          return PARSING;

        case 5: // ACCEPT
          writeTrace("accept\"/>\n");
          accepted = true;
          action = 0;
          return ACCEPTED;

        default: // ERROR
          writeTrace("fail\"/>\n");
          return ERROR;
        }

        if (shift >= 0) {
          writeTrace("shift");
          if (nonterminalId < 0)
          {
            if (eventHandler != null)
            {
              if (isUnambiguous())
              {
                eventHandler.terminal(b1, e1);
              }
              else
              {
                deferredEvent = new TerminalEvent(deferredEvent, b1, e1);
              }
            }
            es = e1;
            stack = new StackNode(state, b1, stack);
            consume(l1);
          }
          else
          {
            stack = new StackNode(state, bs, stack);
          }
          state = shift;
        }

        if (reduce < 0)
        {
          writeTrace("\"/>\n");
          action = predict(state);
          return PARSING;
        }
        else
        {
          ReduceArgument reduceArgument = reduceArguments[reduce];
          int symbols = reduceArgument.getMarks().length;
          nonterminalId = reduceArgument.getNonterminalId();
          if (shift >= 0)
          {
            writeTrace(" ");
          }
          writeTrace("reduce\" nonterminal=\"" + xmlEscape(nonterminal[nonterminalId]) + "\" count=\"" + symbols + "\"/>\n");
          if (symbols > 0)
          {
            for (int i = 1; i < symbols; i++)
            {
              stack = stack.link;
            }
            state = stack.state;
            bs = stack.pos;
            stack = stack.link;
          }
          else
          {
            bs = b1;
            es = b1;
          }
          if (nonterminalId == target && stack.link == null)
          {
            bs = bw;
            es = b1;
            bw = b1;
          }
          if (eventHandler != null)
          {
            if (isUnambiguous())
            {
              eventHandler.nonterminal(nonterminal[nonterminalId], bs, es, symbols);
            }
            else
            {
              deferredEvent = new NonterminalEvent(deferredEvent, nonterminal[nonterminalId], bs, es, symbols);
            }
          }
          action = nonterminalTransitions.get(state * numberOfNonterminals + nonterminalId);
        }
      }
    }

    public boolean isUnambiguous()
    {
      return threads.isEmpty();
    }

    public final void reset(int l, int b, int e)
    {
              b0 = b; e0 = b;
      l1 = l; b1 = b; e1 = e;
      end = e;
      maxId = 0;
      id = maxId;
    }

    private void consume(int t)
    {
      if (l1 == t)
      {
        b0 = b1; e0 = e1; l1 = 0;
      }
      else
      {
        error(b1, e1, 0, l1, t);
      }
    }

    private int error(int b, int e, int s, int l, int t)
    {
      flushTrace();
      throw new ParseException(b, e, s, l, t);
    }

    private String lookaheadString()
    {
      String result = "";
      if (l1 > 0)
      {
        result += terminal[l1].shortName();
      }
      return result;
    }

    private int     b0, e0;
    private int l1, b1, e1;
    private int bw, bs, es;
    private BottomUpEventHandler eventHandler = null;

    private int begin = 0;
    private int end = 0;

    public int predict(int state) {
      if (l1 == 0) {
        l1 = match();
        b1 = begin;
        e1 = end;
      }
      return l1 < 0
           ? 0
           : terminalTransitions.get(state * numberOfTokens + l1);
    }

    private int match() {
      writeTrace("  <tokenize thread=\"" + id + "\">\n");
      writeTrace("    <next");
      writeTrace(" offset=\"" + end + "\"");

      begin = end;
      final int charclass;
      int c0;
      if (end >= size) {
        c0 = -1;
        charclass = 0;
      }
      else {
        c0 = input.charAt(end++);
        if (c0 < 0x80) {
          if (c0 >= 32 && c0 <= 126) {
            writeTrace(" char=\"" + xmlEscape(String.valueOf((char) c0)) + "\"");
          }
          charclass = asciiMap[c0];
        }
        else if (c0 < 0xd800) {
          charclass = bmpMap.get(c0);
        }
        else
        {
          if (c0 < 0xdc00) {
            int c1 = end < size ? input.charAt(end) : 0;
            if (c1 >= 0xdc00 && c1 < 0xe000) {
              ++end;
              c0 = ((c0 & 0x3ff) << 10) + (c1 & 0x3ff) + 0x10000;
            }
          }

          final var smpMapSize = smpMap.length / 3;
          int lo = 0, hi = smpMapSize - 1;
          for (int m = hi >> 1; ; m = (hi + lo) >> 1) {
            if (smpMap[m] > c0) {hi = m - 1;}
            else if (smpMap[smpMapSize + m] < c0) {lo = m + 1;}
            else {charclass = smpMap[2 * smpMapSize + m]; break;}
            if (lo > hi) {charclass = -1; break;}
          }
        }
      }

      if (c0 >= 0)
        writeTrace(" codepoint=\"" + c0 + "\"");
      writeTrace(" class=\"" + charclass + "\"");
      writeTrace("/>\n");

      if (charclass < 0) {
        writeTrace("    <fail begin=\"" + begin + "\" end=\"" + end + "\"/>\n");
        writeTrace("  </tokenize>\n");
        end = begin;
        return -1;
      }

      writeTrace("    <done result=\"" + xmlEscape(terminal[charclass].shortName()) + "\" begin=\"" + begin + "\" end=\"" + end + "\"/>\n");
      writeTrace("  </tokenize>\n");
      return charclass;
    }
  }

  private static String xmlEscape(String s)
  {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < s.length(); ++i)
    {
      char c = s.charAt(i);
      switch (c)
      {
      case '<': sb.append("&lt;"); break;
      case '"': sb.append("&quot;"); break;
      case '&': sb.append("&amp;"); break;
      default : sb.append(c); break;
      }
    }
    return sb.toString();
  }

  public void setTraceWriter(Writer w)
  {
    err = w;
  }

  private void writeTrace(String content)
  {
    try
    {
      err.write(content);
    }
    catch (IOException e)
    {
      throw new RuntimeException(e);
    }
  }

  private void flushTrace()
  {
    try
    {
      err.flush();
    }
    catch (IOException e)
    {
      throw new RuntimeException(e);
    }
  }

  private String[] getTokenSet(int tokenSetId)
  {
    List<String> expected = new ArrayList<>();
    int s = tokenSetId < 0 ? - tokenSetId : INITIAL[tokenSetId] & 31;
    for (int i = 0; i < 31; i += 32)
    {
      int j = i;
      int i0 = (i >> 5) * 27 + s - 1;
      int f = EXPECTED[i0];
      for ( ; f != 0; f >>>= 1, ++j)
      {
        if ((f & 1) != 0)
        {
          expected.add(terminal[j].shortName());
        }
      }
    }
    return expected.toArray(new String[]{});
  }

  private final int[] asciiMap;
  private final CompressedMap bmpMap;
  private final int[] smpMap;
  private final CompressedMap terminalTransitions;
  private final int numberOfTokens;
  private final CompressedMap nonterminalTransitions;
  private final int numberOfNonterminals;
  private final ReduceArgument[] reduceArguments;
  private final String[] nonterminal;
  private final RangeSet[] terminal;
  private final int[] forks;

  private static final int[] INITIAL =
  {
    /*  0 */ 1, 2, 3, 4, 5, 6, 7, 712, 9, 10, 11, 12, 13, 14, 15, 16, 17, 722, 723, 20, 725, 22, 727, 728, 25, 26, 27
  };

  private static final int[] EXPECTED =
  {
    /*  0 */ 2048, 536870912, 16384, 65536, 131072, 524288, 75497472, 20971520, 20971528, 20971552, 20971776, 289406976,
    /* 12 */ 1094713344, 1094713352, 289407008, 1094713376, 75497552, 1438646304, 2034237472, 746600452, 2067791904,
    /* 21 */ 898184, 2109734944, 2143289376, 97821256, 366256712, 2141192190
  };

  private static final int[] TOKENSET =
  {
    /*  0 */ 24, 24, 7, 6, 13, 25, 26, 7, 20, 23, 8, 15, 12, 24, 14, 11, 26, 18, 6, 23, 10, 9, 12, 9, 11, 21, 16, 22,
    /* 28 */ 10, 8, 24, 19, 6, 24, 8, 24, 19, 17, 24, 19, 19, 0, 3, 5, 2, 5, 2, 4, 1, 2, 1
  };

}
