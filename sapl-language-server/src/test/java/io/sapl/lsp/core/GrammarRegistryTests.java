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
package io.sapl.lsp.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.lsp.sapl.SAPLGrammarSupport;
import io.sapl.lsp.sapltest.SAPLTestGrammarSupport;

class GrammarRegistryTests {

    private GrammarRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new GrammarRegistry();
        var saplGrammar = new SAPLGrammarSupport();
        registry.register(saplGrammar);
        registry.setDefaultGrammar(saplGrammar.getGrammarId());
        registry.register(new SAPLTestGrammarSupport());
    }

    static Stream<Arguments> grammarForUriCases() {
        return Stream.of(arguments("SAPL file extension", "file:///test.sapl", "sapl"),
                arguments("SAPLTEST file extension", "file:///test.sapltest", "sapltest"),
                arguments("unknown extension returns default", "file:///test.txt", "sapl"),
                arguments("uppercase extension matches case-insensitive", "file:///test.SAPLTEST", "sapltest"),
                arguments("null URI returns default", null, "sapl"),
                arguments("URI without extension returns default", "file:///noextension", "sapl"),
                arguments("Windows-style SAPL URI", "file:///C:/project/policies/test.sapl", "sapl"),
                arguments("Windows-style SAPLTEST URI", "file:///C:/project/tests/test.sapltest", "sapltest"),
                arguments("Unix-style SAPL URI", "file:///home/user/project/policy.sapl", "sapl"),
                arguments("Unix-style SAPLTEST URI", "file:///home/user/project/test.sapltest", "sapltest"),
                arguments("deep nested path",
                        "file:///project/src/test/resources/integration/policySetLoading.sapltest", "sapltest"),
                arguments("URI with query string - SAPL", "file:///policy.sapl?configurationId=default", "sapl"),
                arguments("URI with query string - SAPLTEST", "file:///test.sapltest?configurationId=production",
                        "sapltest"),
                arguments("URI with fragment - SAPL", "file:///policy.sapl#line=10", "sapl"),
                arguments("URI with fragment - SAPLTEST", "file:///test.sapltest#section", "sapltest"),
                arguments("URI with query and fragment", "file:///policy.sapl?security=test#line=5", "sapl"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("grammarForUriCases")
    void whenGetGrammarForUri_thenReturnsExpectedGrammar(String description, String uri, String expectedGrammarId) {
        var grammar = registry.getGrammarForUri(uri);
        assertThat(grammar.getGrammarId()).isEqualTo(expectedGrammarId);
    }

    @Test
    void whenGetAllFileExtensions_thenReturnsBothExtensions() {
        var extensions = registry.getAllFileExtensions();
        assertThat(extensions).containsExactlyInAnyOrder(".sapl", ".sapltest");
    }

    @Test
    void whenGetAllCompletionTriggerCharacters_thenCombinesAllGrammars() {
        var triggerChars = registry.getAllCompletionTriggerCharacters();
        assertThat(triggerChars).isNotEmpty();
    }

    @Test
    void whenGetCombinedSemanticTokensLegend_thenReturnsLegend() {
        var legend = registry.getCombinedSemanticTokensLegend();
        assertThat(legend).isNotNull();
        assertThat(legend.getTokenTypes()).isNotEmpty();
    }

    static Stream<Arguments> grammarByIdCases() {
        return Stream.of(arguments("SAPL grammar by ID", "sapl", true),
                arguments("SAPLTEST grammar by ID", "sapltest", true),
                arguments("unknown grammar ID returns empty", "unknown", false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("grammarByIdCases")
    void whenGetGrammarById_thenReturnsExpectedResult(String description, String grammarId, boolean shouldBePresent) {
        var grammar = registry.getGrammarById(grammarId);
        if (shouldBePresent) {
            assertThat(grammar).isPresent();
            assertThat(grammar.get().getGrammarId()).isEqualTo(grammarId);
        } else {
            assertThat(grammar).isEmpty();
        }
    }

    @Test
    void whenGetAllGrammars_thenReturnsBothGrammars() {
        var grammars = registry.getAllGrammars();
        assertThat(grammars).hasSize(2);
    }

    @Test
    void whenGetGrammarForTestResourceFile_thenReturnsCorrectGrammar() {
        // Test using actual test resource files
        var saplUri     = getClass().getClassLoader().getResource("testfiles/sample.sapl");
        var sapltestUri = getClass().getClassLoader().getResource("testfiles/sample.sapltest");

        assertThat(saplUri).isNotNull();
        assertThat(sapltestUri).isNotNull();

        var saplGrammar = registry.getGrammarForUri(saplUri.toString());
        assertThat(saplGrammar.getGrammarId()).isEqualTo("sapl");

        var saplTestGrammar = registry.getGrammarForUri(sapltestUri.toString());
        assertThat(saplTestGrammar.getGrammarId()).isEqualTo("sapltest");
    }

}
