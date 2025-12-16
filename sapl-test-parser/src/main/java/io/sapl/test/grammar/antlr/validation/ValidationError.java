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
package io.sapl.test.grammar.antlr.validation;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

/**
 * Represents a semantic validation error in a SAPLTest document.
 *
 * @param message the error message describing the validation failure
 * @param line the line number where the error occurred (1-based)
 * @param charPositionInLine the character position within the line (0-based)
 * @param offendingText the text that caused the validation error
 */
public record ValidationError(String message, int line, int charPositionInLine, String offendingText) {

    /**
     * Creates a ValidationError from a token.
     *
     * @param message the error message
     * @param token the offending token
     * @return a new ValidationError
     */
    public static ValidationError fromToken(String message, Token token) {
        return new ValidationError(message, token.getLine(), token.getCharPositionInLine(), token.getText());
    }

    /**
     * Creates a ValidationError from a parser rule context.
     *
     * @param message the error message
     * @param context the offending context (may be null)
     * @return a new ValidationError
     */
    public static ValidationError fromContext(String message, ParserRuleContext context) {
        if (context == null) {
            return new ValidationError(message, 0, 0, "");
        }
        var start   = context.getStart();
        var stop    = context.getStop();
        var text    = start != null && stop != null ? context.getText() : "";
        var line    = start != null ? start.getLine() : 0;
        var charPos = start != null ? start.getCharPositionInLine() : 0;
        return new ValidationError(message, line, charPos, text);
    }

    @Override
    public String toString() {
        return "line %d:%d %s".formatted(line, charPositionInLine, message);
    }

}
