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
package io.sapl.lsp.sapl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("SAPLSemanticTokensProvider")
class SAPLSemanticTokensProviderTests {

    private final SAPLSemanticTokensProvider provider = new SAPLSemanticTokensProvider();

    static Stream<Arguments> macroTokenCases() {
        return Stream.of(arguments("permit effect", "policy \"test\" permit", "permit"),
                arguments("deny effect", "policy \"test\" deny", "deny"),
                arguments("suspend effect", "policy \"test\" suspend", "suspend"),
                arguments("priority in algorithm", "set \"test\" priority deny", "priority"),
                arguments("unanimous algorithm", "set \"test\" unanimous", "unanimous"),
                arguments("unanimous strict variant", "set \"test\" unanimous strict", "strict"),
                arguments("unique algorithm", "set \"test\" unique", "unique"),
                arguments("first algorithm", "set \"test\" first", "first"),
                arguments("or in algorithm clause", "set \"test\" first or deny", "or"),
                arguments("errors clause", "set \"test\" first or deny errors propagate", "errors"),
                arguments("abstain default", "set \"test\" first or abstain", "abstain"),
                arguments("propagate handler", "set \"test\" first or deny errors propagate", "propagate"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("macroTokenCases")
    void whenTokenIsEffectOrAlgorithm_thenClassifiedAsMacro(String description, String content, String text) {
        var tokens = decodedTokens(content);

        assertThat(tokens).anySatisfy(t -> {
            assertThat(t.text()).isEqualTo(text);
            assertThat(t.tokenType()).isEqualTo(SAPLSemanticTokenTypes.MACRO);
        });
    }

    private List<DecodedToken> decodedTokens(String content) {
        var document    = new SAPLParsedDocument("test://test.sapl", content);
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
