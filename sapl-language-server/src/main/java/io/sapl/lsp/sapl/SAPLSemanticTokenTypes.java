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

import java.util.List;
import java.util.Set;

/**
 * Defines the semantic token types and modifiers for SAPL syntax highlighting.
 * Maps to standard LSP semantic token types.
 */
public final class SAPLSemanticTokenTypes {

    private SAPLSemanticTokenTypes() {
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
     * List of token modifiers (empty for now, can add declaration, definition,
     * etc.).
     */
    public static final List<String> TOKEN_MODIFIERS = List.of();

    /**
     * SAPL keywords that should be highlighted.
     */
    public static final Set<String> KEYWORDS = Set.of("import", "as", "schema", "enforced", "set", "for", "policy",
            "where", "var", "obligation", "advice", "transform", "in", "each", "true", "false", "null", "undefined");

    /**
     * Entitlements and combining algorithms - get special macro styling.
     */
    public static final Set<String> ENTITLEMENTS_AND_ALGORITHMS = Set.of("permit", "deny", "deny-overrides",
            "permit-overrides", "first-applicable", "only-one-applicable", "deny-unless-permit", "permit-unless-deny");

    /**
     * Authorization subscription element names.
     */
    public static final Set<String> SUBSCRIPTION_ELEMENTS = Set.of("subject", "action", "resource", "environment");

    /**
     * SAPL operators.
     */
    public static final Set<String> OPERATORS = Set.of("||", "&&", "|", "^", "&", "==", "!=", "=~", "<", "<=", ">",
            ">=", "+", "-", "*", "/", "%", "!", "|-", "::", "@", ".", "..", ":", ",", "|<", "#");

}
