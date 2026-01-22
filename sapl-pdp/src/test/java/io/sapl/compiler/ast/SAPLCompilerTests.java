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
package io.sapl.compiler.ast;

import io.sapl.compiler.expressions.SaplCompilerException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SAPLCompilerTests {

    @Test
    void whenValidPolicy_thenParsesSuccessfully() {
        var policyDocument = "policy \"test\" permit";
        assertDoesNotThrow(() -> SAPLCompiler.parse(policyDocument));
    }

    @Test
    void whenBrokenInputStream_thenThrowsException() throws IOException {
        var brokenInputStream = mock(InputStream.class);
        when(brokenInputStream.read()).thenThrow(new IOException("Simulated IO errors"));
        assertThatThrownBy(() -> SAPLCompiler.parse(brokenInputStream)).isInstanceOf(SaplCompilerException.class);
    }

    @Test
    void whenInvalidSyntax_thenThrowsException() {
        var policyDocument = "xyz";
        assertThatThrownBy(() -> SAPLCompiler.parse(policyDocument)).isInstanceOf(SaplCompilerException.class);
    }

    @Test
    void whenValidPolicy_thenDocumentContainsCorrectMetadata() {
        var policyDefinition = "policy \"test\" permit";
        var document         = SAPLCompiler.parseDocument(policyDefinition);

        assertThat(document.isInvalid()).isFalse();
        assertThat(document.name()).isEqualTo("test");
        assertThat(document.errorMessage()).isEqualTo("OK");
    }

    @Test
    void whenInvalidSyntax_thenDocumentIsInvalid() {
        assertThat(SAPLCompiler.parseDocument("xyz").isInvalid()).isTrue();
    }

    @Test
    void whenParsingPolicySet_thenDocumentContainsCorrectMetadata() {
        var policySetDefinition = """
                set "test-set"
                priority deny or abstain errors propagate
                policy "inner-policy" permit
                """;
        var document            = SAPLCompiler.parseDocument(policySetDefinition);

        assertThat(document.isInvalid()).isFalse();
        assertThat(document.name()).isEqualTo("test-set");
        assertThat(document.type()).isEqualTo(DocumentType.POLICY_SET);
    }

    @Test
    void whenParsingSinglePolicy_thenDocumentTypeIsPolicy() {
        var policyDefinition = "policy \"single-policy\" deny";
        var document         = SAPLCompiler.parseDocument(policyDefinition);

        assertThat(document.isInvalid()).isFalse();
        assertThat(document.type()).isEqualTo(DocumentType.POLICY);
    }

    @ParameterizedTest
    @ValueSource(strings = { "policy \"test\" permit ,{ \"key\" : \"value\" } =~ 6432",
            "policy \"p\" permit var subject = {};", "policy \"p\" permit var action = {};",
            "policy \"p\" permit var resource = {};", "policy \"p\" permit var environment = {};" })
    void whenInvalidPolicyDefinitions_thenParsingThrowsException(String policyDefinition) {
        assertThatThrownBy(() -> SAPLCompiler.parse(policyDefinition)).isInstanceOf(SaplCompilerException.class);
    }

    @Test
    void whenParsingWithId_thenDocumentContainsId() {
        var policyDefinition = "policy \"test\" permit";
        var document         = SAPLCompiler.parseDocument("custom-id", policyDefinition);

        assertThat(document.isInvalid()).isFalse();
        assertThat(document.name()).isEqualTo("test");
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

    @ParameterizedTest(name = "{0}")
    @MethodSource("validPolicyDefinitions")
    void whenParsingValidPolicyVariant_thenParsesSuccessfully(String description, String policyDefinition) {
        assertDoesNotThrow(() -> SAPLCompiler.parse(policyDefinition));
    }

}
