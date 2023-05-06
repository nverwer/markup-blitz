package de.bottlecaps.markup.blitz.grammar;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import de.bottlecaps.markup.blitz.transform.Visitor;

public class CharSet extends Term {
  private final boolean deleted;
  private final boolean exclusion;
  private final List<Member> members;

  public CharSet(boolean deleted, boolean exclusion) {
    this.deleted = deleted;
    this.exclusion = exclusion;
    members = new ArrayList<>();
  }

  public boolean isDeleted() {
    return deleted;
  }

  public boolean isExclusion() {
    return exclusion;
  }

  public List<Member> getMembers() {
    return members;
  }

  public void addLiteral(String literal, boolean isHex) {
    members.add(new StringMember(literal, isHex));
  }

  public void addRange(String firstCodePoint, String lastCodePoint) {
    members.add(new RangeMember(firstCodePoint, lastCodePoint));
  }

  @Override
  public void accept(Visitor v) {
    v.visit(this);
  }

  public void addClass(String clazz) {
    members.add(new ClassMember(clazz));
  }

  @SuppressWarnings("unchecked")
  @Override
  public CharSet copy() {
    CharSet charSet = new CharSet(deleted, exclusion);
    for (Member member : members)
      charSet.getMembers().add(member.copy());
    return charSet;
  }

  @Override
  public String toString() {
    String prefix = (deleted ? "-" : "")
                  + (exclusion ? "~" : "")
                  + "[";
    return members.stream().map(Member::toString).collect(Collectors.joining("; ", prefix, "]"));
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + (deleted ? 1231 : 1237);
    result = prime * result + (exclusion ? 1231 : 1237);
    result = prime * result + ((members == null) ? 0 : members.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (!(obj instanceof CharSet))
      return false;
    CharSet other = (CharSet) obj;
    if (deleted != other.deleted)
      return false;
    if (exclusion != other.exclusion)
      return false;
    if (members == null) {
      if (other.members != null)
        return false;
    }
    else if (!members.equals(other.members))
      return false;
    return true;
  }
}
