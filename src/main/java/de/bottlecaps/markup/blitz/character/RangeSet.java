package de.bottlecaps.markup.blitz.character;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import de.bottlecaps.markup.blitz.grammar.Charset;
import de.bottlecaps.markup.blitz.grammar.ClassMember;
import de.bottlecaps.markup.blitz.grammar.Member;
import de.bottlecaps.markup.blitz.grammar.RangeMember;
import de.bottlecaps.markup.blitz.grammar.StringMember;
import de.bottlecaps.markup.blitz.grammar.Term;

public class RangeSet extends AbstractSet<Range> implements Comparable<RangeSet> {
  private static final int MAX_VALID_CODEPOINT = 0x10fffd;

  public static final Map<String, RangeSet> unicodeClasses = new ConcurrentHashMap<>();

  private final TreeSet<Range> addedRanges = new TreeSet<>();

  private RangeSet() {
  }

  public RangeSet complement() {
    return ALPHABET.minus(this);
  }

  public RangeSet union(RangeSet rangeSet) {
    Builder builder = new Builder();
    stream().forEach(builder::add);
    rangeSet.stream().forEach(builder::add);
    return builder.build().join();
  }

  public RangeSet minus(RangeSet rangeSet) {
    Iterator<Range> removeIt = rangeSet.join().iterator();
    Range remove = removeIt.hasNext() ? removeIt.next() : null;
    Iterator<Range> rangeIt = join().iterator();
    Range range = rangeIt.hasNext() ? rangeIt.next() : null;
    Builder builder = new Builder();
    while (range != null) {
      if (remove == null || range.getLastCodepoint() < remove.getFirstCodepoint()) {
        // no overlap, range smaller
        builder.add(range);
        range = rangeIt.hasNext() ? rangeIt.next() : null;
      }
      // range.getLastCodepoint() >= remove.getFirstCodepoint()
      else if (range.getFirstCodepoint() > remove.getLastCodepoint()) {
        // no overlap, remove smaller
        remove = removeIt.hasNext() ? removeIt.next() : null;
      }
      else {
        if (range.getFirstCodepoint() < remove.getFirstCodepoint()) {
          // overlap, left residual
          builder.add(range.getFirstCodepoint(), remove.getFirstCodepoint() - 1);
        }
        if (range.getLastCodepoint() > remove.getLastCodepoint()) {
          // overlap, right residual
          range = new Range(remove.getLastCodepoint() + 1, range.getLastCodepoint());
          remove = removeIt.hasNext() ? removeIt.next() : null;
        }
        else {
          range = rangeIt.hasNext() ? rangeIt.next() : null;
        }
      }
    }
    return builder.build();
  }

  public RangeSet join() {
    Integer firstCodepoint = null;
    Integer lastCodepoint = null;
    Builder builder = new Builder();
    for (Range range : split(addedRanges)) {
      if (firstCodepoint == null) {
        firstCodepoint = range.getFirstCodepoint();
      }
      else if (lastCodepoint + 1 != range.getFirstCodepoint()) {
        builder.add(new Range(firstCodepoint, lastCodepoint));
        firstCodepoint = range.getFirstCodepoint();
      }
      lastCodepoint = range.getLastCodepoint();
    }
    if (firstCodepoint != null)
      builder.add(new Range(firstCodepoint, lastCodepoint));
    return builder.build();
  }

  public RangeSet split(Set<Range> ranges) {
    TreeSet<Integer> lastCodepoints = new TreeSet<>();
    addedRanges.forEach(r -> {
      lastCodepoints.add(r.getFirstCodepoint() - 1);
      lastCodepoints.add(r.getLastCodepoint());
    });
    Builder builder = new Builder();
    for (Range range : ranges) {
      for (int firstCodepoint = range.getFirstCodepoint(), lastCodepoint;
          firstCodepoint <= range.getLastCodepoint();
          firstCodepoint = lastCodepoint + 1) {
        lastCodepoint = lastCodepoints.ceiling(firstCodepoint);
        builder.add(new Range(firstCodepoint, lastCodepoint));
      }
    }
    return builder.build();
  }

  public RangeSet split() {
    return split(addedRanges);
  }

  public int charCount() {
    return stream().mapToInt(Range::size).sum();
  }

  @Override
  public String toString() {
    return addedRanges.stream()
      .map(Range::toString)
      .collect(Collectors.joining("; ", "[", "]"));
  }

  public String toJava() {
    return addedRanges.stream()
      .map(Range::toJava)
      .collect(Collectors.joining("", "builder()", ".build()"));
  }

  @Override
  public Iterator<Range> iterator() {
    return addedRanges.iterator();
  }

  @Override
  public int size() {
    return addedRanges.size();
  }

  @Override
  public boolean add(Range e) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean addAll(Collection<? extends Range> c) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int compareTo(RangeSet o) {
    Iterator<Range> li = iterator();
    Iterator<Range> ri = o.iterator();
    while (li.hasNext() && ri.hasNext()) {
      Range ln = li.next();
      Range rn = ri.next();
      int c = ln.compareTo(rn);
      if (c != 0)
        return c;
    }
    if (! li.hasNext() && ! ri.hasNext())
      return 0;
    if (! li.hasNext())
      return -1;
    return 1;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private RangeSet set = new RangeSet();

    public Builder add(int codepoint) {
      return add(codepoint, codepoint);
    }

    public Builder add(int firstCodepoint, int lastCodepoint) {
      return add(new Range(firstCodepoint, lastCodepoint));
    }

    public Builder add(Range range) {
      set.addedRanges.add(range);
      return this;
    }

    public RangeSet build() {
      RangeSet result = set;
      set = null;
      return result;
    }
  }

  public static RangeSet of(Range... ranges) {
    Builder builder = new Builder();
    for (Range range : ranges)
      builder.add(range);
    return builder.build();
  }

  public static RangeSet of(String unicodeCharClassName) {
    return unicodeClasses.computeIfAbsent(unicodeCharClassName, RangeSet::computeFromPattern);
  }

  static RangeSet computeFromPattern(String unicodeCharClassName) {
    Builder builder = new Builder();

    int first = 0;
    int last = 0x10FFFF;

    Pattern pattern = unicodeCharClassName.equals(".")
        ? Pattern.compile("^.$", Pattern.DOTALL)
        : Pattern.compile("^\\p{" + unicodeCharClassName + "}$");

    int lo = 0;
    int hi = 0;
    Boolean matches = null;

    for (int i = first; i <= last + 1; ++i)
    {
      boolean inClass = i > last || i > 0xD7FF && i < 0xE000 || i > 0xFFFD && i < 0x10000
          ? false
          : pattern.matcher(new String(Character.toChars(i))).matches() && Character.isDefined(i);

      if (matches == null)
      {
        lo = i;
      }
      else if (matches != inClass)
      {
        if (matches)
          builder.add(new Range(lo, hi));
        lo = i;
      }
      hi = i;
      matches = inClass;
    }
    return builder.build();
  }

  public static RangeSet of(Charset c) {
    Builder builder = new Builder();
    for (Member member : c.getMembers()) {
      if (member instanceof StringMember) {
        StringMember m = (StringMember) member;
        String value = m.getValue();
        if (m.isHex()) {
          int codepoint = Integer.parseInt(value.substring(1), 16);
          builder.add(codepoint);
        }
        else {
          for (char chr : value.toCharArray())
            builder.add(chr);
        }
      }
      else if (member instanceof RangeMember) {
        builder.add(((RangeMember) member).getRange());
      }
      else if (member instanceof ClassMember) {
        of(((ClassMember) member).getValue()).forEach(builder::add);
      }
      else {
        throw new IllegalStateException();
      }
    }
    return c.isExclusion()
         ? ALPHABET.minus(builder.build())
         : builder.build();
  }

  public Term toTerm(boolean isDeleted) {
//    if (addedRanges.size() == 1 && addedRanges.iterator().next().size() == 1) {
//      int codepoint = addedRanges.iterator().next().getFirstCodepoint();
//      return Range.isAscii(codepoint)
//          ? new Literal(isDeleted, Character.toString(codepoint), false)
//          : new Literal(isDeleted, "#" + Integer.toHexString(codepoint), true);
//    }
//    else {
      Charset set = new Charset(isDeleted, false);
      for (Range range : addedRanges)
        set.getMembers().add(new RangeMember(range));
      return set;
//    }
  }

  public static final RangeSet ALPHABET = builder().add(0x0, 0x377)
      .add(0x37a, 0x37f).add(0x384, 0x38a).add(0x38c).add(0x38e, 0x3a1).add(0x3a3, 0x52f).add(0x531, 0x556).add(0x559, 0x55f).add(0x561, 0x587).add(0x589, 0x58a).add(0x58d, 0x58f).add(0x591, 0x5c7).add(0x5d0, 0x5ea).add(0x5f0, 0x5f4).add(0x600, 0x61c).add(0x61e, 0x70d).add(0x70f, 0x74a).add(0x74d, 0x7b1).add(0x7c0, 0x7fa).add(0x800, 0x82d).add(0x830, 0x83e).add(0x840, 0x85b).add(0x85e).add(0x860, 0x86a).add(0x8a0, 0x8b4).add(0x8b6, 0x8bd).add(0x8d4, 0x983).add(0x985, 0x98c).add(0x98f, 0x990).add(0x993, 0x9a8).add(0x9aa, 0x9b0).add(0x9b2).add(0x9b6, 0x9b9).add(0x9bc, 0x9c4).add(0x9c7, 0x9c8).add(0x9cb, 0x9ce).add(0x9d7).add(0x9dc, 0x9dd).add(0x9df, 0x9e3).add(0x9e6, 0x9fd).add(0xa01, 0xa03).add(0xa05, 0xa0a).add(0xa0f, 0xa10).add(0xa13, 0xa28).add(0xa2a, 0xa30).add(0xa32, 0xa33).add(0xa35, 0xa36).add(0xa38, 0xa39).add(0xa3c).add(0xa3e, 0xa42).add(0xa47, 0xa48).add(0xa4b, 0xa4d).add(0xa51).add(0xa59, 0xa5c).add(0xa5e).add(0xa66, 0xa75).add(0xa81, 0xa83).add(0xa85, 0xa8d).add(0xa8f, 0xa91).add(0xa93, 0xaa8).add(0xaaa, 0xab0).add(0xab2, 0xab3).add(0xab5, 0xab9).add(0xabc, 0xac5).add(0xac7, 0xac9).add(0xacb, 0xacd).add(0xad0).add(0xae0, 0xae3).add(0xae6, 0xaf1).add(0xaf9, 0xaff).add(0xb01, 0xb03).add(0xb05, 0xb0c).add(0xb0f, 0xb10).add(0xb13, 0xb28).add(0xb2a, 0xb30).add(0xb32, 0xb33).add(0xb35, 0xb39).add(0xb3c, 0xb44).add(0xb47, 0xb48).add(0xb4b, 0xb4d).add(0xb56, 0xb57).add(0xb5c, 0xb5d).add(0xb5f, 0xb63).add(0xb66, 0xb77).add(0xb82, 0xb83).add(0xb85, 0xb8a).add(0xb8e, 0xb90).add(0xb92, 0xb95).add(0xb99, 0xb9a).add(0xb9c).add(0xb9e, 0xb9f).add(0xba3, 0xba4).add(0xba8, 0xbaa).add(0xbae, 0xbb9).add(0xbbe, 0xbc2).add(0xbc6, 0xbc8).add(0xbca, 0xbcd).add(0xbd0).add(0xbd7).add(0xbe6, 0xbfa).add(0xc00, 0xc03).add(0xc05, 0xc0c).add(0xc0e, 0xc10).add(0xc12, 0xc28).add(0xc2a, 0xc39).add(0xc3d, 0xc44).add(0xc46, 0xc48).add(0xc4a, 0xc4d).add(0xc55, 0xc56).add(0xc58, 0xc5a).add(0xc60, 0xc63).add(0xc66, 0xc6f).add(0xc78, 0xc83).add(0xc85, 0xc8c).add(0xc8e, 0xc90).add(0xc92, 0xca8).add(0xcaa, 0xcb3).add(0xcb5, 0xcb9).add(0xcbc, 0xcc4).add(0xcc6, 0xcc8).add(0xcca, 0xccd).add(0xcd5, 0xcd6).add(0xcde).add(0xce0, 0xce3).add(0xce6, 0xcef).add(0xcf1, 0xcf2).add(0xd00, 0xd03).add(0xd05, 0xd0c).add(0xd0e, 0xd10).add(0xd12, 0xd44).add(0xd46, 0xd48).add(0xd4a, 0xd4f).add(0xd54, 0xd63).add(0xd66, 0xd7f).add(0xd82, 0xd83).add(0xd85, 0xd96).add(0xd9a, 0xdb1).add(0xdb3, 0xdbb).add(0xdbd).add(0xdc0, 0xdc6).add(0xdca).add(0xdcf, 0xdd4).add(0xdd6).add(0xdd8, 0xddf).add(0xde6, 0xdef).add(0xdf2, 0xdf4).add(0xe01, 0xe3a).add(0xe3f, 0xe5b).add(0xe81, 0xe82).add(0xe84).add(0xe87, 0xe88).add(0xe8a).add(0xe8d).add(0xe94, 0xe97).add(0xe99, 0xe9f).add(0xea1, 0xea3).add(0xea5).add(0xea7).add(0xeaa, 0xeab).add(0xead, 0xeb9).add(0xebb, 0xebd).add(0xec0, 0xec4).add(0xec6).add(0xec8, 0xecd).add(0xed0, 0xed9).add(0xedc, 0xedf).add(0xf00, 0xf47).add(0xf49, 0xf6c).add(0xf71, 0xf97).add(0xf99, 0xfbc).add(0xfbe, 0xfcc).add(0xfce, 0xfda).add(0x1000, 0x10c5).add(0x10c7).add(0x10cd).add(0x10d0, 0x1248).add(0x124a, 0x124d).add(0x1250, 0x1256).add(0x1258).add(0x125a, 0x125d).add(0x1260, 0x1288).add(0x128a, 0x128d).add(0x1290, 0x12b0).add(0x12b2, 0x12b5).add(0x12b8, 0x12be).add(0x12c0).add(0x12c2, 0x12c5).add(0x12c8, 0x12d6).add(0x12d8, 0x1310).add(0x1312, 0x1315).add(0x1318, 0x135a).add(0x135d, 0x137c).add(0x1380, 0x1399).add(0x13a0, 0x13f5).add(0x13f8, 0x13fd).add(0x1400, 0x169c).add(0x16a0, 0x16f8).add(0x1700, 0x170c).add(0x170e, 0x1714).add(0x1720, 0x1736).add(0x1740, 0x1753).add(0x1760, 0x176c).add(0x176e, 0x1770).add(0x1772, 0x1773).add(0x1780, 0x17dd).add(0x17e0, 0x17e9).add(0x17f0, 0x17f9).add(0x1800, 0x180e).add(0x1810, 0x1819).add(0x1820, 0x1877).add(0x1880, 0x18aa).add(0x18b0, 0x18f5).add(0x1900, 0x191e).add(0x1920, 0x192b).add(0x1930, 0x193b).add(0x1940).add(0x1944, 0x196d).add(0x1970, 0x1974).add(0x1980, 0x19ab).add(0x19b0, 0x19c9).add(0x19d0, 0x19da).add(0x19de, 0x1a1b).add(0x1a1e, 0x1a5e).add(0x1a60, 0x1a7c).add(0x1a7f, 0x1a89).add(0x1a90, 0x1a99).add(0x1aa0, 0x1aad).add(0x1ab0, 0x1abe).add(0x1b00, 0x1b4b).add(0x1b50, 0x1b7c).add(0x1b80, 0x1bf3).add(0x1bfc, 0x1c37).add(0x1c3b, 0x1c49).add(0x1c4d, 0x1c88).add(0x1cc0, 0x1cc7).add(0x1cd0, 0x1cf9).add(0x1d00, 0x1df9).add(0x1dfb, 0x1f15).add(0x1f18, 0x1f1d).add(0x1f20, 0x1f45).add(0x1f48, 0x1f4d).add(0x1f50, 0x1f57).add(0x1f59).add(0x1f5b).add(0x1f5d).add(0x1f5f, 0x1f7d).add(0x1f80, 0x1fb4).add(0x1fb6, 0x1fc4).add(0x1fc6, 0x1fd3).add(0x1fd6, 0x1fdb).add(0x1fdd, 0x1fef).add(0x1ff2, 0x1ff4).add(0x1ff6, 0x1ffe).add(0x2000, 0x2064).add(0x2066, 0x2071).add(0x2074, 0x208e).add(0x2090, 0x209c).add(0x20a0, 0x20bf).add(0x20d0, 0x20f0).add(0x2100, 0x218b).add(0x2190, 0x2426).add(0x2440, 0x244a).add(0x2460, 0x2b73).add(0x2b76, 0x2b95).add(0x2b98, 0x2bb9).add(0x2bbd, 0x2bc8).add(0x2bca, 0x2bd2).add(0x2bec, 0x2bef).add(0x2c00, 0x2c2e).add(0x2c30, 0x2c5e).add(0x2c60, 0x2cf3).add(0x2cf9, 0x2d25).add(0x2d27).add(0x2d2d).add(0x2d30, 0x2d67).add(0x2d6f, 0x2d70).add(0x2d7f, 0x2d96).add(0x2da0, 0x2da6).add(0x2da8, 0x2dae).add(0x2db0, 0x2db6).add(0x2db8, 0x2dbe).add(0x2dc0, 0x2dc6).add(0x2dc8, 0x2dce).add(0x2dd0, 0x2dd6).add(0x2dd8, 0x2dde).add(0x2de0, 0x2e49).add(0x2e80, 0x2e99).add(0x2e9b, 0x2ef3).add(0x2f00, 0x2fd5).add(0x2ff0, 0x2ffb).add(0x3000, 0x303f).add(0x3041, 0x3096).add(0x3099, 0x30ff).add(0x3105, 0x312e).add(0x3131, 0x318e).add(0x3190, 0x31ba).add(0x31c0, 0x31e3).add(0x31f0, 0x321e).add(0x3220, 0x4db5).add(0x4dc0, 0x9fea).add(0xa000, 0xa48c).add(0xa490, 0xa4c6).add(0xa4d0, 0xa62b).add(0xa640, 0xa6f7).add(0xa700, 0xa7ae).add(0xa7b0, 0xa7b7).add(0xa7f7, 0xa82b).add(0xa830, 0xa839).add(0xa840, 0xa877).add(0xa880, 0xa8c5).add(0xa8ce, 0xa8d9).add(0xa8e0, 0xa8fd).add(0xa900, 0xa953).add(0xa95f, 0xa97c).add(0xa980, 0xa9cd).add(0xa9cf, 0xa9d9).add(0xa9de, 0xa9fe).add(0xaa00, 0xaa36).add(0xaa40, 0xaa4d).add(0xaa50, 0xaa59).add(0xaa5c, 0xaac2).add(0xaadb, 0xaaf6).add(0xab01, 0xab06).add(0xab09, 0xab0e).add(0xab11, 0xab16).add(0xab20, 0xab26).add(0xab28, 0xab2e).add(0xab30, 0xab65).add(0xab70, 0xabed).add(0xabf0, 0xabf9).add(0xac00, 0xd7a3).add(0xd7b0, 0xd7c6).add(0xd7cb, 0xd7fb).add(0xe000, 0xfa6d).add(0xfa70, 0xfad9).add(0xfb00, 0xfb06).add(0xfb13, 0xfb17).add(0xfb1d, 0xfb36).add(0xfb38, 0xfb3c).add(0xfb3e).add(0xfb40, 0xfb41).add(0xfb43, 0xfb44).add(0xfb46, 0xfbc1).add(0xfbd3, 0xfd3f).add(0xfd50, 0xfd8f).add(0xfd92, 0xfdc7).add(0xfdf0, 0xfdfd).add(0xfe00, 0xfe19).add(0xfe20, 0xfe52).add(0xfe54, 0xfe66).add(0xfe68, 0xfe6b).add(0xfe70, 0xfe74).add(0xfe76, 0xfefc).add(0xfeff).add(0xff01, 0xffbe).add(0xffc2, 0xffc7).add(0xffca, 0xffcf).add(0xffd2, 0xffd7).add(0xffda, 0xffdc).add(0xffe0, 0xffe6).add(0xffe8, 0xffee).add(0xfff9, 0xfffd).add(0x10000, 0x1000b).add(0x1000d, 0x10026).add(0x10028, 0x1003a).add(0x1003c, 0x1003d).add(0x1003f, 0x1004d).add(0x10050, 0x1005d).add(0x10080, 0x100fa).add(0x10100, 0x10102).add(0x10107, 0x10133).add(0x10137, 0x1018e).add(0x10190, 0x1019b).add(0x101a0).add(0x101d0, 0x101fd).add(0x10280, 0x1029c).add(0x102a0, 0x102d0).add(0x102e0, 0x102fb).add(0x10300, 0x10323).add(0x1032d, 0x1034a).add(0x10350, 0x1037a).add(0x10380, 0x1039d).add(0x1039f, 0x103c3).add(0x103c8, 0x103d5).add(0x10400, 0x1049d).add(0x104a0, 0x104a9).add(0x104b0, 0x104d3).add(0x104d8, 0x104fb).add(0x10500, 0x10527).add(0x10530, 0x10563).add(0x1056f).add(0x10600, 0x10736).add(0x10740, 0x10755).add(0x10760, 0x10767).add(0x10800, 0x10805).add(0x10808).add(0x1080a, 0x10835).add(0x10837, 0x10838).add(0x1083c).add(0x1083f, 0x10855).add(0x10857, 0x1089e).add(0x108a7, 0x108af).add(0x108e0, 0x108f2).add(0x108f4, 0x108f5).add(0x108fb, 0x1091b).add(0x1091f, 0x10939).add(0x1093f).add(0x10980, 0x109b7).add(0x109bc, 0x109cf).add(0x109d2, 0x10a03).add(0x10a05, 0x10a06).add(0x10a0c, 0x10a13).add(0x10a15, 0x10a17).add(0x10a19, 0x10a33).add(0x10a38, 0x10a3a).add(0x10a3f, 0x10a47).add(0x10a50, 0x10a58).add(0x10a60, 0x10a9f).add(0x10ac0, 0x10ae6).add(0x10aeb, 0x10af6).add(0x10b00, 0x10b35).add(0x10b39, 0x10b55).add(0x10b58, 0x10b72).add(0x10b78, 0x10b91).add(0x10b99, 0x10b9c).add(0x10ba9, 0x10baf).add(0x10c00, 0x10c48).add(0x10c80, 0x10cb2).add(0x10cc0, 0x10cf2).add(0x10cfa, 0x10cff).add(0x10e60, 0x10e7e).add(0x11000, 0x1104d).add(0x11052, 0x1106f).add(0x1107f, 0x110c1).add(0x110d0, 0x110e8).add(0x110f0, 0x110f9).add(0x11100, 0x11134).add(0x11136, 0x11143).add(0x11150, 0x11176).add(0x11180, 0x111cd).add(0x111d0, 0x111df).add(0x111e1, 0x111f4).add(0x11200, 0x11211).add(0x11213, 0x1123e).add(0x11280, 0x11286).add(0x11288).add(0x1128a, 0x1128d).add(0x1128f, 0x1129d).add(0x1129f, 0x112a9).add(0x112b0, 0x112ea).add(0x112f0, 0x112f9).add(0x11300, 0x11303).add(0x11305, 0x1130c).add(0x1130f, 0x11310).add(0x11313, 0x11328).add(0x1132a, 0x11330).add(0x11332, 0x11333).add(0x11335, 0x11339).add(0x1133c, 0x11344).add(0x11347, 0x11348).add(0x1134b, 0x1134d).add(0x11350).add(0x11357).add(0x1135d, 0x11363).add(0x11366, 0x1136c).add(0x11370, 0x11374).add(0x11400, 0x11459).add(0x1145b).add(0x1145d).add(0x11480, 0x114c7).add(0x114d0, 0x114d9).add(0x11580, 0x115b5).add(0x115b8, 0x115dd).add(0x11600, 0x11644).add(0x11650, 0x11659).add(0x11660, 0x1166c).add(0x11680, 0x116b7).add(0x116c0, 0x116c9).add(0x11700, 0x11719).add(0x1171d, 0x1172b).add(0x11730, 0x1173f).add(0x118a0, 0x118f2).add(0x118ff).add(0x11a00, 0x11a47).add(0x11a50, 0x11a83).add(0x11a86, 0x11a9c).add(0x11a9e, 0x11aa2).add(0x11ac0, 0x11af8).add(0x11c00, 0x11c08).add(0x11c0a, 0x11c36).add(0x11c38, 0x11c45).add(0x11c50, 0x11c6c).add(0x11c70, 0x11c8f).add(0x11c92, 0x11ca7).add(0x11ca9, 0x11cb6).add(0x11d00, 0x11d06).add(0x11d08, 0x11d09).add(0x11d0b, 0x11d36).add(0x11d3a).add(0x11d3c, 0x11d3d).add(0x11d3f, 0x11d47).add(0x11d50, 0x11d59).add(0x12000, 0x12399).add(0x12400, 0x1246e).add(0x12470, 0x12474).add(0x12480, 0x12543).add(0x13000, 0x1342e).add(0x14400, 0x14646).add(0x16800, 0x16a38).add(0x16a40, 0x16a5e).add(0x16a60, 0x16a69).add(0x16a6e, 0x16a6f).add(0x16ad0, 0x16aed).add(0x16af0, 0x16af5).add(0x16b00, 0x16b45).add(0x16b50, 0x16b59).add(0x16b5b, 0x16b61).add(0x16b63, 0x16b77).add(0x16b7d, 0x16b8f).add(0x16f00, 0x16f44).add(0x16f50, 0x16f7e).add(0x16f8f, 0x16f9f).add(0x16fe0, 0x16fe1).add(0x17000, 0x187ec).add(0x18800, 0x18af2).add(0x1b000, 0x1b11e).add(0x1b170, 0x1b2fb).add(0x1bc00, 0x1bc6a).add(0x1bc70, 0x1bc7c).add(0x1bc80, 0x1bc88).add(0x1bc90, 0x1bc99).add(0x1bc9c, 0x1bca3).add(0x1d000, 0x1d0f5).add(0x1d100, 0x1d126).add(0x1d129, 0x1d1e8).add(0x1d200, 0x1d245).add(0x1d300, 0x1d356).add(0x1d360, 0x1d371).add(0x1d400, 0x1d454).add(0x1d456, 0x1d49c).add(0x1d49e, 0x1d49f).add(0x1d4a2).add(0x1d4a5, 0x1d4a6).add(0x1d4a9, 0x1d4ac).add(0x1d4ae, 0x1d4b9).add(0x1d4bb).add(0x1d4bd, 0x1d4c3).add(0x1d4c5, 0x1d505).add(0x1d507, 0x1d50a).add(0x1d50d, 0x1d514).add(0x1d516, 0x1d51c).add(0x1d51e, 0x1d539).add(0x1d53b, 0x1d53e).add(0x1d540, 0x1d544).add(0x1d546).add(0x1d54a, 0x1d550).add(0x1d552, 0x1d6a5).add(0x1d6a8, 0x1d7cb).add(0x1d7ce, 0x1da8b).add(0x1da9b, 0x1da9f).add(0x1daa1, 0x1daaf).add(0x1e000, 0x1e006).add(0x1e008, 0x1e018).add(0x1e01b, 0x1e021).add(0x1e023, 0x1e024).add(0x1e026, 0x1e02a).add(0x1e800, 0x1e8c4).add(0x1e8c7, 0x1e8d6).add(0x1e900, 0x1e94a).add(0x1e950, 0x1e959).add(0x1e95e, 0x1e95f).add(0x1ee00, 0x1ee03).add(0x1ee05, 0x1ee1f).add(0x1ee21, 0x1ee22).add(0x1ee24).add(0x1ee27).add(0x1ee29, 0x1ee32).add(0x1ee34, 0x1ee37).add(0x1ee39).add(0x1ee3b).add(0x1ee42).add(0x1ee47).add(0x1ee49).add(0x1ee4b).add(0x1ee4d, 0x1ee4f).add(0x1ee51, 0x1ee52).add(0x1ee54).add(0x1ee57).add(0x1ee59).add(0x1ee5b).add(0x1ee5d).add(0x1ee5f).add(0x1ee61, 0x1ee62).add(0x1ee64).add(0x1ee67, 0x1ee6a).add(0x1ee6c, 0x1ee72).add(0x1ee74, 0x1ee77).add(0x1ee79, 0x1ee7c).add(0x1ee7e).add(0x1ee80, 0x1ee89).add(0x1ee8b, 0x1ee9b).add(0x1eea1, 0x1eea3).add(0x1eea5, 0x1eea9).add(0x1eeab, 0x1eebb).add(0x1eef0, 0x1eef1).add(0x1f000, 0x1f02b).add(0x1f030, 0x1f093).add(0x1f0a0, 0x1f0ae).add(0x1f0b1, 0x1f0bf).add(0x1f0c1, 0x1f0cf).add(0x1f0d1, 0x1f0f5).add(0x1f100, 0x1f10c).add(0x1f110, 0x1f12e).add(0x1f130, 0x1f16b).add(0x1f170, 0x1f1ac).add(0x1f1e6, 0x1f202).add(0x1f210, 0x1f23b).add(0x1f240, 0x1f248).add(0x1f250, 0x1f251).add(0x1f260, 0x1f265).add(0x1f300, 0x1f6d4).add(0x1f6e0, 0x1f6ec).add(0x1f6f0, 0x1f6f8).add(0x1f700, 0x1f773).add(0x1f780, 0x1f7d4).add(0x1f800, 0x1f80b).add(0x1f810, 0x1f847).add(0x1f850, 0x1f859).add(0x1f860, 0x1f887).add(0x1f890, 0x1f8ad).add(0x1f900, 0x1f90b).add(0x1f910, 0x1f93e).add(0x1f940, 0x1f94c).add(0x1f950, 0x1f96b).add(0x1f980, 0x1f997).add(0x1f9c0).add(0x1f9d0, 0x1f9e6).add(0x20000, 0x2a6d6).add(0x2a700, 0x2b734).add(0x2b740, 0x2b81d).add(0x2b820, 0x2cea1).add(0x2ceb0, 0x2ebe0).add(0x2f800, 0x2fa1d).add(0xe0001).add(0xe0020, 0xe007f).add(0xe0100, 0xe01ef).add(0xf0000, 0xffffd).add(0x100000, MAX_VALID_CODEPOINT)
      .build();

  static {
//    Runtime.getRuntime().addShutdownHook(
//        new Thread(new Runnable() {
//          @Override
//          public void run() {
//            unicodeClasses.forEach((k, v) -> System.out.println("unicodeClasses.put(\"" + k + "\", " + v.toJava() + ");"));
//          }
//        },
//        "ShutdownHook")
//    );
    unicodeClasses.put("Mn", builder().add(0x300, 0x36f).add(0x483, 0x487).add(0x591, 0x5bd).add(0x5bf).add(0x5c1, 0x5c2).add(0x5c4, 0x5c5).add(0x5c7).add(0x610, 0x61a).add(0x64b, 0x65f).add(0x670).add(0x6d6, 0x6dc).add(0x6df, 0x6e4).add(0x6e7, 0x6e8).add(0x6ea, 0x6ed).add(0x711).add(0x730, 0x74a).add(0x7a6, 0x7b0).add(0x7eb, 0x7f3).add(0x816, 0x819).add(0x81b, 0x823).add(0x825, 0x827).add(0x829, 0x82d).add(0x859, 0x85b).add(0x8d4, 0x8e1).add(0x8e3, 0x902).add(0x93a).add(0x93c).add(0x941, 0x948).add(0x94d).add(0x951, 0x957).add(0x962, 0x963).add(0x981).add(0x9bc).add(0x9c1, 0x9c4).add(0x9cd).add(0x9e2, 0x9e3).add(0xa01, 0xa02).add(0xa3c).add(0xa41, 0xa42).add(0xa47, 0xa48).add(0xa4b, 0xa4d).add(0xa51).add(0xa70, 0xa71).add(0xa75).add(0xa81, 0xa82).add(0xabc).add(0xac1, 0xac5).add(0xac7, 0xac8).add(0xacd).add(0xae2, 0xae3).add(0xafa, 0xaff).add(0xb01).add(0xb3c).add(0xb3f).add(0xb41, 0xb44).add(0xb4d).add(0xb56).add(0xb62, 0xb63).add(0xb82).add(0xbc0).add(0xbcd).add(0xc00).add(0xc3e, 0xc40).add(0xc46, 0xc48).add(0xc4a, 0xc4d).add(0xc55, 0xc56).add(0xc62, 0xc63).add(0xc81).add(0xcbc).add(0xcbf).add(0xcc6).add(0xccc, 0xccd).add(0xce2, 0xce3).add(0xd00, 0xd01).add(0xd3b, 0xd3c).add(0xd41, 0xd44).add(0xd4d).add(0xd62, 0xd63).add(0xdca).add(0xdd2, 0xdd4).add(0xdd6).add(0xe31).add(0xe34, 0xe3a).add(0xe47, 0xe4e).add(0xeb1).add(0xeb4, 0xeb9).add(0xebb, 0xebc).add(0xec8, 0xecd).add(0xf18, 0xf19).add(0xf35).add(0xf37).add(0xf39).add(0xf71, 0xf7e).add(0xf80, 0xf84).add(0xf86, 0xf87).add(0xf8d, 0xf97).add(0xf99, 0xfbc).add(0xfc6).add(0x102d, 0x1030).add(0x1032, 0x1037).add(0x1039, 0x103a).add(0x103d, 0x103e).add(0x1058, 0x1059).add(0x105e, 0x1060).add(0x1071, 0x1074).add(0x1082).add(0x1085, 0x1086).add(0x108d).add(0x109d).add(0x135d, 0x135f).add(0x1712, 0x1714).add(0x1732, 0x1734).add(0x1752, 0x1753).add(0x1772, 0x1773).add(0x17b4, 0x17b5).add(0x17b7, 0x17bd).add(0x17c6).add(0x17c9, 0x17d3).add(0x17dd).add(0x180b, 0x180d).add(0x1885, 0x1886).add(0x18a9).add(0x1920, 0x1922).add(0x1927, 0x1928).add(0x1932).add(0x1939, 0x193b).add(0x1a17, 0x1a18).add(0x1a1b).add(0x1a56).add(0x1a58, 0x1a5e).add(0x1a60).add(0x1a62).add(0x1a65, 0x1a6c).add(0x1a73, 0x1a7c).add(0x1a7f).add(0x1ab0, 0x1abd).add(0x1b00, 0x1b03).add(0x1b34).add(0x1b36, 0x1b3a).add(0x1b3c).add(0x1b42).add(0x1b6b, 0x1b73).add(0x1b80, 0x1b81).add(0x1ba2, 0x1ba5).add(0x1ba8, 0x1ba9).add(0x1bab, 0x1bad).add(0x1be6).add(0x1be8, 0x1be9).add(0x1bed).add(0x1bef, 0x1bf1).add(0x1c2c, 0x1c33).add(0x1c36, 0x1c37).add(0x1cd0, 0x1cd2).add(0x1cd4, 0x1ce0).add(0x1ce2, 0x1ce8).add(0x1ced).add(0x1cf4).add(0x1cf8, 0x1cf9).add(0x1dc0, 0x1df9).add(0x1dfb, 0x1dff).add(0x20d0, 0x20dc).add(0x20e1).add(0x20e5, 0x20f0).add(0x2cef, 0x2cf1).add(0x2d7f).add(0x2de0, 0x2dff).add(0x302a, 0x302d).add(0x3099, 0x309a).add(0xa66f).add(0xa674, 0xa67d).add(0xa69e, 0xa69f).add(0xa6f0, 0xa6f1).add(0xa802).add(0xa806).add(0xa80b).add(0xa825, 0xa826).add(0xa8c4, 0xa8c5).add(0xa8e0, 0xa8f1).add(0xa926, 0xa92d).add(0xa947, 0xa951).add(0xa980, 0xa982).add(0xa9b3).add(0xa9b6, 0xa9b9).add(0xa9bc).add(0xa9e5).add(0xaa29, 0xaa2e).add(0xaa31, 0xaa32).add(0xaa35, 0xaa36).add(0xaa43).add(0xaa4c).add(0xaa7c).add(0xaab0).add(0xaab2, 0xaab4).add(0xaab7, 0xaab8).add(0xaabe, 0xaabf).add(0xaac1).add(0xaaec, 0xaaed).add(0xaaf6).add(0xabe5).add(0xabe8).add(0xabed).add(0xfb1e).add(0xfe00, 0xfe0f).add(0xfe20, 0xfe2f).add(0x101fd).add(0x102e0).add(0x10376, 0x1037a).add(0x10a01, 0x10a03).add(0x10a05, 0x10a06).add(0x10a0c, 0x10a0f).add(0x10a38, 0x10a3a).add(0x10a3f).add(0x10ae5, 0x10ae6).add(0x11001).add(0x11038, 0x11046).add(0x1107f, 0x11081).add(0x110b3, 0x110b6).add(0x110b9, 0x110ba).add(0x11100, 0x11102).add(0x11127, 0x1112b).add(0x1112d, 0x11134).add(0x11173).add(0x11180, 0x11181).add(0x111b6, 0x111be).add(0x111ca, 0x111cc).add(0x1122f, 0x11231).add(0x11234).add(0x11236, 0x11237).add(0x1123e).add(0x112df).add(0x112e3, 0x112ea).add(0x11300, 0x11301).add(0x1133c).add(0x11340).add(0x11366, 0x1136c).add(0x11370, 0x11374).add(0x11438, 0x1143f).add(0x11442, 0x11444).add(0x11446).add(0x114b3, 0x114b8).add(0x114ba).add(0x114bf, 0x114c0).add(0x114c2, 0x114c3).add(0x115b2, 0x115b5).add(0x115bc, 0x115bd).add(0x115bf, 0x115c0).add(0x115dc, 0x115dd).add(0x11633, 0x1163a).add(0x1163d).add(0x1163f, 0x11640).add(0x116ab).add(0x116ad).add(0x116b0, 0x116b5).add(0x116b7).add(0x1171d, 0x1171f).add(0x11722, 0x11725).add(0x11727, 0x1172b).add(0x11a01, 0x11a06).add(0x11a09, 0x11a0a).add(0x11a33, 0x11a38).add(0x11a3b, 0x11a3e).add(0x11a47).add(0x11a51, 0x11a56).add(0x11a59, 0x11a5b).add(0x11a8a, 0x11a96).add(0x11a98, 0x11a99).add(0x11c30, 0x11c36).add(0x11c38, 0x11c3d).add(0x11c3f).add(0x11c92, 0x11ca7).add(0x11caa, 0x11cb0).add(0x11cb2, 0x11cb3).add(0x11cb5, 0x11cb6).add(0x11d31, 0x11d36).add(0x11d3a).add(0x11d3c, 0x11d3d).add(0x11d3f, 0x11d45).add(0x11d47).add(0x16af0, 0x16af4).add(0x16b30, 0x16b36).add(0x16f8f, 0x16f92).add(0x1bc9d, 0x1bc9e).add(0x1d167, 0x1d169).add(0x1d17b, 0x1d182).add(0x1d185, 0x1d18b).add(0x1d1aa, 0x1d1ad).add(0x1d242, 0x1d244).add(0x1da00, 0x1da36).add(0x1da3b, 0x1da6c).add(0x1da75).add(0x1da84).add(0x1da9b, 0x1da9f).add(0x1daa1, 0x1daaf).add(0x1e000, 0x1e006).add(0x1e008, 0x1e018).add(0x1e01b, 0x1e021).add(0x1e023, 0x1e024).add(0x1e026, 0x1e02a).add(0x1e8d0, 0x1e8d6).add(0x1e944, 0x1e94a).add(0xe0100, 0xe01ef).build());
    unicodeClasses.put("Nd", builder().add('0', '9').add(0x660, 0x669).add(0x6f0, 0x6f9).add(0x7c0, 0x7c9).add(0x966, 0x96f).add(0x9e6, 0x9ef).add(0xa66, 0xa6f).add(0xae6, 0xaef).add(0xb66, 0xb6f).add(0xbe6, 0xbef).add(0xc66, 0xc6f).add(0xce6, 0xcef).add(0xd66, 0xd6f).add(0xde6, 0xdef).add(0xe50, 0xe59).add(0xed0, 0xed9).add(0xf20, 0xf29).add(0x1040, 0x1049).add(0x1090, 0x1099).add(0x17e0, 0x17e9).add(0x1810, 0x1819).add(0x1946, 0x194f).add(0x19d0, 0x19d9).add(0x1a80, 0x1a89).add(0x1a90, 0x1a99).add(0x1b50, 0x1b59).add(0x1bb0, 0x1bb9).add(0x1c40, 0x1c49).add(0x1c50, 0x1c59).add(0xa620, 0xa629).add(0xa8d0, 0xa8d9).add(0xa900, 0xa909).add(0xa9d0, 0xa9d9).add(0xa9f0, 0xa9f9).add(0xaa50, 0xaa59).add(0xabf0, 0xabf9).add(0xff10, 0xff19).add(0x104a0, 0x104a9).add(0x11066, 0x1106f).add(0x110f0, 0x110f9).add(0x11136, 0x1113f).add(0x111d0, 0x111d9).add(0x112f0, 0x112f9).add(0x11450, 0x11459).add(0x114d0, 0x114d9).add(0x11650, 0x11659).add(0x116c0, 0x116c9).add(0x11730, 0x11739).add(0x118e0, 0x118e9).add(0x11c50, 0x11c59).add(0x11d50, 0x11d59).add(0x16a60, 0x16a69).add(0x16b50, 0x16b59).add(0x1d7ce, 0x1d7ff).add(0x1e950, 0x1e959).build());
    unicodeClasses.put("Zs", builder().add(' ').add(0xa0).add(0x1680).add(0x2000, 0x200a).add(0x202f).add(0x205f).add(0x3000).build());
    unicodeClasses.put("L", builder().add('A', 'Z').add('a', 'z').add(0xaa).add(0xb5).add(0xba).add(0xc0, 0xd6).add(0xd8, 0xf6).add(0xf8, 0x2c1).add(0x2c6, 0x2d1).add(0x2e0, 0x2e4).add(0x2ec).add(0x2ee).add(0x370, 0x374).add(0x376, 0x377).add(0x37a, 0x37d).add(0x37f).add(0x386).add(0x388, 0x38a).add(0x38c).add(0x38e, 0x3a1).add(0x3a3, 0x3f5).add(0x3f7, 0x481).add(0x48a, 0x52f).add(0x531, 0x556).add(0x559).add(0x561, 0x587).add(0x5d0, 0x5ea).add(0x5f0, 0x5f2).add(0x620, 0x64a).add(0x66e, 0x66f).add(0x671, 0x6d3).add(0x6d5).add(0x6e5, 0x6e6).add(0x6ee, 0x6ef).add(0x6fa, 0x6fc).add(0x6ff).add(0x710).add(0x712, 0x72f).add(0x74d, 0x7a5).add(0x7b1).add(0x7ca, 0x7ea).add(0x7f4, 0x7f5).add(0x7fa).add(0x800, 0x815).add(0x81a).add(0x824).add(0x828).add(0x840, 0x858).add(0x860, 0x86a).add(0x8a0, 0x8b4).add(0x8b6, 0x8bd).add(0x904, 0x939).add(0x93d).add(0x950).add(0x958, 0x961).add(0x971, 0x980).add(0x985, 0x98c).add(0x98f, 0x990).add(0x993, 0x9a8).add(0x9aa, 0x9b0).add(0x9b2).add(0x9b6, 0x9b9).add(0x9bd).add(0x9ce).add(0x9dc, 0x9dd).add(0x9df, 0x9e1).add(0x9f0, 0x9f1).add(0x9fc).add(0xa05, 0xa0a).add(0xa0f, 0xa10).add(0xa13, 0xa28).add(0xa2a, 0xa30).add(0xa32, 0xa33).add(0xa35, 0xa36).add(0xa38, 0xa39).add(0xa59, 0xa5c).add(0xa5e).add(0xa72, 0xa74).add(0xa85, 0xa8d).add(0xa8f, 0xa91).add(0xa93, 0xaa8).add(0xaaa, 0xab0).add(0xab2, 0xab3).add(0xab5, 0xab9).add(0xabd).add(0xad0).add(0xae0, 0xae1).add(0xaf9).add(0xb05, 0xb0c).add(0xb0f, 0xb10).add(0xb13, 0xb28).add(0xb2a, 0xb30).add(0xb32, 0xb33).add(0xb35, 0xb39).add(0xb3d).add(0xb5c, 0xb5d).add(0xb5f, 0xb61).add(0xb71).add(0xb83).add(0xb85, 0xb8a).add(0xb8e, 0xb90).add(0xb92, 0xb95).add(0xb99, 0xb9a).add(0xb9c).add(0xb9e, 0xb9f).add(0xba3, 0xba4).add(0xba8, 0xbaa).add(0xbae, 0xbb9).add(0xbd0).add(0xc05, 0xc0c).add(0xc0e, 0xc10).add(0xc12, 0xc28).add(0xc2a, 0xc39).add(0xc3d).add(0xc58, 0xc5a).add(0xc60, 0xc61).add(0xc80).add(0xc85, 0xc8c).add(0xc8e, 0xc90).add(0xc92, 0xca8).add(0xcaa, 0xcb3).add(0xcb5, 0xcb9).add(0xcbd).add(0xcde).add(0xce0, 0xce1).add(0xcf1, 0xcf2).add(0xd05, 0xd0c).add(0xd0e, 0xd10).add(0xd12, 0xd3a).add(0xd3d).add(0xd4e).add(0xd54, 0xd56).add(0xd5f, 0xd61).add(0xd7a, 0xd7f).add(0xd85, 0xd96).add(0xd9a, 0xdb1).add(0xdb3, 0xdbb).add(0xdbd).add(0xdc0, 0xdc6).add(0xe01, 0xe30).add(0xe32, 0xe33).add(0xe40, 0xe46).add(0xe81, 0xe82).add(0xe84).add(0xe87, 0xe88).add(0xe8a).add(0xe8d).add(0xe94, 0xe97).add(0xe99, 0xe9f).add(0xea1, 0xea3).add(0xea5).add(0xea7).add(0xeaa, 0xeab).add(0xead, 0xeb0).add(0xeb2, 0xeb3).add(0xebd).add(0xec0, 0xec4).add(0xec6).add(0xedc, 0xedf).add(0xf00).add(0xf40, 0xf47).add(0xf49, 0xf6c).add(0xf88, 0xf8c).add(0x1000, 0x102a).add(0x103f).add(0x1050, 0x1055).add(0x105a, 0x105d).add(0x1061).add(0x1065, 0x1066).add(0x106e, 0x1070).add(0x1075, 0x1081).add(0x108e).add(0x10a0, 0x10c5).add(0x10c7).add(0x10cd).add(0x10d0, 0x10fa).add(0x10fc, 0x1248).add(0x124a, 0x124d).add(0x1250, 0x1256).add(0x1258).add(0x125a, 0x125d).add(0x1260, 0x1288).add(0x128a, 0x128d).add(0x1290, 0x12b0).add(0x12b2, 0x12b5).add(0x12b8, 0x12be).add(0x12c0).add(0x12c2, 0x12c5).add(0x12c8, 0x12d6).add(0x12d8, 0x1310).add(0x1312, 0x1315).add(0x1318, 0x135a).add(0x1380, 0x138f).add(0x13a0, 0x13f5).add(0x13f8, 0x13fd).add(0x1401, 0x166c).add(0x166f, 0x167f).add(0x1681, 0x169a).add(0x16a0, 0x16ea).add(0x16f1, 0x16f8).add(0x1700, 0x170c).add(0x170e, 0x1711).add(0x1720, 0x1731).add(0x1740, 0x1751).add(0x1760, 0x176c).add(0x176e, 0x1770).add(0x1780, 0x17b3).add(0x17d7).add(0x17dc).add(0x1820, 0x1877).add(0x1880, 0x1884).add(0x1887, 0x18a8).add(0x18aa).add(0x18b0, 0x18f5).add(0x1900, 0x191e).add(0x1950, 0x196d).add(0x1970, 0x1974).add(0x1980, 0x19ab).add(0x19b0, 0x19c9).add(0x1a00, 0x1a16).add(0x1a20, 0x1a54).add(0x1aa7).add(0x1b05, 0x1b33).add(0x1b45, 0x1b4b).add(0x1b83, 0x1ba0).add(0x1bae, 0x1baf).add(0x1bba, 0x1be5).add(0x1c00, 0x1c23).add(0x1c4d, 0x1c4f).add(0x1c5a, 0x1c7d).add(0x1c80, 0x1c88).add(0x1ce9, 0x1cec).add(0x1cee, 0x1cf1).add(0x1cf5, 0x1cf6).add(0x1d00, 0x1dbf).add(0x1e00, 0x1f15).add(0x1f18, 0x1f1d).add(0x1f20, 0x1f45).add(0x1f48, 0x1f4d).add(0x1f50, 0x1f57).add(0x1f59).add(0x1f5b).add(0x1f5d).add(0x1f5f, 0x1f7d).add(0x1f80, 0x1fb4).add(0x1fb6, 0x1fbc).add(0x1fbe).add(0x1fc2, 0x1fc4).add(0x1fc6, 0x1fcc).add(0x1fd0, 0x1fd3).add(0x1fd6, 0x1fdb).add(0x1fe0, 0x1fec).add(0x1ff2, 0x1ff4).add(0x1ff6, 0x1ffc).add(0x2071).add(0x207f).add(0x2090, 0x209c).add(0x2102).add(0x2107).add(0x210a, 0x2113).add(0x2115).add(0x2119, 0x211d).add(0x2124).add(0x2126).add(0x2128).add(0x212a, 0x212d).add(0x212f, 0x2139).add(0x213c, 0x213f).add(0x2145, 0x2149).add(0x214e).add(0x2183, 0x2184).add(0x2c00, 0x2c2e).add(0x2c30, 0x2c5e).add(0x2c60, 0x2ce4).add(0x2ceb, 0x2cee).add(0x2cf2, 0x2cf3).add(0x2d00, 0x2d25).add(0x2d27).add(0x2d2d).add(0x2d30, 0x2d67).add(0x2d6f).add(0x2d80, 0x2d96).add(0x2da0, 0x2da6).add(0x2da8, 0x2dae).add(0x2db0, 0x2db6).add(0x2db8, 0x2dbe).add(0x2dc0, 0x2dc6).add(0x2dc8, 0x2dce).add(0x2dd0, 0x2dd6).add(0x2dd8, 0x2dde).add(0x2e2f).add(0x3005, 0x3006).add(0x3031, 0x3035).add(0x303b, 0x303c).add(0x3041, 0x3096).add(0x309d, 0x309f).add(0x30a1, 0x30fa).add(0x30fc, 0x30ff).add(0x3105, 0x312e).add(0x3131, 0x318e).add(0x31a0, 0x31ba).add(0x31f0, 0x31ff).add(0x3400, 0x4db5).add(0x4e00, 0x9fea).add(0xa000, 0xa48c).add(0xa4d0, 0xa4fd).add(0xa500, 0xa60c).add(0xa610, 0xa61f).add(0xa62a, 0xa62b).add(0xa640, 0xa66e).add(0xa67f, 0xa69d).add(0xa6a0, 0xa6e5).add(0xa717, 0xa71f).add(0xa722, 0xa788).add(0xa78b, 0xa7ae).add(0xa7b0, 0xa7b7).add(0xa7f7, 0xa801).add(0xa803, 0xa805).add(0xa807, 0xa80a).add(0xa80c, 0xa822).add(0xa840, 0xa873).add(0xa882, 0xa8b3).add(0xa8f2, 0xa8f7).add(0xa8fb).add(0xa8fd).add(0xa90a, 0xa925).add(0xa930, 0xa946).add(0xa960, 0xa97c).add(0xa984, 0xa9b2).add(0xa9cf).add(0xa9e0, 0xa9e4).add(0xa9e6, 0xa9ef).add(0xa9fa, 0xa9fe).add(0xaa00, 0xaa28).add(0xaa40, 0xaa42).add(0xaa44, 0xaa4b).add(0xaa60, 0xaa76).add(0xaa7a).add(0xaa7e, 0xaaaf).add(0xaab1).add(0xaab5, 0xaab6).add(0xaab9, 0xaabd).add(0xaac0).add(0xaac2).add(0xaadb, 0xaadd).add(0xaae0, 0xaaea).add(0xaaf2, 0xaaf4).add(0xab01, 0xab06).add(0xab09, 0xab0e).add(0xab11, 0xab16).add(0xab20, 0xab26).add(0xab28, 0xab2e).add(0xab30, 0xab5a).add(0xab5c, 0xab65).add(0xab70, 0xabe2).add(0xac00, 0xd7a3).add(0xd7b0, 0xd7c6).add(0xd7cb, 0xd7fb).add(0xf900, 0xfa6d).add(0xfa70, 0xfad9).add(0xfb00, 0xfb06).add(0xfb13, 0xfb17).add(0xfb1d).add(0xfb1f, 0xfb28).add(0xfb2a, 0xfb36).add(0xfb38, 0xfb3c).add(0xfb3e).add(0xfb40, 0xfb41).add(0xfb43, 0xfb44).add(0xfb46, 0xfbb1).add(0xfbd3, 0xfd3d).add(0xfd50, 0xfd8f).add(0xfd92, 0xfdc7).add(0xfdf0, 0xfdfb).add(0xfe70, 0xfe74).add(0xfe76, 0xfefc).add(0xff21, 0xff3a).add(0xff41, 0xff5a).add(0xff66, 0xffbe).add(0xffc2, 0xffc7).add(0xffca, 0xffcf).add(0xffd2, 0xffd7).add(0xffda, 0xffdc).add(0x10000, 0x1000b).add(0x1000d, 0x10026).add(0x10028, 0x1003a).add(0x1003c, 0x1003d).add(0x1003f, 0x1004d).add(0x10050, 0x1005d).add(0x10080, 0x100fa).add(0x10280, 0x1029c).add(0x102a0, 0x102d0).add(0x10300, 0x1031f).add(0x1032d, 0x10340).add(0x10342, 0x10349).add(0x10350, 0x10375).add(0x10380, 0x1039d).add(0x103a0, 0x103c3).add(0x103c8, 0x103cf).add(0x10400, 0x1049d).add(0x104b0, 0x104d3).add(0x104d8, 0x104fb).add(0x10500, 0x10527).add(0x10530, 0x10563).add(0x10600, 0x10736).add(0x10740, 0x10755).add(0x10760, 0x10767).add(0x10800, 0x10805).add(0x10808).add(0x1080a, 0x10835).add(0x10837, 0x10838).add(0x1083c).add(0x1083f, 0x10855).add(0x10860, 0x10876).add(0x10880, 0x1089e).add(0x108e0, 0x108f2).add(0x108f4, 0x108f5).add(0x10900, 0x10915).add(0x10920, 0x10939).add(0x10980, 0x109b7).add(0x109be, 0x109bf).add(0x10a00).add(0x10a10, 0x10a13).add(0x10a15, 0x10a17).add(0x10a19, 0x10a33).add(0x10a60, 0x10a7c).add(0x10a80, 0x10a9c).add(0x10ac0, 0x10ac7).add(0x10ac9, 0x10ae4).add(0x10b00, 0x10b35).add(0x10b40, 0x10b55).add(0x10b60, 0x10b72).add(0x10b80, 0x10b91).add(0x10c00, 0x10c48).add(0x10c80, 0x10cb2).add(0x10cc0, 0x10cf2).add(0x11003, 0x11037).add(0x11083, 0x110af).add(0x110d0, 0x110e8).add(0x11103, 0x11126).add(0x11150, 0x11172).add(0x11176).add(0x11183, 0x111b2).add(0x111c1, 0x111c4).add(0x111da).add(0x111dc).add(0x11200, 0x11211).add(0x11213, 0x1122b).add(0x11280, 0x11286).add(0x11288).add(0x1128a, 0x1128d).add(0x1128f, 0x1129d).add(0x1129f, 0x112a8).add(0x112b0, 0x112de).add(0x11305, 0x1130c).add(0x1130f, 0x11310).add(0x11313, 0x11328).add(0x1132a, 0x11330).add(0x11332, 0x11333).add(0x11335, 0x11339).add(0x1133d).add(0x11350).add(0x1135d, 0x11361).add(0x11400, 0x11434).add(0x11447, 0x1144a).add(0x11480, 0x114af).add(0x114c4, 0x114c5).add(0x114c7).add(0x11580, 0x115ae).add(0x115d8, 0x115db).add(0x11600, 0x1162f).add(0x11644).add(0x11680, 0x116aa).add(0x11700, 0x11719).add(0x118a0, 0x118df).add(0x118ff).add(0x11a00).add(0x11a0b, 0x11a32).add(0x11a3a).add(0x11a50).add(0x11a5c, 0x11a83).add(0x11a86, 0x11a89).add(0x11ac0, 0x11af8).add(0x11c00, 0x11c08).add(0x11c0a, 0x11c2e).add(0x11c40).add(0x11c72, 0x11c8f).add(0x11d00, 0x11d06).add(0x11d08, 0x11d09).add(0x11d0b, 0x11d30).add(0x11d46).add(0x12000, 0x12399).add(0x12480, 0x12543).add(0x13000, 0x1342e).add(0x14400, 0x14646).add(0x16800, 0x16a38).add(0x16a40, 0x16a5e).add(0x16ad0, 0x16aed).add(0x16b00, 0x16b2f).add(0x16b40, 0x16b43).add(0x16b63, 0x16b77).add(0x16b7d, 0x16b8f).add(0x16f00, 0x16f44).add(0x16f50).add(0x16f93, 0x16f9f).add(0x16fe0, 0x16fe1).add(0x17000, 0x187ec).add(0x18800, 0x18af2).add(0x1b000, 0x1b11e).add(0x1b170, 0x1b2fb).add(0x1bc00, 0x1bc6a).add(0x1bc70, 0x1bc7c).add(0x1bc80, 0x1bc88).add(0x1bc90, 0x1bc99).add(0x1d400, 0x1d454).add(0x1d456, 0x1d49c).add(0x1d49e, 0x1d49f).add(0x1d4a2).add(0x1d4a5, 0x1d4a6).add(0x1d4a9, 0x1d4ac).add(0x1d4ae, 0x1d4b9).add(0x1d4bb).add(0x1d4bd, 0x1d4c3).add(0x1d4c5, 0x1d505).add(0x1d507, 0x1d50a).add(0x1d50d, 0x1d514).add(0x1d516, 0x1d51c).add(0x1d51e, 0x1d539).add(0x1d53b, 0x1d53e).add(0x1d540, 0x1d544).add(0x1d546).add(0x1d54a, 0x1d550).add(0x1d552, 0x1d6a5).add(0x1d6a8, 0x1d6c0).add(0x1d6c2, 0x1d6da).add(0x1d6dc, 0x1d6fa).add(0x1d6fc, 0x1d714).add(0x1d716, 0x1d734).add(0x1d736, 0x1d74e).add(0x1d750, 0x1d76e).add(0x1d770, 0x1d788).add(0x1d78a, 0x1d7a8).add(0x1d7aa, 0x1d7c2).add(0x1d7c4, 0x1d7cb).add(0x1e800, 0x1e8c4).add(0x1e900, 0x1e943).add(0x1ee00, 0x1ee03).add(0x1ee05, 0x1ee1f).add(0x1ee21, 0x1ee22).add(0x1ee24).add(0x1ee27).add(0x1ee29, 0x1ee32).add(0x1ee34, 0x1ee37).add(0x1ee39).add(0x1ee3b).add(0x1ee42).add(0x1ee47).add(0x1ee49).add(0x1ee4b).add(0x1ee4d, 0x1ee4f).add(0x1ee51, 0x1ee52).add(0x1ee54).add(0x1ee57).add(0x1ee59).add(0x1ee5b).add(0x1ee5d).add(0x1ee5f).add(0x1ee61, 0x1ee62).add(0x1ee64).add(0x1ee67, 0x1ee6a).add(0x1ee6c, 0x1ee72).add(0x1ee74, 0x1ee77).add(0x1ee79, 0x1ee7c).add(0x1ee7e).add(0x1ee80, 0x1ee89).add(0x1ee8b, 0x1ee9b).add(0x1eea1, 0x1eea3).add(0x1eea5, 0x1eea9).add(0x1eeab, 0x1eebb).add(0x20000, 0x2a6d6).add(0x2a700, 0x2b734).add(0x2b740, 0x2b81d).add(0x2b820, 0x2cea1).add(0x2ceb0, 0x2ebe0).add(0x2f800, 0x2fa1d).build());
  }
}