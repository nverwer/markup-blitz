package de.bottlecaps.markup.blitz;

public interface Serializer<ResultType>
{
  public void startNonterminal(String name);

  public void endNonterminal(String name);

  public void startAttribute(String name);

  public void endAttribute();

  public void terminal(int codepoint);

  public void excluded(int length);

  public ResultType getSerialization();

}
