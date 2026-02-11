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

import java.util.List;
import java.util.Set;

/**
 * Defines the semantic token types and modifiers for SAPLTest syntax
 * highlighting.
 * Maps to standard LSP semantic token types.
 */
final class SAPLTestSemanticTokenTypes {

    private SAPLTestSemanticTokenTypes() {
    }

    // Token type indices (must match TOKEN_TYPES order)
    public static final int KEYWORD   = 0;
    public static final int OPERATOR  = 1;
    public static final int STRING    = 2;
    public static final int NUMBER    = 3;
    public static final int COMMENT   = 4;
    public static final int NAMESPACE = 5;
    public static final int CLASS     = 6;
    public static final int VARIABLE  = 7;
    public static final int FUNCTION  = 8;
    public static final int PROPERTY  = 9;
    public static final int PARAMETER = 10;
    public static final int MACRO     = 11;

    /**
     * List of token types in the order they are encoded.
     * This is sent to the client as the semantic tokens legend.
     */
    public static final List<String> TOKEN_TYPES = List.of("keyword", "operator", "string", "number", "comment",
            "namespace", "class", "variable", "function", "property", "parameter", "macro");

    /**
     * List of token modifiers.
     */
    public static final List<String> TOKEN_MODIFIERS = List.of();

    /**
     * Structure keywords for test organization.
     */
    public static final Set<String> STRUCTURE_KEYWORDS = Set.of("requirement", "scenario", "given", "when", "then",
            "expect");

    /**
     * Authorization keywords.
     */
    public static final Set<String> AUTH_KEYWORDS = Set.of("subject", "action", "resource", "environment", "attempts",
            "on", "in");

    /**
     * Decision keywords - get special macro styling.
     */
    public static final Set<String> DECISION_KEYWORDS = Set.of("permit", "deny", "indeterminate", "not-applicable",
            "decision");

    /**
     * Mock and setup keywords.
     */
    public static final Set<String> MOCK_KEYWORDS = Set.of("function", "attribute", "maps", "to", "emits", "stream",
            "timing", "of", "is", "called", "virtual-time", "error", "once", "times");

    /**
     * Import keywords.
     */
    public static final Set<String> IMPORT_KEYWORDS = Set.of("pip", "static-pip", "function-library",
            "static-function-library");

    /**
     * PDP configuration keywords.
     */
    public static final Set<String> PDP_KEYWORDS = Set.of("pdp", "variables", "secrets", "combining-algorithm",
            "configuration", "pdp-configuration", "policy", "set", "policies");

    /**
     * Combining algorithm keywords.
     */
    public static final Set<String> ALGORITHM_KEYWORDS = Set.of("deny-overrides", "permit-overrides",
            "only-one-applicable", "deny-unless-permit", "permit-unless-deny");

    /**
     * Matcher keywords.
     */
    public static final Set<String> MATCHER_KEYWORDS = Set.of("null", "text", "number", "boolean", "array", "object",
            "where", "matching", "any", "equals", "containing", "key", "value", "with");

    /**
     * String matcher keywords.
     */
    public static final Set<String> STRING_MATCHER_KEYWORDS = Set.of("blank", "empty", "null-or-empty", "null-or-blank",
            "equal", "compressed", "whitespace", "case-insensitive", "regex", "starting", "ending", "length", "order");

    /**
     * Expectation step keywords.
     */
    public static final Set<String> EXPECT_KEYWORDS = Set.of("no-event", "for", "wait", "next", "obligation", "advice",
            "obligations");

    /**
     * Boolean and special value literals.
     */
    public static final Set<String> LITERALS = Set.of("true", "false", "undefined");

    /**
     * Punctuation operator.
     */
    public static final Set<String> OPERATORS = Set.of("{", "}", "[", "]", "(", ")", ",", ":", ";", "-", "<", ">",
            "and");

    /**
     * Checks if a token text is any keyword.
     *
     * @param text the token text
     * @return true if it's a keyword
     */
    public static boolean isKeyword(String text) {
        return STRUCTURE_KEYWORDS.contains(text) || AUTH_KEYWORDS.contains(text) || MOCK_KEYWORDS.contains(text)
                || IMPORT_KEYWORDS.contains(text) || PDP_KEYWORDS.contains(text) || MATCHER_KEYWORDS.contains(text)
                || STRING_MATCHER_KEYWORDS.contains(text) || EXPECT_KEYWORDS.contains(text);
    }

}
