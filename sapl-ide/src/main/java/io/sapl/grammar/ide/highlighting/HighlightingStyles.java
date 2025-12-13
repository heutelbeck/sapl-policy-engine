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
package io.sapl.grammar.ide.highlighting;

import java.util.Set;

/**
 * Defines semantic highlighting style identifiers for SAPL. These map to
 * standard LSP semantic token types for consistent highlighting across editors.
 */
public final class HighlightingStyles {

    private HighlightingStyles() {
    }

    /** Standard LSP token type for keywords. */
    public static final String KEYWORD = "keyword";

    /** Standard LSP token type for operators. */
    public static final String OPERATOR = "operator";

    /** Standard LSP token type for string literals. */
    public static final String STRING = "string";

    /** Standard LSP token type for number literals. */
    public static final String NUMBER = "number";

    /** Standard LSP token type for comments. */
    public static final String COMMENT = "comment";

    /** Standard LSP token type for namespaces (policy set names, imports). */
    public static final String NAMESPACE = "namespace";

    /** Standard LSP token type for classes (policy names). */
    public static final String CLASS = "class";

    /** Standard LSP token type for variables. */
    public static final String VARIABLE = "variable";

    /** Standard LSP token type for functions. */
    public static final String FUNCTION = "function";

    /** Standard LSP token type for properties (attributes). */
    public static final String PROPERTY = "property";

    /** Standard LSP token type for types (schemas). */
    public static final String TYPE = "type";

    /**
     * Standard LSP token type for parameters (authorization subscription parts).
     */
    public static final String PARAMETER = "parameter";

    /** All SAPL keywords for lexical highlighting. */
    public static final Set<String> KEYWORDS = Set.of("import", "as", "schema", "enforced", "set", "for", "policy",
            "permit", "deny", "where", "var", "obligation", "advice", "transform", "in", "each", "true", "false",
            "null", "undefined", "deny-overrides", "permit-overrides", "first-applicable", "only-one-applicable",
            "deny-unless-permit", "permit-unless-deny");

    /** Entitlements and combining algorithms - get special macro styling. */
    public static final Set<String> ENTITLEMENTS_AND_ALGORITHMS = Set.of("permit", "deny", "deny-overrides",
            "permit-overrides", "first-applicable", "only-one-applicable", "deny-unless-permit", "permit-unless-deny");

    /** All SAPL operators for lexical highlighting. */
    public static final Set<String> OPERATORS = Set.of("||", "&&", "|", "^", "&", "==", "!=", "=~", "<", "<=", ">",
            ">=", "+", "-", "*", "/", "%", "!", "|-", "::", "@", ".", "..", ":", ",", "|<");

}
