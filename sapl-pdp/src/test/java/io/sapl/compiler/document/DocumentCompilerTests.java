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

import io.sapl.api.attributes.AttributeBroker;
import io.sapl.api.functions.FunctionBroker;
import io.sapl.compiler.expressions.CompilationContext;
import io.sapl.compiler.expressions.SaplCompilerException;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.mock;

@DisplayName("DocumentCompiler")
class DocumentCompilerTests {

    private CompilationContext ctx;

    @BeforeEach
    void setUp() {
        val functionBroker  = mock(FunctionBroker.class);
        val attributeBroker = mock(AttributeBroker.class);
        ctx = new CompilationContext(functionBroker, attributeBroker);
    }

    @Nested
    @DisplayName("parseDocument")
    class ParseDocumentTests {

        @Test
        @DisplayName("returns valid document for correct policy")
        void whenValidPolicy_thenDocumentIsValid() {
            val document = DocumentCompiler.parseDocument("policy \"test\" permit");

            assertThat(document.isInvalid()).isFalse();
            assertThat(document.errorMessage()).isEqualTo("OK");
        }

        @Test
        @DisplayName("returns invalid document for syntax errors")
        void whenInvalidSyntax_thenDocumentIsInvalid() {
            val document = DocumentCompiler.parseDocument("xyz");

            assertThat(document.isInvalid()).isTrue();
        }

        @Test
        @DisplayName("identifies policy type correctly")
        void whenSinglePolicy_thenTypeIsPolicy() {
            val document = DocumentCompiler.parseDocument("policy \"single-policy\" deny");

            assertThat(document.isInvalid()).isFalse();
            assertThat(document.type()).isEqualTo(DocumentType.POLICY);
        }

        @Test
        @DisplayName("identifies policy set type correctly")
        void whenPolicySet_thenTypeIsPolicySet() {
            val policySetDefinition = """
                    set "test-set"
                    priority deny or abstain errors propagate
                    policy "inner-policy" permit
                    """;
            val document            = DocumentCompiler.parseDocument(policySetDefinition);

            assertThat(document.isInvalid()).isFalse();
            assertThat(document.type()).isEqualTo(DocumentType.POLICY_SET);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidPolicyDefinitions")
        @DisplayName("detects validation errors")
        void whenValidationError_thenDocumentIsInvalid(String description, String policyDefinition) {
            val document = DocumentCompiler.parseDocument(policyDefinition);

            assertThat(document.isInvalid()).isTrue();
        }

        static Stream<Arguments> invalidPolicyDefinitions() {
            return Stream.of(
                    arguments("regex type mismatch", "policy \"test\" permit ,{ \"key\" : \"value\" } =~ 6432"),
                    arguments("reserved var 'subject'", "policy \"p\" permit var subject = {};"),
                    arguments("reserved var 'action'", "policy \"p\" permit var action = {};"),
                    arguments("reserved var 'resource'", "policy \"p\" permit var resource = {};"),
                    arguments("reserved var 'environment'", "policy \"p\" permit var environment = {};"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("validPolicyDefinitions")
        @DisplayName("parses valid policy variants")
        void whenValidPolicyVariant_thenDocumentIsValid(String description, String policyDefinition) {
            val document = DocumentCompiler.parseDocument(policyDefinition);

            assertThat(document.isInvalid()).isFalse();
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
    @DisplayName("compileDocument")
    class CompileDocumentTests {

        @Test
        @DisplayName("compiles valid policy successfully")
        void whenValidPolicy_thenCompiles() {
            assertThatCode(() -> DocumentCompiler.compileDocument("policy \"test\" permit", ctx))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("returns compiled document with metadata")
        void whenValidPolicy_thenReturnsCompiledDocument() {
            val compiled = DocumentCompiler.compileDocument("policy \"test\" permit", ctx);

            assertThat(compiled).isNotNull();
            assertThat(compiled.metadata()).isNotNull();
        }

        @Test
        @DisplayName("throws on syntax errors")
        void whenInvalidSyntax_thenThrows() {
            assertThatThrownBy(() -> DocumentCompiler.compileDocument("xyz", ctx))
                    .isInstanceOf(SaplCompilerException.class);
        }

        @ParameterizedTest
        @ValueSource(strings = { "policy \"test\" permit ,{ \"key\" : \"value\" } =~ 6432",
                "policy \"p\" permit var subject = {};", "policy \"p\" permit var action = {};",
                "policy \"p\" permit var resource = {};", "policy \"p\" permit var environment = {};" })
        @DisplayName("throws on validation errors")
        void whenValidationError_thenThrows(String policyDefinition) {
            assertThatThrownBy(() -> DocumentCompiler.compileDocument(policyDefinition, ctx))
                    .isInstanceOf(SaplCompilerException.class);
        }

        @Test
        @DisplayName("compiles policy set successfully")
        void whenValidPolicySet_thenCompiles() {
            val policySetDefinition = """
                    set "test-set"
                    priority deny or abstain errors propagate
                    policy "inner-policy" permit
                    """;

            assertThatCode(() -> DocumentCompiler.compileDocument(policySetDefinition, ctx)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("trojan source protection")
    class TrojanSourceProtectionTests {

        private static final char LRI = '\u2066';
        private static final char RLI = '\u2067';
        private static final char PDI = '\u2069';
        private static final char RLO = '\u202E';

        @ParameterizedTest(name = "rejects input containing {0}")
        @ValueSource(chars = { LRI, RLI, PDI, RLO })
        @DisplayName("parseDocument rejects trojan source characters")
        void whenTrojanCharacterInParseDocument_thenThrows(char trojanChar) {
            val malicious = "policy \"te" + trojanChar + "st\" permit";

            assertThatThrownBy(() -> DocumentCompiler.parseDocument(malicious))
                    .isInstanceOf(SaplCompilerException.class).hasMessageContaining("trojan source");
        }

        @ParameterizedTest(name = "rejects input containing {0}")
        @ValueSource(chars = { LRI, RLI, PDI, RLO })
        @DisplayName("compileDocument rejects trojan source characters")
        void whenTrojanCharacterInCompileDocument_thenThrows(char trojanChar) {
            val malicious = "policy \"te" + trojanChar + "st\" permit";

            assertThatThrownBy(() -> DocumentCompiler.compileDocument(malicious, ctx))
                    .isInstanceOf(SaplCompilerException.class).hasMessageContaining("trojan source");
        }
    }
}
