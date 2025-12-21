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
package io.sapl.lsp.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

    @Test
    void whenGetGrammarForSaplUri_thenReturnsSaplGrammar() {
        var grammar = registry.getGrammarForUri("file:///test.sapl");
        assertThat(grammar.getGrammarId()).isEqualTo("sapl");
    }

    @Test
    void whenGetGrammarForSapltestUri_thenReturnsSaplTestGrammar() {
        var grammar = registry.getGrammarForUri("file:///test.sapltest");
        assertThat(grammar.getGrammarId()).isEqualTo("sapltest");
    }

    @Test
    void whenGetGrammarForUnknownExtension_thenReturnsDefaultGrammar() {
        var grammar = registry.getGrammarForUri("file:///test.txt");
        assertThat(grammar.getGrammarId()).isEqualTo("sapl");
    }

    @Test
    void whenGetGrammarForUriWithUppercaseExtension_thenMatchesCaseInsensitive() {
        var grammar = registry.getGrammarForUri("file:///test.SAPLTEST");
        assertThat(grammar.getGrammarId()).isEqualTo("sapltest");
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

    @Test
    void whenGetGrammarById_thenReturnsGrammar() {
        var saplGrammar = registry.getGrammarById("sapl");
        assertThat(saplGrammar).isPresent();
        assertThat(saplGrammar.get().getGrammarId()).isEqualTo("sapl");

        var saplTestGrammar = registry.getGrammarById("sapltest");
        assertThat(saplTestGrammar).isPresent();
        assertThat(saplTestGrammar.get().getGrammarId()).isEqualTo("sapltest");
    }

    @Test
    void whenGetGrammarByUnknownId_thenReturnsEmpty() {
        var grammar = registry.getGrammarById("unknown");
        assertThat(grammar).isEmpty();
    }

    @Test
    void whenGetAllGrammars_thenReturnsBothGrammars() {
        var grammars = registry.getAllGrammars();
        assertThat(grammars).hasSize(2);
    }

    @Test
    void whenGetGrammarForNullUri_thenReturnsDefaultGrammar() {
        var grammar = registry.getGrammarForUri(null);
        assertThat(grammar.getGrammarId()).isEqualTo("sapl");
    }

    @Test
    void whenGetGrammarForUriWithoutExtension_thenReturnsDefaultGrammar() {
        var grammar = registry.getGrammarForUri("file:///noextension");
        assertThat(grammar.getGrammarId()).isEqualTo("sapl");
    }

    @Test
    void whenGetGrammarForWindowsStyleUri_thenReturnsCorrectGrammar() {
        // Windows-style file URI pattern
        var saplGrammar = registry.getGrammarForUri("file:///C:/project/policies/test.sapl");
        assertThat(saplGrammar.getGrammarId()).isEqualTo("sapl");

        var saplTestGrammar = registry.getGrammarForUri("file:///C:/project/tests/test.sapltest");
        assertThat(saplTestGrammar.getGrammarId()).isEqualTo("sapltest");
    }

    @Test
    void whenGetGrammarForUnixStyleUri_thenReturnsCorrectGrammar() {
        // Unix-style file URI pattern
        var saplGrammar = registry.getGrammarForUri("file:///home/user/project/policy.sapl");
        assertThat(saplGrammar.getGrammarId()).isEqualTo("sapl");

        var saplTestGrammar = registry.getGrammarForUri("file:///home/user/project/test.sapltest");
        assertThat(saplTestGrammar.getGrammarId()).isEqualTo("sapltest");
    }

    @Test
    void whenGetGrammarForDeepNestedUri_thenReturnsSaplTestGrammar() {
        // Deep nested path pattern (typical for test resources)
        var grammar = registry
                .getGrammarForUri("file:///project/src/test/resources/integration/policySetLoading.sapltest");
        assertThat(grammar.getGrammarId()).isEqualTo("sapltest");
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

    @Test
    void whenGetGrammarForUriWithQueryString_thenIgnoresQueryAndReturnsCorrectGrammar() {
        var saplGrammar = registry.getGrammarForUri("file:///policy.sapl?configurationId=default");
        assertThat(saplGrammar.getGrammarId()).isEqualTo("sapl");

        var saplTestGrammar = registry.getGrammarForUri("file:///test.sapltest?configurationId=production");
        assertThat(saplTestGrammar.getGrammarId()).isEqualTo("sapltest");
    }

    @Test
    void whenGetGrammarForUriWithFragment_thenIgnoresFragmentAndReturnsCorrectGrammar() {
        var saplGrammar = registry.getGrammarForUri("file:///policy.sapl#line=10");
        assertThat(saplGrammar.getGrammarId()).isEqualTo("sapl");

        var saplTestGrammar = registry.getGrammarForUri("file:///test.sapltest#section");
        assertThat(saplTestGrammar.getGrammarId()).isEqualTo("sapltest");
    }

    @Test
    void whenGetGrammarForUriWithQueryAndFragment_thenIgnoresBothAndReturnsCorrectGrammar() {
        var grammar = registry.getGrammarForUri("file:///policy.sapl?security=test#line=5");
        assertThat(grammar.getGrammarId()).isEqualTo("sapl");
    }

}
