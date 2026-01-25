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
package io.sapl.test.lang;

import io.sapl.compiler.util.InputStreamUtil;
import io.sapl.compiler.util.TrojanSourceUtil;
import io.sapl.test.grammar.antlr.SAPLTestLexer;
import io.sapl.test.grammar.antlr.SAPLTestParser.SaplTestContext;
import lombok.experimental.UtilityClass;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@UtilityClass
public class SaplTestParser {

    /**
     * Parses a SAPL test definition from a string.
     *
     * @param testDefinition the test definition source code
     * @return the parsed ANTLR parse tree
     * @throws SaplTestException if parsing fails or invalid characters are
     * detected
     */
    public static SaplTestContext parse(String testDefinition) {
        return parse(new ByteArrayInputStream(testDefinition.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Parses a SAPL test definition from an input stream.
     * <p>
     * The stream is processed to detect encoding and protect against trojan source
     * attacks.
     *
     * @param inputStream the input stream containing the test definition
     * @return the parsed ANTLR parse tree
     * @throws SaplTestException if parsing fails or invalid characters are
     * detected
     */
    public static SaplTestContext parse(InputStream inputStream) {
        try {
            var securedStream = secureInputStream(inputStream);
            return parseStream(securedStream);
        } catch (IOException e) {
            throw new SaplTestException("Failed to read test definition.", e);
        }
    }

    private static InputStream secureInputStream(InputStream inputStream) throws IOException {
        var converted = InputStreamUtil.detectAndConvertEncodingOfStream(inputStream);
        return TrojanSourceUtil.guardInputStream(converted);
    }

    private static SaplTestContext parseStream(InputStream inputStream) throws IOException {
        var charStream  = CharStreams.fromStream(inputStream, StandardCharsets.UTF_8);
        var lexer       = new SAPLTestLexer(charStream);
        var tokenStream = new CommonTokenStream(lexer);
        var parser      = new io.sapl.test.grammar.antlr.SAPLTestParser(tokenStream);

        // Collect syntax errors
        var errorListener = new SaplTestErrorListener();
        parser.removeErrorListeners();
        parser.addErrorListener(errorListener);

        var parseTree = parser.saplTest();

        if (errorListener.hasErrors()) {
            throw new SaplTestException("Parsing errors: " + errorListener.getErrors());
        }

        return parseTree;
    }

}
