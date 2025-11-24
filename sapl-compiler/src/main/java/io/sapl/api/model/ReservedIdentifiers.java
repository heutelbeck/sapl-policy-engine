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
package io.sapl.api.model;

import lombok.experimental.UtilityClass;

import java.util.Set;

/**
 * Reserved identifiers in SAPL that cannot be used as variable names. These
 * identifiers are automatically populated in
 * the evaluation context from the authorization subscription or during
 * expression evaluation.
 */
@UtilityClass
public class ReservedIdentifiers {
    public static final String ACTION            = "action";
    public static final String ENVIRONMENT       = "environment";
    public static final String RESOURCE          = "resource";
    public static final String SUBJECT           = "subject";
    public static final String RELATIVE_VALUE    = "@";
    public static final String RELATIVE_LOCATION = "#";

    /**
     * All reserved identifiers that cannot be used as variable names in policies.
     * Attempting to define a variable with
     * any of these names will result in a PolicyEvaluationException.
     */
    public static final Set<String> RESERVED_IDENTIFIERS = Set.of(ACTION, ENVIRONMENT, RESOURCE, SUBJECT,
            RELATIVE_VALUE, RELATIVE_LOCATION);

    /**
     * Identifiers populated from the authorization subscription. These represent
     * the core access control decision
     * context: who (subject) wants to do what (action) on which (resource) under
     * what conditions (environment).
     */
    public static final Set<String> SUBSCRIPTION_IDENTIFIERS = Set.of(ACTION, ENVIRONMENT, RESOURCE, SUBJECT);

    /**
     * Identifiers set during filter step evaluation. The relative value (@) is the
     * current element being processed, and
     * the relative location (#) is its position (array index or object key).
     */
    public static final Set<String> RELATIVE_IDENTIFIERS = Set.of(RELATIVE_VALUE, RELATIVE_LOCATION);

}
