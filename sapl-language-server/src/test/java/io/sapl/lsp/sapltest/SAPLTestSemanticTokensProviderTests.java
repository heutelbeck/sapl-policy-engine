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
package io.sapl.lsp.sapltest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("SAPLTestSemanticTokensProvider")
class SAPLTestSemanticTokensProviderTests {

    private final SAPLTestSemanticTokensProvider provider = new SAPLTestSemanticTokensProvider();

    static Stream<Arguments> macroTokenCases() {
        return Stream.of(arguments("permit decision",
                "requirement \"r\" { scenario \"s\" { when subject \"a\" attempts action \"x\" on resource \"y\" expect permit } }",
                "permit"),
                arguments("deny decision",
                        "requirement \"r\" { scenario \"s\" { when subject \"a\" attempts action \"x\" on resource \"y\" expect deny } }",
                        "deny"),
                arguments("suspend decision",
                        "requirement \"r\" { scenario \"s\" { when subject \"a\" attempts action \"x\" on resource \"y\" expect suspend } }",
                        "suspend"),
                arguments("priority in algorithm",
                        "pdp-configuration \"p\" { combining-algorithm priority deny or deny }", "priority"),
                arguments("unanimous algorithm", "pdp-configuration \"p\" { combining-algorithm unanimous or deny }",
                        "unanimous"),
                arguments("strict in unanimous strict",
                        "pdp-configuration \"p\" { combining-algorithm unanimous strict or deny }", "strict"),
                arguments("unique algorithm", "pdp-configuration \"p\" { combining-algorithm unique or deny }",
                        "unique"),
                arguments("first algorithm", "pdp-configuration \"p\" { combining-algorithm first or deny }", "first"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("macroTokenCases")
    void whenTokenIsDecisionOrAlgorithm_thenClassifiedAsMacro(String description, String content, String text) {
        var tokens = decodedTokens(content);

        assertThat(tokens).anySatisfy(t -> {
            assertThat(t.text()).isEqualTo(text);
            assertThat(t.tokenType()).isEqualTo(SAPLTestSemanticTokenTypes.MACRO);
        });
    }

    private List<DecodedToken> decodedTokens(String content) {
        var document    = new SAPLTestParsedDocument("test://test.sapltest", content);
        var data        = provider.provideSemanticTokens(document).getData();
        var sourceLines = content.split("\n", -1);
        var decoded     = new ArrayList<DecodedToken>();

        var line = 0;
        var col  = 0;
        for (var i = 0; i < data.size(); i += 5) {
            var deltaLine = data.get(i);
            var deltaChar = data.get(i + 1);
            var length    = data.get(i + 2);
            var tokenType = data.get(i + 3);

            line += deltaLine;
            if (deltaLine == 0) {
                col += deltaChar;
            } else {
                col = deltaChar;
            }

            var text = sourceLines[line].substring(col, col + length);
            decoded.add(new DecodedToken(line, col, text, tokenType));
        }
        return decoded;
    }

    private record DecodedToken(int line, int column, String text, int tokenType) {}

}
