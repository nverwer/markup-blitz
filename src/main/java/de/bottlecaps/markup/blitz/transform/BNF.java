package de.bottlecaps.markup.blitz.transform;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.Stack;

import de.bottlecaps.markup.blitz.grammar.Alt;
import de.bottlecaps.markup.blitz.grammar.Alts;
import de.bottlecaps.markup.blitz.grammar.CharSet;
import de.bottlecaps.markup.blitz.grammar.ClassMember;
import de.bottlecaps.markup.blitz.grammar.Control;
import de.bottlecaps.markup.blitz.grammar.Grammar;
import de.bottlecaps.markup.blitz.grammar.Insertion;
import de.bottlecaps.markup.blitz.grammar.Literal;
import de.bottlecaps.markup.blitz.grammar.Mark;
import de.bottlecaps.markup.blitz.grammar.Member;
import de.bottlecaps.markup.blitz.grammar.Node;
import de.bottlecaps.markup.blitz.grammar.Nonterminal;
import de.bottlecaps.markup.blitz.grammar.RangeMember;
import de.bottlecaps.markup.blitz.grammar.Rule;
import de.bottlecaps.markup.blitz.grammar.StringMember;
import de.bottlecaps.markup.blitz.grammar.Term;

public class BNF extends Visitor {
  private List<Member> members;
  private Stack<Alts> alts = new Stack<>();
  private Grammar copy;
  private Queue<Rule> justAdded = new LinkedList<>();
  private Set<String> additionalRules = new HashSet<>();

  public BNF(Grammar g) {
    visit(g);
  }

  public Grammar get() {
    return copy;
  }

  @Override
  public void visit(Grammar g) {
    copy = new Grammar();
    // TODO: prevent duplicate rules (original + extra), making sure that result has proper mark
    super.visit(g);
    new PostProcess(copy).visit(copy);
  }

  @Override
  public void visit(Rule r) {
    super.visit(r);
    copy.addRule(new Rule(r.getMark(), r.getName(), alts.pop()));
    for (Rule rule; (rule = justAdded.poll()) != null; )
      copy.getRules().put(rule.getName(), rule);
  }

  @Override
  public void visit(Alts a) {
    alts.push(new Alts());
    super.visit(a);
    String name = a.getBnfRuleName();
    if (name == null) {
      // add to enclosing term, unless rule level Alts
      if (alts.size() != 1) {
        Alts nested = alts.pop();
        alts.peek().last().addAlts(nested);
      }
    }
    else {
      Alts pop = alts.pop();
      Nonterminal nonterminal = new Nonterminal(Mark.DELETED, name);
      alts.peek().last().getTerms().add(nonterminal);
      if (! additionalRules.contains(name)) {
        Mark mark = mark(name, a, Mark.NONE);
        Rule additionalRule = new Rule(mark, name, pop);
        additionalRules.add(additionalRule.getName());
        justAdded.offer(additionalRule);
      }
    }
  }

  private Mark mark(String name, Node context, Mark defaultMark) {
    Rule rule = context.getGrammar().getRules().get(name);
    return rule == null
        ? defaultMark
        : rule.getMark();
  }

  @Override
  public void visit(Alt a) {
    alts.peek().addAlt(new Alt());
    super.visit(a);
  }

  @Override
  public void visit(Nonterminal n) {
    alts.peek().last().getTerms().add(n.copy());
  }

  @Override
  public void visit(Literal l) {
    alts.peek().last().getTerms().add(l.copy());
  }

  @Override
  public void visit(Insertion i) {
    alts.peek().last().getTerms().add(i.copy());
  }

  @Override
  public void visit(Control c) {
    super.visit(c);
    Term separator = c.getSeparator() == null
        ? null
        : alts.peek().last().removeLast();
    Term term = alts.peek().last().removeLast();
    // TODO: add rule
    String name = c.getBnfRuleName();
    alts.peek().last().getTerms().add(new Nonterminal(Mark.DELETED, name));
    if (! additionalRules.contains(name)) {
      Rule additionalRule;
      switch (c.getOccurrence()) {
      case ONE_OR_MORE: {
          Alts alts = new Alts();
          Alt alt1 = new Alt();
          alt1.getTerms().add(term.copy());
          Alt alt2 = new Alt();
          alt2.addNonterminal(Mark.DELETED, name);
          if (separator != null)
            alt2.getTerms().add(separator.copy());
          alt2.getTerms().add(term.copy());
          alts.addAlt(alt1);
          alts.addAlt(alt2);
          additionalRule = new Rule(mark(name, c, Mark.NONE), name, alts);
          additionalRules.add(additionalRule.getName());
          justAdded.offer(additionalRule);
        }
        break;
      case ZERO_OR_MORE: {
          if (separator == null) {
            Alts alts = new Alts();
            Alt alt1 = new Alt();
            Alt alt2 = new Alt();
            alt2.addNonterminal(Mark.DELETED, name);
            alt2.getTerms().add(term.copy());
            alts.addAlt(alt1);
            alts.addAlt(alt2);
            additionalRule = new Rule(mark(name, c, Mark.NONE), name, alts);
            additionalRules.add(additionalRule.getName());
            justAdded.offer(additionalRule);
          }
          else {
            String listName = c.getListBnfRuleName(); {
              Alts alts = new Alts();
              Alt alt1 = new Alt();
              alt1.getTerms().add(term.copy());
              Alt alt2 = new Alt();
              alt2.addNonterminal(Mark.DELETED, listName);
              alt2.getTerms().add(term.copy());
              alts.addAlt(alt1);
              alts.addAlt(alt2);
              additionalRule = new Rule(mark(listName, c, Mark.NONE), listName, alts);
              additionalRules.add(additionalRule.getName());
              justAdded.offer(additionalRule);
            } {
              Alts alts = new Alts();
              Alt alt2 = new Alt();
              alt2.addNonterminal(Mark.DELETED, listName);
              alts.addAlt(new Alt());
              alts.addAlt(alt2);
              additionalRule = new Rule(mark(name, c, Mark.NONE), name, alts);
              additionalRules.add(additionalRule.getName());
              justAdded.offer(additionalRule);
            }
          }
        }
        break;
      case ZERO_OR_ONE: {
          Alts alts = new Alts();
          alts.addAlt(new Alt());
          alts.addAlt(new Alt());
          alts.last().getTerms().add(term);
          additionalRule = new Rule(mark(name, c, Mark.NONE), name, alts);
          additionalRules.add(additionalRule.getName());
          justAdded.offer(additionalRule);
        }
        break;
      default:
        throw new IllegalArgumentException();
      }
    }
  }

  @Override
  public void visit(CharSet c) {
    if (c.getBnfRuleName() != null) {
      Nonterminal nonterminal = new Nonterminal(Mark.DELETED, c.getBnfRuleName());
      alts.peek().last().getTerms().add(nonterminal);
    }
    else {
      CharSet set = new CharSet(c.isDeleted(), c.isExclusion());
      members = set.getMembers();
      for (Member member : c.getMembers())
        member.accept(this);
      alts.peek().last().getTerms().add(set);
    }
  }

  @Override
  public void visit(StringMember s) {
    members.add(s.copy());
  }

  @Override
  public void visit(RangeMember r) {
    members.add(r.copy());
  }

  @Override
  public void visit(ClassMember c) {
    members.add(c.copy());
  }
}
