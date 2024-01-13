![Markup Blitz][logo]

Markup Blitz is a [parser generator][parser-generator] implemented in Java. It takes a [context-free grammar][CFG], generates a [parser][parser] for it, and uses the parser to convert input into a [parse tree][parse-tree], provided that the input conforms to the grammar.

The algorithms used are
* [LALR(1)][LALR] for parser table construction, and
* [GLR][GLR] for dynamic conflict resolution.

It also uses concepts from [REx Parser Generator][REx].

Markup Blitz is an implementation of [Invisible XML][IXML] (ixml). Please see the ixml specification for details on grammar notation and parse-tree serialization.

# Building Markup Blitz

Use JDK 11 or higher. For building Markup Blitz, clone this GitHub repository and go to the resulting directory:

```sh
git clone https://github.com/GuntherRademacher/markup-blitz.git
cd markup-blitz 
```

Then run this command, on Unix/Linux,

```sh
./gradlew clean jar
```

or this one, on Windows

```sh
gradlew clean jar
```

This creates `build/libs/markup-blitz-1.3-SNAPSHOT.jar` which serves the Markup Blitz API. It is also usable as an executable jar for standalone execution.

# Running tests

For running the tests, use this command on Unix/Linux,

```sh
./gradlew test
```

or this one on Windows:

```sh
gradlew test
```

Markup Blitz comes with a few tests, but it also passes all of the 5168 tests in the Invisible XML community project [ixml][GHIXML]. For running those as well, make sure that the [ixml][GHIXML] project is available next to the [markup-blitz][markup-blitz] project and use the above command. Executing these tests takes a few minutes, however some of the performance tests are skipped by default, because they require significantly more time or memory. For running those as well, set property `ALL_TESTS` to `true`:

```sh
./gradlew test -PALL_TESTS=true
```

(on Windows omit the leading `./`). Note that this causes JVM arguments to be set for a heap size of 16GB and a stack size of 4MB. Execution may take more than half an hour in this case.

# Markup Blitz in Eclipse

The project can be imported into Eclipse as a Gradle project.

# Markup Blitz on Maven Central

Markup Blitz is available on [Maven Central][maven-central] with groupId `de.bottlecaps` and artifactId `markup-blitz`.

# Running Markup Blitz from command line

Markup Blitz can be run from command line to process some input according to an Invisible XML grammar:

```txt
Usage: java -jar markup-blitz-1.3-SNAPSHOT.jar [<OPTION>...] [<GRAMMAR>] <INPUT>

  Compile an Invisible XML grammar, and parse input with the resulting parser.

  <GRAMMAR>          the grammar (literal, file name or URL), in ixml notation.
                     When omitted, the ixml grammar will be used.
  <INPUT>            the input (literal, file name or URL).

  <OPTION>:
    --indent         generate resulting xml with indentation.
    --trace          print parser trace.
    --fail-on-error  throw an exception instead of returning an error document.
    --timing         print timing information.
    --verbose        print intermediate results.

  A literal grammar or input must be preceded by an exclamation point (!).
  All inputs must be presented in UTF-8 encoding, and output is written in
  UTF-8 as well. Resulting XML goes to standard output, all diagnostics go
  to standard error.
```

# Running Markup Blitz online

[BaseX][BaseX] uses Markup Blitz to implement [`fn:invisible-xml`][fnInvisibleXml]. It can be tested online on [BXFiddle][BXFiddle].

# Using the Java API

A parser is generated by passing an Invisible XML grammar as a string to the `generate` method of class `de.bottlecaps.markup.Blitz`. This returns an object of type `de.bottlecaps.markup.blitz.Parser`, which has a `parse` method accepting the input string as a parameter and returns the resulting XML as a string. 

### de.bottlecaps.markup.Blitz.generate
Generate a parser from an Invisible XML grammar in ixml notation.

```java
public static Parser generate(String grammar, Option... blitzOptions) throws BlitzException
```
**Parameters:**
- `String grammar`: the Invisible XML grammar in ixml notation
- `Option... blitzOptions`: options for use at generation time and parsing time

**Returns:** `Parser`: the generated parser

**Throws:** `BlitzException`: if any error is detected while generating the parser

### de.bottlecaps.markup.blitz.Parser.parse

Parse the given input.

```java
public String parse(String input, Option... options)
```
**Parameters**:
- `String input`: the input string
- `Option options`: options for use at parsing time. If absent, any options passed at generation time will be in effect

**Returns:** `String`: the resulting XML

### de.bottlecaps.markup.Blitz.Option
Either of the `generate` and `parse` methods accepts `Option` arguments for creating extra diagnostic output. Generation time options are passed to the `Parser` object implicitly, and they are used at parsing time, when `parse` is called without any options.

```java
/** Parser and generator options. */
public enum Option {
  /**    Parser option: Generate XML with indentation.             */ INDENT,
  /**    Parser option: Print parser trace.                        */ TRACE,
  /**    Parser option: Fail on parsing error.                     */ FAIL_ON_ERROR,
  /** Generator option: Print timing information.                  */ TIMING,
  /** Generator option: Print information on intermediate results. */ VERBOSE;
}
```

# Performance

As with [REx Parser Generator][REx], the goal of Markup Blitz is to provide good performance. In general, however, REx parsers can be expected to perform much better. This is primarily because REx allows separating the specification into tokenization and parsing steps. This is in contrast to Invisible XML, which uses a uniform grammar to resolve from the start symbol down to codepoint level. Separate tokenization enables the use of algorithms optimized for this purpose, the establishment of token termination rules, and the easy accommodation of whitespace rules. Without it, all of this has to be accomplished by the parser alone, which often leads to costly handling of local ambiguities.

Some performance comparison of REx-generated parsers and Invisible XML parsers can be found in the [rex-parser-benchmark][rex-parser-benchmark] project. This also covers a measurement of Markup Blitz performance.

# License

Copyright (c) 2023-2024 Gunther Rademacher. Markup Blitz is provided under the [Apache 2 License][ASL].

# Thanks

The work in this project was supported by the [BaseX][BaseX] organization.

[<img src="https://avatars.githubusercontent.com/u/621314?s=200&v=4" alt="drawing" width="40"/>][BaseX]

[logo]: markup-blitz.svg "Markup Blitz"
[BaseX]: https://basex.org/
[ASL]: http://www.apache.org/licenses/LICENSE-2.0
[REx]: https://www.bottlecaps.de/rex
[LALR]: https://en.wikipedia.org/wiki/LALR_parser
[GLR]: https://en.wikipedia.org/wiki/GLR_parser
[rex-parser-benchmark]: https://github.com/GuntherRademacher/rex-parser-benchmark
[IXML]: https://invisiblexml.org/
[GHIXML]: https://github.com/invisibleXML/ixml
[CFG]: https://en.wikipedia.org/wiki/Context-free_grammar
[parser]: https://en.wikipedia.org/wiki/Parsing#Parser
[parse-tree]: https://en.wikipedia.org/wiki/Parse_tree
[parser-generator]: https://en.wikipedia.org/wiki/Compiler-compiler
[fnInvisibleXml]: https://qt4cg.org/pr/791/xpath-functions-40/Overview.html#func-invisible-xml
[BXFiddle]: https://bxfiddle.cloud.basexgmbh.de/
[markup-blitz]: https://github.com/GuntherRademacher/markup-blitz
[maven-central]: https://central.sonatype.com/artifact/de.bottlecaps/markup-blitz