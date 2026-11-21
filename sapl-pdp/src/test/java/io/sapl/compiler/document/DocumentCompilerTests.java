/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.compiler.document;

import com.github.valfirst.slf4jtest.LoggingEvent;
import io.sapl.api.functions.FunctionBroker;
import io.sapl.compiler.diagnostics.Diagnostic;
import io.sapl.compiler.diagnostics.Problem;
import io.sapl.compiler.diagnostics.QuickFix;
import io.sapl.compiler.diagnostics.Region;
import io.sapl.compiler.expressions.CompilationContext;
import io.sapl.compiler.expressions.SaplCompilerException;
import io.sapl.grammar.antlr.SAPLLexer;
import io.sapl.grammar.antlr.SAPLParser;
import io.sapl.util.PlacedDocuments.Placed;
import lombok.val;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.event.Level;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.github.valfirst.slf4jtest.TestLoggerFactory.getTestLogger;
import static io.sapl.util.PlacedDocuments.GRINNING_FACE;
import static io.sapl.util.PlacedDocuments.documentOf;
import static io.sapl.util.PlacedDocuments.placedWithEveryLineEnd;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.InstanceOfAssertFactories.list;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

@DisplayName("DocumentCompiler")
@ExtendWith(MockitoExtension.class)
class DocumentCompilerTests {

    private static final String POLICY_SET = """
            set "test-set"
            priority deny or abstain errors propagate
            policy "inner-policy" permit
            """;

    @Mock
    private FunctionBroker functionBroker;

    private CompilationContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new CompilationContext(functionBroker);
    }

    @Nested
    @DisplayName("an invalid document")
    class InvalidDocumentTests {

        static Stream<Arguments> invalidDocuments() {
            return Stream.of(arguments("a word that is no document", "xyz"),
                    arguments("stray comma after the effect",
                            "policy \"test\" permit ,{ \"key\" : \"value\" } =~ 6432"),
                    arguments("reserved var 'subject'", "policy \"p\" permit var subject = {};"),
                    arguments("reserved var 'action'", "policy \"p\" permit var action = {};"),
                    arguments("reserved var 'resource'", "policy \"p\" permit var resource = {};"),
                    arguments("reserved var 'environment'", "policy \"p\" permit var environment = {};"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidDocuments")
        @DisplayName("parseDocument returns it as an invalid document")
        void whenDocumentIsInvalidThenParseDocumentReturnsAnInvalidDocument(String description, String source) {
            val document = DocumentCompiler.parseDocument(source);

            assertThat(document.isInvalid()).isTrue();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidDocuments")
        @DisplayName("compileDocument throws for it")
        void whenDocumentIsInvalidThenCompileDocumentThrows(String description, String source) {
            assertThatThrownBy(() -> DocumentCompiler.compileDocument(source, ctx))
                    .isInstanceOf(SaplCompilerException.class);
        }
    }

    @Nested
    @DisplayName("parseDocument")
    class ParseDocumentTests {

        @Test
        @DisplayName("returns valid document for correct policy")
        void whenValidPolicyThenDocumentIsValid() {
            val document = DocumentCompiler.parseDocument("policy \"test\" permit");

            assertThat(document).returns(false, Document::isInvalid).returns(List.of(), Document::diagnostics)
                    .returns("", Document::errors);
        }

        static Stream<Arguments> documentsAndTheirType() {
            return Stream.of(arguments("a single policy", "policy \"single-policy\" deny", DocumentType.POLICY),
                    arguments("a policy set", POLICY_SET, DocumentType.POLICY_SET),
                    arguments("a document with a syntax error", "policy", DocumentType.INVALID));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("documentsAndTheirType")
        @DisplayName("identifies the type of a document")
        void whenDocumentIsParsedThenItsTypeIsIdentified(String description, String source, DocumentType expected) {
            val document = DocumentCompiler.parseDocument(source);

            assertThat(document.type()).isEqualTo(expected);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("validPolicyDefinitions")
        @DisplayName("parses valid policy variants")
        void whenValidPolicyVariantThenDocumentIsValid(String description, String policyDefinition) {
            val document = DocumentCompiler.parseDocument(policyDefinition);

            assertThat(document.isInvalid()).as(document.errors()).isFalse();
        }

        static Stream<Arguments> validPolicyDefinitions() {
            return Stream.of(arguments("policy with clause", "policy \"test\" permit true;"),
                    arguments("policy with transform", "policy \"test\" permit transform null"),
                    arguments("policy with obligation", "policy \"test\" permit obligation null"),
                    arguments("policy with advice", "policy \"test\" permit advice null"),
                    arguments("policy with import", "import simple.append policy \"test\" permit"),
                    arguments("policy with import alias", "import simple.append as concat policy \"test\" permit"),
                    arguments("policy with multiple imports",
                            "import simple.length import simple.append policy \"test\" permit"),
                    arguments("policy with complex expression", """
                            policy "complex"
                            permit
                                resource.type == "document";
                                var owner = resource.owner;
                                subject.id == owner;
                            """),
                    arguments("policy with attribute finder",
                            "policy \"test\" permit \"test\".<pip.attribute> == \"test\";"),
                    arguments("policy with environment attribute", "policy \"test\" permit <time.now> != undefined;"));
        }
    }

    @Nested
    @DisplayName("the documents of this repository")
    class RepositoryDocumentTests {

        // The valid policies of the parser and the policies the tests of this module
        // configure PDPs with. The conformance corpus is read in the profile
        // "conformance" only.
        private static final List<Path> DIRECTORIES = List
                .of(Path.of("../sapl-parser/src/test/resources/policies/valid"), Path.of("src/test/resources"));

        static Stream<Path> documentsOfThisRepository() throws IOException {
            val documents = Stream.<Path>builder();
            for (val directory : DIRECTORIES) {
                try (val files = Files.walk(directory)) {
                    files.filter(file -> file.toString().endsWith(".sapl")).sorted().forEach(documents::add);
                }
            }
            return documents.build();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("documentsOfThisRepository")
        @DisplayName("no document is refused for a failure that the checks did not foresee")
        void whenDocumentOfThisRepositoryIsParsedThenNoProblemIsAnUnforeseenFailure(Path file) throws IOException {
            val document = DocumentCompiler.parseDocument(Files.readString(file));

            assertThat(document.diagnostics()).extracting(Diagnostic::problem).doesNotContain(Problem.SYNTAX_ERROR);
        }
    }

    @Nested
    @DisplayName("compileDocument")
    class CompileDocumentTests {

        static Stream<Arguments> validDocuments() {
            return Stream.of(arguments("a policy", "policy \"test\" permit"), arguments("a policy set", POLICY_SET));
        }

        @MethodSource("validDocuments")
        @ParameterizedTest(name = "{0}")
        @DisplayName("compiles a valid document")
        void whenDocumentIsValidThenItCompiles(String description, String source) {
            assertThatCode(() -> DocumentCompiler.compileDocument(source, ctx)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("returns compiled document with metadata")
        void whenValidPolicyThenReturnsCompiledDocument() {
            val compiled = DocumentCompiler.compileDocument("policy \"test\" permit", ctx);

            assertThat(compiled).isNotNull().extracting(CompiledDocument::metadata).isNotNull();
        }

    }

    @Nested
    @DisplayName("the problems of a document")
    class ProblemsOfADocumentTests {

        private static final String COMPARISON_WITH_THE_SIGN_OF_A_DEFINITION = """
                policy "p"
                permit
                    action = "read";""";

        private static final String UNRESOLVED_FUNCTION = """
                policy "p" permit
                  foo();""";

        static Stream<Placed> comparisonsWrittenWithTheSignOfADefinition() {
            val afterTheFace = "    \"" + GRINNING_FACE + "\" = subject.mood;";
            return Stream.concat(placedWithEveryLineEnd("    action = \"read\";", "=", 3, 12, 3, 13),
                    Stream.of(new Placed("a character outside the basic plane before the place, on its line",
                            documentOf("\n", afterTheFace), "=", 3, 9, 3, 10)));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("comparisonsWrittenWithTheSignOfADefinition")
        @DisplayName("a comparison written with '=' is reported at the '=', with '==' as its quick fix")
        void whenComparisonIsWrittenWithTheSignOfADefinitionThenTheSignIsReportedWithItsQuickFix(Placed placed) {
            val document = DocumentCompiler.parseDocument(placed.document());

            assertThat(document.diagnostics()).singleElement().satisfies(diagnostic -> {
                assertThat(diagnostic.problem()).isEqualTo(Problem.ASSIGNMENT_AS_COMPARISON);
                assertThat(textOf(placed.document(), diagnostic.region())).isEqualTo(placed.text());
                assertThat(diagnostic.region())
                        .extracting(Region::line, Region::column, Region::endLine, Region::endColumn)
                        .containsExactly(placed.line(), placed.column(), placed.endLine(), placed.endColumn());
                assertThat(diagnostic.quickFix()).isEqualTo(new QuickFix(diagnostic.region(), "=="));
            });
        }

        @Test
        @DisplayName("applying the quick fix of a comparison written with '=' leaves a valid document")
        void whenQuickFixOfComparisonWithTheSignOfADefinitionIsAppliedThenTheDocumentIsValid() {
            val quickFix = Objects.requireNonNull(DocumentCompiler
                    .parseDocument(COMPARISON_WITH_THE_SIGN_OF_A_DEFINITION).diagnostics().getFirst().quickFix());
            val repaired = QuickFixes.applied(COMPARISON_WITH_THE_SIGN_OF_A_DEFINITION, List.of(quickFix));

            val document = DocumentCompiler.parseDocument(repaired);

            assertThat(document.isInvalid()).as(document.errors()).isFalse();
        }

        @Test
        @DisplayName("errors() names each problem on a line of its own: its line, its column from 1 and its message")
        void whenProblemIsReportedThenErrorsNamesItsLineItsColumnAndItsMessage() {
            val document = DocumentCompiler.parseDocument(COMPARISON_WITH_THE_SIGN_OF_A_DEFINITION);

            assertThat(document.errors()).isEqualTo("""
                    line 3:12 Wrong operator '='. '=' only defines variables. To compare two values, use '=='.
                    """);
        }

        @Test
        @DisplayName("compileDocument names the problems as errors() does")
        void whenDocumentHasAProblemThenCompileNamesItAsErrorsDoes() {
            val errors = DocumentCompiler.parseDocument(COMPARISON_WITH_THE_SIGN_OF_A_DEFINITION).errors();

            assertThatThrownBy(() -> DocumentCompiler.compileDocument(COMPARISON_WITH_THE_SIGN_OF_A_DEFINITION, ctx))
                    .isInstanceOf(SaplCompilerException.class).hasMessageContaining(errors);
        }

        @Test
        @DisplayName("a policy without name is reported at the keyword policy, after which the name is missing")
        void whenPolicyHasNoNameThenTheKeywordPolicyIsMarked() {
            val missingName = "policy permit";

            val document = DocumentCompiler.parseDocument(missingName);

            assertThat(document.diagnostics()).singleElement().satisfies(diagnostic -> {
                assertThat(diagnostic.problem()).isEqualTo(Problem.MISSING_POLICY_NAME);
                assertThat(textOf(missingName, diagnostic.region())).isEqualTo("policy");
                assertThat(diagnostic.quickFix()).isNull();
            });
        }

        @Test
        @DisplayName("a broken rule is placed like every other problem, with the column from 1")
        void whenRuleIsBrokenThenItsPlaceIsNamedLikeThatOfEveryOtherProblem() {
            val document = DocumentCompiler.parseDocument(UNRESOLVED_FUNCTION);

            assertThat(document.diagnostics()).singleElement().satisfies(diagnostic -> {
                assertThat(diagnostic.problem()).isEqualTo(Problem.NAME_NOT_IMPORTED);
                assertThat(document.errors()).isEqualTo("line 2:3 " + diagnostic.message() + "\n");
            });
        }

        static Stream<Arguments> invalidDocumentsWithTheirProblem() {
            return Stream.of(arguments("a mistake in the syntax", "policy", Problem.MISSING_POLICY_NAME),
                    arguments("an attribute finder in a schema", """
                            subject schema <time.now>
                            policy "p" permit""", Problem.ATTRIBUTE_FINDER_IN_SCHEMA),
                    arguments("a name that is not imported", UNRESOLVED_FUNCTION, Problem.NAME_NOT_IMPORTED));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidDocumentsWithTheirProblem")
        @DisplayName("parseDocument reports every problem once")
        void whenDocumentIsInvalidThenEachProblemIsReportedOnce(String description, String invalid, Problem problem) {
            val document = DocumentCompiler.parseDocument(invalid);

            assertThat(document.diagnostics()).extracting(Diagnostic::problem).containsExactly(problem);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidDocumentsWithTheirProblem")
        @DisplayName("compileDocument names every problem once")
        void whenDocumentIsInvalidThenCompileNamesEachProblemOnce(String description, String invalid, Problem problem) {
            val message = DocumentCompiler.parseDocument(invalid).diagnostics().getFirst().message();

            assertThatThrownBy(() -> DocumentCompiler.compileDocument(invalid, ctx))
                    .isInstanceOf(SaplCompilerException.class).message().containsOnlyOnce(message);
        }
    }

    @Nested
    @DisplayName("concurrent compilation")
    class ConcurrentCompilationTests {

        // Single-part attribute name shared by both documents. Document A imports it
        // (so it resolves). Document B does not import it (so it must stay
        // unresolved). If two parseDocument calls share mutable transformer state,
        // B can pick up A's import map and resolve a name the author never imported,
        // or A can lose its import map and fail a valid policy.
        private static final String IMPORTING_DOCUMENT = """
                import library.clash
                policy "importing" permit "x".<clash> == "x";
                """;

        private static final String NON_IMPORTING_DOCUMENT = """
                policy "non-importing" permit "x".<clash> == "x";
                """;

        private static final int ROUNDS  = 2000;
        private static final int THREADS = 8;

        @Test
        @DisplayName("each document resolves single-part names only against its own imports under concurrency")
        void whenTwoDocumentsWithCollidingShortNameCompiledInParallelThenEachResolvesAgainstOwnImports()
                throws Exception {
            val executor = Executors.newFixedThreadPool(THREADS);
            try {
                val tasks   = buildTasks();
                val futures = executor.invokeAll(tasks);
                assertThat(futures).allSatisfy(DocumentCompilerTests::assertSucceeded);
            } finally {
                executor.shutdownNow();
            }
        }

        private List<Callable<Void>> buildTasks() {
            return IntStream.range(0, ROUNDS).mapToObj(this::alternatingTask).toList();
        }

        private Callable<Void> alternatingTask(int round) {
            if (round % 2 == 0) {
                return () -> {
                    val document = DocumentCompiler.parseDocument(IMPORTING_DOCUMENT);
                    assertThat(document.isInvalid())
                            .as("imported short name 'clash' must resolve in the importing document").isFalse();
                    return null;
                };
            }
            return () -> {
                val document = DocumentCompiler.parseDocument(NON_IMPORTING_DOCUMENT);
                assertThat(document).satisfies(d -> {
                    assertThat(d.isInvalid())
                            .as("un-imported short name 'clash' must stay unresolved in the non-importing document")
                            .isTrue();
                    assertThat(d.errors()).contains("clash");
                });
                return null;
            };
        }
    }

    private static void assertSucceeded(Future<Void> future) {
        assertThatCode(future::get).doesNotThrowAnyException();
    }

    private static String textOf(String document, Region region) {
        return document.substring(region.start(), region.end());
    }

    @Nested
    @DisplayName("trojan source protection")
    class TrojanSourceProtectionTests {

        private static final char LRI = '\u2066';
        private static final char RLI = '\u2067';
        private static final char PDI = '\u2069';
        private static final char RLO = '\u202E';

        private static final String POLICY_COMPARING_SUBJECT_TO_TEXT = """
                policy "p" permit subject == "a%sb";""";

        @ValueSource(chars = { LRI, RLI, PDI, RLO })
        @ParameterizedTest(name = "reports input containing {0}")
        @DisplayName("parseDocument reports a trojan source character at its place instead of throwing")
        void whenTrojanCharacterInParseDocumentThenItIsReportedAtItsPlace(char trojanChar) {
            val malicious = "policy \"te" + trojanChar + "st\" permit";

            val document = DocumentCompiler.parseDocument(malicious);

            assertThat(document.diagnostics()).singleElement().satisfies(diagnostic -> {
                assertThat(diagnostic.problem()).isEqualTo(Problem.BIDIRECTIONAL_CONTROL);
                assertThat(textOf(malicious, diagnostic.region())).isEqualTo(String.valueOf(trojanChar));
            });
        }

        @ValueSource(chars = { LRI, RLI, PDI, RLO })
        @ParameterizedTest(name = "rejects input containing {0}")
        @DisplayName("compileDocument rejects trojan source characters")
        void whenTrojanCharacterInCompileDocumentThenThrows(char trojanChar) {
            val malicious = "policy \"te" + trojanChar + "st\" permit";
            val errors    = DocumentCompiler.parseDocument(malicious).errors();

            assertThatThrownBy(() -> DocumentCompiler.compileDocument(malicious, ctx))
                    .isInstanceOf(SaplCompilerException.class).hasMessageContaining(errors);
        }

        static Stream<Arguments> documentsWithEscapedTrojanCharacters() {
            val everyCharacterInAStringLiteral = Stream
                    .of("202A", "202B", "202C", "202D", "202E", "2066", "2067", "2068", "2069", "202e")
                    .map(code -> arguments("\\u" + code + " in a string literal",
                            POLICY_COMPARING_SUBJECT_TO_TEXT.formatted("\\u" + code), "\\u" + code));
            val otherPlacements                = Stream.of(arguments("\\u202E in a policy name", """
                    policy "te\\u202Est" permit""", "\\u202E"), arguments("\\u202E in a single-quoted string", """
                    policy "p" permit 'a\\u202Eb' == subject;""", "\\u202E"), arguments("\\u2066 in an object key", """
                    policy "p" permit { "k\\u2066": 1 } == subject;""", "\\u2066"),
                    arguments("\\u2069 in a key step", """
                            policy "p" permit subject."a\\u2069b" == 1;""", "\\u2069"),
                    arguments("\\u202E after an escaped backslash", """
                            policy "p" permit "a\\\\\\u202Eb" == 1;""", "\\u202E"));
            return Stream.concat(everyCharacterInAStringLiteral, otherPlacements);
        }

        @ParameterizedTest(name = "reports {0}")
        @MethodSource("documentsWithEscapedTrojanCharacters")
        @DisplayName("parseDocument reports a string escape that denotes a trojan source character at the escape")
        void whenStringEscapeDenotesTrojanCharacterThenItIsReportedAtTheEscape(String description, String malicious,
                String escape) {
            val document = DocumentCompiler.parseDocument(malicious);

            assertThat(document.diagnostics()).singleElement().satisfies(diagnostic -> {
                assertThat(diagnostic.problem()).isEqualTo(Problem.ESCAPED_BIDIRECTIONAL_CONTROL);
                assertThat(textOf(malicious, diagnostic.region())).isEqualTo(escape);
            });
        }

        @Test
        @DisplayName("parseDocument accepts the code of a trojan source character after an escaped backslash")
        void whenBackslashIsEscapedThenFollowingCodeIsPlainText() {
            val harmless = POLICY_COMPARING_SUBJECT_TO_TEXT.formatted("\\\\u202E");

            val document = DocumentCompiler.parseDocument(harmless);

            assertThat(document.isInvalid()).as(document.errors()).isFalse();
        }
    }

    @Nested
    @DisplayName("string literals")
    class StringLiteralTests {

        private static final String POLICY_COMPARING_SUBJECT_TO = """
                policy "p" permit subject == %s;""";

        static Stream<Arguments> stringsWithLineBreaks() {
            return Stream.of(arguments("a line feed", "\"re\nad\""),
                    arguments("a carriage return and a line feed", "\"re\r\nad\""),
                    arguments("a carriage return", "\"re\rad\""),
                    arguments("a line feed in single quotes", "'re\nad'"));
        }

        @MethodSource("stringsWithLineBreaks")
        @ParameterizedTest(name = "rejects a string that holds {0}")
        @DisplayName("parseDocument rejects a string that holds a line break")
        void whenStringHoldsLineBreakThenDocumentIsInvalid(String description, String literal) {
            val document = DocumentCompiler.parseDocument(POLICY_COMPARING_SUBJECT_TO.formatted(literal));

            assertThat(document.isInvalid()).isTrue();
        }

        static Stream<Arguments> stringsWithLoneSurrogates() {
            return Stream.of(arguments("the first half alone", "\"\\uD83D\""),
                    arguments("the second half alone", "\"\\uDE00\""),
                    arguments("the halves with a letter between them", "\"\\uD83Dx\\uDE00\""),
                    arguments("the halves in the wrong order", "\"\\uDE00\\uD83D\""),
                    arguments("a first half before a complete pair", "\"\\uD83D\\uD83D\\uDE00\""),
                    arguments("a half in lower case", "\"\\ud83d\""),
                    arguments("a half in single quotes", "'\\uD83D'"));
        }

        @ParameterizedTest(name = "rejects {0}")
        @MethodSource("stringsWithLoneSurrogates")
        @DisplayName("parseDocument rejects a string escape that is one half of a surrogate pair")
        void whenStringHoldsLoneSurrogateThenDocumentIsInvalid(String description, String literal) {
            val document = DocumentCompiler.parseDocument(POLICY_COMPARING_SUBJECT_TO.formatted(literal));

            assertThat(document).satisfies(d -> {
                assertThat(d.isInvalid()).isTrue();
                assertThat(d.diagnostics()).singleElement().satisfies(diagnostic -> {
                    assertThat(diagnostic.problem()).isEqualTo(Problem.LONE_SURROGATE_ESCAPE);
                    assertThat(diagnostic.region().column()).isEqualTo(31);
                });
            });
        }

        static Stream<Arguments> stringsThatStayValid() {
            return Stream.of(arguments("a line break written as an escape", "\"re\\nad\""),
                    arguments("a complete surrogate pair", "\"\\uD83D\\uDE00\""),
                    arguments("the code of a half after an escaped backslash", "\"\\\\uD83D\""),
                    arguments("a character outside the basic plane written as itself", "\"\uD83D\uDE00\""));
        }

        @MethodSource("stringsThatStayValid")
        @ParameterizedTest(name = "accepts {0}")
        @DisplayName("parseDocument accepts strings that only resemble the refused ones")
        void whenStringOnlyResemblesRefusedOnesThenDocumentIsValid(String description, String literal) {
            val document = DocumentCompiler.parseDocument(POLICY_COMPARING_SUBJECT_TO.formatted(literal));

            assertThat(document.isInvalid()).as(document.errors()).isFalse();
        }
    }

    @Nested
    @DisplayName("resource limits")
    class ResourceLimitTests {

        private static final int OVER_SIZE_LIMIT             = 2 * 1024 * 1024 + 1;
        private static final int DEEP_NESTING                = 50_000;
        private static final int FLAT_BINARY_CHAIN_OPERATORS = 10_000;
        private static final int NESTING_LIMIT               = DocumentCompiler.MAX_NESTING_DEPTH;
        private static final int SIZE_LIMIT                  = DocumentCompiler.MAX_DOCUMENT_SIZE_BYTES;
        private static final int LONGEST_IMPORT_LINE         = 32;

        private static final String UNFORESEEN_FAILURE = "Unresolved reference 'foo': not imported and not fully qualified.";

        private static final String POLICY_WITH_CONDITION = """
                policy "x" permit %s;""";
        private static final String OPENING_OBJECT        = """
                {"k":""";

        private static String deeplyNestedPolicy() {
            return "policy \"x\" permit " + "(".repeat(DEEP_NESTING) + "true" + ")".repeat(DEEP_NESTING);
        }

        private static String flatBinaryChainPolicy() {
            return "policy \"x\" permit " + "1" + " + 1".repeat(FLAT_BINARY_CHAIN_OPERATORS) + " == "
                    + (FLAT_BINARY_CHAIN_OPERATORS + 1) + ";";
        }

        static Stream<Arguments> documentsBeyondALimit() {
            return Stream.of(
                    arguments("an oversized document", "a".repeat(OVER_SIZE_LIMIT), Problem.DOCUMENT_TOO_LARGE),
                    arguments("deeply nested input", deeplyNestedPolicy(), Problem.NESTING_TOO_DEEP));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("documentsBeyondALimit")
        @DisplayName("parseDocument rejects a document beyond a limit instead of parsing it")
        void whenDocumentIsBeyondALimitThenDocumentIsInvalid(String description, String source, Problem problem) {
            val document = DocumentCompiler.parseDocument(source);

            assertThat(document.diagnostics()).extracting(Diagnostic::problem).containsExactly(problem);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("documentsBeyondALimit")
        @DisplayName("compileDocument throws on a document beyond a limit")
        void whenDocumentIsBeyondALimitThenCompileThrows(String description, String source, Problem problem) {
            val errors = DocumentCompiler.parseDocument(source).errors();

            assertThatThrownBy(() -> DocumentCompiler.compileDocument(source, ctx))
                    .isInstanceOf(SaplCompilerException.class).hasMessageContaining(errors);
        }

        /**
         * Each import is a part of the document before its policy. A document of the
         * size limit holds well over 100000 of them.
         */
        private static String importsUpToTheSizeLimit() {
            val policy   = "policy \"x\" permit";
            val document = new StringBuilder();
            for (var number = 0; document.length() + LONGEST_IMPORT_LINE + policy.length() <= SIZE_LIMIT; number++) {
                document.append("import lib.f").append(number).append('\n');
            }
            return document.append(policy).toString();
        }

        @Test
        @Timeout(30)
        @DisplayName("parseDocument reads a document of the size limit that holds as many imports as fit")
        void whenDocumentHoldsAsManyImportsAsFitThenItIsReadInTime() {
            val document = importsUpToTheSizeLimit();

            val parsed = DocumentCompiler.parseDocument(document);

            assertThat(parsed.isInvalid()).as(parsed.errors()).isFalse();
        }

        @Test
        @DisplayName("compileDocument rejects a flat binary expression chain that is too deep")
        void whenFlatBinaryExpressionChainIsTooDeepThenCompileThrowsCompilerException() {
            val              flatChain = flatBinaryChainPolicy();
            ThrowingCallable compile   = () -> DocumentCompiler.compileDocument(flatChain, ctx);

            assertThatThrownBy(compile).isInstanceOf(SaplCompilerException.class).hasMessageContaining("too deep");
        }

        private static String nested(String opening, String closing, int depth) {
            return opening.repeat(depth) + "true" + closing.repeat(depth);
        }

        static Stream<Arguments> conditionsWithinNestingLimit() {
            val brackets = "([{".repeat(NESTING_LIMIT + 1);
            return Stream.of(arguments("parentheses nested to the limit", nested("(", ")", NESTING_LIMIT)),
                    arguments("arrays nested to the limit", nested("[", "]", NESTING_LIMIT)),
                    arguments("objects nested to the limit", nested(OPENING_OBJECT, "}", NESTING_LIMIT)),
                    arguments("different brackets nested to the limit", nested("([", "])", NESTING_LIMIT / 2)),
                    arguments("brackets next to each other", "[" + "[1],".repeat(4 * NESTING_LIMIT) + "[1]]"),
                    arguments("brackets inside a comment", "/* " + brackets + " */ true"),
                    arguments("brackets inside a string", '"' + brackets + '"'));
        }

        @ParameterizedTest(name = "accepts {0}")
        @MethodSource("conditionsWithinNestingLimit")
        @DisplayName("parseDocument accepts documents within the nesting limit")
        void whenNestingIsWithinLimitThenDocumentIsValid(String description, String condition) {
            val withinLimit = POLICY_WITH_CONDITION.formatted(condition);

            val document = DocumentCompiler.parseDocument(withinLimit);

            assertThat(document.isInvalid()).as(document.errors()).isFalse();
        }

        static Stream<Arguments> conditionsBeyondNestingLimit() {
            val beyondLimit = NESTING_LIMIT + 1;
            return Stream.of(arguments("parentheses nested beyond the limit", nested("(", ")", beyondLimit)),
                    arguments("arrays nested beyond the limit", nested("[", "]", beyondLimit)),
                    arguments("objects nested beyond the limit", nested(OPENING_OBJECT, "}", beyondLimit)),
                    arguments("different brackets nested beyond the limit",
                            "(" + nested("([", "])", NESTING_LIMIT / 2) + ")"),
                    arguments("closing brackets followed by nesting beyond the limit",
                            ")".repeat(beyondLimit) + nested("(", ")", beyondLimit)));
        }

        @ParameterizedTest(name = "rejects {0}")
        @MethodSource("conditionsBeyondNestingLimit")
        @DisplayName("parseDocument rejects documents nested beyond the limit")
        void whenNestingExceedsLimitThenDocumentIsInvalid(String description, String condition) {
            val beyondLimit = POLICY_WITH_CONDITION.formatted(condition);

            val document = DocumentCompiler.parseDocument(beyondLimit);

            assertThat(document.diagnostics()).extracting(Diagnostic::problem).contains(Problem.NESTING_TOO_DEEP);
        }

        @Test
        @DisplayName("parseDocument rejects prefix operators chained deeply without brackets, naming the chain and not the brackets")
        void whenPrefixOperatorsChainDeeplyWithoutBracketsThenDocumentIsRefusedNamingTheChain() {
            val chain = POLICY_WITH_CONDITION.formatted("!".repeat(DEEP_NESTING) + "true");

            val document = DocumentCompiler.parseDocument(chain);

            assertThat(document.diagnostics()).extracting(Diagnostic::problem, Diagnostic::explanation).contains(tuple(
                    Problem.NESTING_TOO_DEEP_WITHOUT_BRACKETS,
                    "This expression nests too deeply to be read, for example through a long chain of '!', '-', '+' or '::'."));
        }

        @Test
        @DisplayName("parseDocument reports a failure of the transformation that the analysis did not foresee instead of throwing it")
        void whenTransformationFailsUnforeseenThenTheFailureIsReported() {
            val analysis   = SyntaxAnalysis.of(POLICY_WITH_CONDITION.formatted("foo()"));
            val unforeseen = new SyntaxAnalysis(analysis.source(), analysis.tokenStream(), analysis.parseTree(), true,
                    List.of());

            val document = DocumentCompiler.parseDocument(unforeseen);

            assertThat(document.diagnostics()).singleElement().satisfies(diagnostic -> {
                assertThat(diagnostic.problem()).isEqualTo(Problem.SYNTAX_ERROR);
                assertThat(diagnostic.message()).contains(UNFORESEEN_FAILURE);
                assertThat(textOf(unforeseen.source(), diagnostic.region())).isEqualTo("foo");
            });
        }

        @Test
        @DisplayName("parseDocument reports any exception of the transformation instead of throwing it, and logs it once")
        void whenTransformationThrowsAnyExceptionThenTheDocumentIsRefusedAndTheExceptionIsLoggedOnce() {
            // An analysis that missed the problems of its parse tree stands in for a gap
            // of the checks: the transformation fails on the incomplete import.
            val source = "import";
            val tokens = new CommonTokenStream(new SAPLLexer(CharStreams.fromString(source)));
            val parser = new SAPLParser(tokens);
            parser.removeErrorListeners();
            val unchecked = new SyntaxAnalysis(source, tokens, parser.sapl(), true, List.of());
            getTestLogger(SyntaxAnalysis.class).clear();

            val document = DocumentCompiler.parseDocument(unchecked);

            assertThat(document).returns(null, Document::saplDocument).extracting(Document::diagnostics)
                    .asInstanceOf(list(Diagnostic.class)).singleElement()
                    .returns(Problem.SYNTAX_ERROR, Diagnostic::problem);
            assertThat(getTestLogger(SyntaxAnalysis.class).getLoggingEvents()).singleElement()
                    .returns(Level.ERROR, LoggingEvent::getLevel).satisfies(
                            event -> assertThat(event.getThrowable()).containsInstanceOf(NullPointerException.class));
        }

        @Test
        @DisplayName("parseDocument rejects a document whose transformation exhausts the stack")
        void whenTransformationExhaustsStackThenDocumentIsInvalid() {
            try (val ignored = mockConstruction(AstTransformer.class,
                    (transformer, context) -> when(transformer.visitSapl(any())).thenThrow(StackOverflowError.class))) {
                val document = DocumentCompiler.parseDocument(POLICY_WITH_CONDITION.formatted("true"));

                assertThat(document.diagnostics()).extracting(Diagnostic::problem)
                        .containsExactly(Problem.NESTING_TOO_DEEP_WITHOUT_BRACKETS);
            }
        }
    }
}
