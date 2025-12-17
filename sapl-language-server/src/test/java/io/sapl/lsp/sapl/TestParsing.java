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
package io.sapl.lsp.sapl;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;

import io.sapl.grammar.antlr.SAPLLexer;
import io.sapl.grammar.antlr.SAPLParser;
import io.sapl.grammar.antlr.SAPLParser.SaplContext;
import lombok.experimental.UtilityClass;

/**
 * Test utility for parsing SAPL documents without console error output. Removes
 * the default error listeners from lexer and parser to prevent ANTLR error
 * messages from polluting test output.
 */
@UtilityClass
public class TestParsing {

    /**
     * Parses SAPL content silently without printing errors to stderr.
     *
     * @param content the SAPL document content
     * @return the parsed SAPL context
     */
    public static SaplContext parseSilently(String content) {
        var charStream  = CharStreams.fromString(content);
        var lexer       = new SAPLLexer(charStream);
        var tokenStream = new CommonTokenStream(lexer);
        var parser      = new SAPLParser(tokenStream);
        lexer.removeErrorListeners();
        parser.removeErrorListeners();
        return parser.sapl();
    }

    /**
     * Creates a lexer with error listeners removed for tokenization tests.
     *
     * @param content the SAPL document content
     * @return the lexer with silenced error output
     */
    public static Lexer createSilentLexer(String content) {
        var charStream = CharStreams.fromString(content);
        var lexer      = new SAPLLexer(charStream);
        lexer.removeErrorListeners();
        return lexer;
    }

}
