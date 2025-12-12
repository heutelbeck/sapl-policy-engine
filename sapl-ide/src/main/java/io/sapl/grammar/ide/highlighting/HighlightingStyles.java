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

/**
 * Defines semantic highlighting style identifiers for SAPL.
 * These map to standard LSP semantic token types where possible.
 */
public final class HighlightingStyles {

    private HighlightingStyles() {
        // Utility class
    }

    /** Style for policy set names - maps to LSP 'namespace' */
    public static final String POLICY_SET_NAME = "namespace";

    /** Style for policy names - maps to LSP 'class' */
    public static final String POLICY_NAME = "class";

    /** Style for variable definitions - maps to LSP 'variable' */
    public static final String VARIABLE = "variable";

    /** Style for function calls - maps to LSP 'function' */
    public static final String FUNCTION = "function";

    /** Style for attribute finder references - maps to LSP 'property' */
    public static final String ATTRIBUTE = "property";

    /** Style for import statements - maps to LSP 'namespace' */
    public static final String IMPORT = "namespace";

    /** Style for schema definitions - maps to LSP 'type' */
    public static final String SCHEMA = "type";

    /**
     * Style for authorization subscription parts (subject, action, resource,
     * environment)
     */
    public static final String AUTHORIZATION_SUBSCRIPTION = "parameter";

}
