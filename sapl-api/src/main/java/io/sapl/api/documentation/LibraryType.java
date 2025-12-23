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
package io.sapl.api.documentation;

/**
 * Identifies the type of a SAPL extension library.
 */
public enum LibraryType {

    /**
     * A function library provides pure functions callable from SAPL expressions.
     * Functions are invoked using the syntax
     * {@code namespace.functionName(args)}.
     */
    FUNCTION_LIBRARY,

    /**
     * A Policy Information Point provides attribute finders that retrieve external
     * data during policy evaluation.
     * Attributes are accessed using the syntax {@code <namespace.attributeName>}
     * for environment attributes or
     * {@code entity.<namespace.attributeName>} for entity attributes.
     */
    POLICY_INFORMATION_POINT

}
