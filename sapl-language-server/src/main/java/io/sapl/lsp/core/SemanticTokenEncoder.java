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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToIntFunction;

import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.SemanticTokens;

import lombok.experimental.UtilityClass;
import lombok.val;

/**
 * Encodes the tokens of a document as semantic tokens of the language server
 * protocol, for every language the server supports. The language decides only
 * the type of each token.
 */
@UtilityClass
public class SemanticTokenEncoder {

    /**
     * Encodes the tokens of a document. The protocol forbids a token to cross a
     * line, so a token over several lines, a block comment or a text with line
     * breaks, becomes one semantic token per line. Its continuation lines start
     * at character 0.
     *
     * @param document the parsed document
     * @param typeOfToken the index of the semantic token type of a token, or a
     * negative number for a token that is not highlighted
     * @return the semantic tokens, each as five integers: delta line, delta start
     * character, length, token type, and token modifiers
     */
    public static SemanticTokens semanticTokensOf(ParsedDocument document, ToIntFunction<Token> typeOfToken) {
        val positions     = ProtocolPositions.of(document.getContent());
        val semanticInfos = new ArrayList<SemanticTokenInfo>();

        for (val token : document.getTokens()) {
            val tokenType = typeOfToken.applyAsInt(token);
            if (tokenType >= 0) {
                for (val range : positions.lineRangesOf(token)) {
                    val start = range.getStart();
                    semanticInfos.add(new SemanticTokenInfo(start.getLine(), start.getCharacter(),
                            range.getEnd().getCharacter() - start.getCharacter(), tokenType, 0));
                }
            }
        }

        semanticInfos
                .sort(Comparator.comparingInt(SemanticTokenInfo::line).thenComparingInt(SemanticTokenInfo::column));
        return new SemanticTokens(encode(semanticInfos));
    }

    private static List<Integer> encode(List<SemanticTokenInfo> tokens) {
        val data     = new ArrayList<Integer>();
        var prevLine = 0;
        var prevChar = 0;

        for (val token : tokens) {
            val deltaLine = token.line() - prevLine;
            val deltaChar = deltaLine == 0 ? token.column() - prevChar : token.column();

            data.add(deltaLine);
            data.add(deltaChar);
            data.add(token.length());
            data.add(token.tokenType());
            data.add(token.tokenModifiers());

            prevLine = token.line();
            prevChar = token.column();
        }

        return data;
    }

    /**
     * Represents semantic token information before encoding.
     */
    private record SemanticTokenInfo(int line, int column, int length, int tokenType, int tokenModifiers) {}

}
