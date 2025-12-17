/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.parser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultSAPLParserTests {

    private static final SAPLParser INTERPRETER = new DefaultSAPLParser();

    @Test
    void whenValidPolicy_thenParsesSuccessfully() {
        var policyDocument = "policy \"test\" permit";
        assertDoesNotThrow(() -> INTERPRETER.parse(policyDocument));
    }

    @Test
    void whenLazyBooleanOperatorsInTarget_thenValidationFails() {
        var policyDocument = "policy \"test\" permit true && false";
        assertThatThrownBy(() -> INTERPRETER.parse(policyDocument)).isInstanceOf(SaplParserException.class);
    }

    @Test
    void whenBrokenInputStream_thenThrowsException() throws IOException {
        var brokenInputStream = mock(InputStream.class);
        when(brokenInputStream.read()).thenThrow(new IOException("Simulated IO error"));
        assertThatThrownBy(() -> INTERPRETER.parse(brokenInputStream)).isInstanceOf(SaplParserException.class);
    }

    @Test
    void whenInvalidSyntax_thenThrowsException() {
        var policyDocument = "xyz";
        assertThatThrownBy(() -> INTERPRETER.parse(policyDocument)).isInstanceOf(SaplParserException.class);
    }

    @Test
    void whenValidPolicy_thenDocumentContainsCorrectMetadata() {
        var policyDefinition = "policy \"test\" permit";
        var document         = INTERPRETER.parseDocument(policyDefinition);

        assertThat(document.isInvalid()).isFalse();
        assertThat(document.name()).isEqualTo("test");
        assertThat(document.errorMessage()).isEqualTo("OK");
    }

    @Test
    void whenInvalidSyntax_thenDocumentIsInvalid() {
        assertThat(INTERPRETER.parseDocument("xyz").isInvalid()).isTrue();
    }

    @Test
    void whenParsingPolicySet_thenDocumentContainsCorrectMetadata() {
        var policySetDefinition = """
                set "test-set"
                deny-overrides
                policy "inner-policy" permit
                """;
        var document            = INTERPRETER.parseDocument(policySetDefinition);

        assertThat(document.isInvalid()).isFalse();
        assertThat(document.name()).isEqualTo("test-set");
        assertThat(document.type()).isEqualTo(DocumentType.POLICY_SET);
    }

    @Test
    void whenParsingSinglePolicy_thenDocumentTypeIsPolicy() {
        var policyDefinition = "policy \"single-policy\" deny";
        var document         = INTERPRETER.parseDocument(policyDefinition);

        assertThat(document.isInvalid()).isFalse();
        assertThat(document.type()).isEqualTo(DocumentType.POLICY);
    }

    @ParameterizedTest
    @ValueSource(strings = { "policy \"test\" permit ,{ \"key\" : \"value\" } =~ 6432",
            "policy \"p\" permit where var subject = {};", "policy \"p\" permit where var action = {};",
            "policy \"p\" permit where var resource = {};", "policy \"p\" permit where var environment = {};" })
    void whenInvalidPolicyDefinitions_thenParsingThrowsException(String policyDefinition) {
        assertThatThrownBy(() -> INTERPRETER.parse(policyDefinition)).isInstanceOf(SaplParserException.class);
    }

    @Test
    void whenParsingWithId_thenDocumentContainsId() {
        var policyDefinition = "policy \"test\" permit";
        var document         = INTERPRETER.parseDocument("custom-id", policyDefinition);

        assertThat(document.isInvalid()).isFalse();
        assertThat(document.name()).isEqualTo("test");
    }

    @Test
    void whenParsingPolicyWithWhereClause_thenParsesSuccessfully() {
        var policyDefinition = "policy \"test\" permit where true;";
        assertDoesNotThrow(() -> INTERPRETER.parse(policyDefinition));
    }

    @Test
    void whenParsingPolicyWithTransform_thenParsesSuccessfully() {
        var policyDefinition = "policy \"test\" permit transform null";
        assertDoesNotThrow(() -> INTERPRETER.parse(policyDefinition));
    }

    @Test
    void whenParsingPolicyWithObligation_thenParsesSuccessfully() {
        var policyDefinition = "policy \"test\" permit obligation null";
        assertDoesNotThrow(() -> INTERPRETER.parse(policyDefinition));
    }

    @Test
    void whenParsingPolicyWithAdvice_thenParsesSuccessfully() {
        var policyDefinition = "policy \"test\" permit advice null";
        assertDoesNotThrow(() -> INTERPRETER.parse(policyDefinition));
    }

    @Test
    void whenParsingPolicyWithImport_thenParsesSuccessfully() {
        var policyDefinition = "import simple.append policy \"test\" permit";
        assertDoesNotThrow(() -> INTERPRETER.parse(policyDefinition));
    }

    @Test
    void whenParsingPolicyWithImportAlias_thenParsesSuccessfully() {
        var policyDefinition = "import simple.append as concat policy \"test\" permit";
        assertDoesNotThrow(() -> INTERPRETER.parse(policyDefinition));
    }

    @Test
    void whenParsingPolicyWithMultipleImports_thenParsesSuccessfully() {
        var policyDefinition = "import simple.length import simple.append policy \"test\" permit";
        assertDoesNotThrow(() -> INTERPRETER.parse(policyDefinition));
    }

    @Test
    void whenParsingComplexExpression_thenParsesSuccessfully() {
        var policyDefinition = """
                policy "complex"
                permit resource.type == "document"
                where
                    var owner = resource.owner;
                    subject.id == owner;
                """;
        assertDoesNotThrow(() -> INTERPRETER.parse(policyDefinition));
    }

    @Test
    void whenParsingPolicyWithAttributeFinder_thenParsesSuccessfully() {
        var policyDefinition = "policy \"test\" permit where \"test\".<pip.attribute> == \"test\";";
        assertDoesNotThrow(() -> INTERPRETER.parse(policyDefinition));
    }

    @Test
    void whenParsingPolicyWithEnvironmentAttribute_thenParsesSuccessfully() {
        var policyDefinition = "policy \"test\" permit where <time.now> != undefined;";
        assertDoesNotThrow(() -> INTERPRETER.parse(policyDefinition));
    }

}
