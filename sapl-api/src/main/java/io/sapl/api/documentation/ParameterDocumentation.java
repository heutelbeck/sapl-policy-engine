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
package io.sapl.api.documentation;

import java.util.List;

/**
 * Documentation for a single parameter of a function or attribute finder.
 *
 * @param name
 * the parameter name as declared in the method signature
 * @param allowedTypes
 * list of allowed SAPL types for this parameter, derived from validation
 * annotations such as {@code @Text},
 * {@code @Number}, {@code @Bool}, {@code @Array}, {@code @Object}. An empty
 * list indicates any Value type is
 * accepted.
 * @param varArgs
 * true if this parameter accepts variable arguments
 */
public record ParameterDocumentation(String name, List<String> allowedTypes, boolean varArgs) {

    /**
     * Creates parameter documentation with no type constraints or schema.
     *
     * @param name
     * the parameter name
     *
     * @return parameter documentation accepting any value type
     */
    public static ParameterDocumentation untyped(String name) {
        return new ParameterDocumentation(name, List.of(), false);
    }

    /**
     * Creates varargs parameter documentation.
     *
     * @param name
     * the parameter name
     * @param allowedTypes
     * the allowed types for each vararg element
     *
     * @return varargs parameter documentation
     */
    public static ParameterDocumentation varArgs(String name, List<String> allowedTypes) {
        return new ParameterDocumentation(name, allowedTypes, true);
    }

}
