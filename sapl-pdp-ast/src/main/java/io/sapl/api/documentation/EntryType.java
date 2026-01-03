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
 * Identifies the type of entry within a SAPL extension library.
 */
public enum EntryType {

    /**
     * A pure function that takes parameters and returns a value. Invoked using
     * {@code namespace.functionName(args)}.
     */
    FUNCTION,

    /**
     * An attribute finder that operates on an entity value. Invoked using
     * {@code entity.<namespace.attributeName(args)>}.
     */
    ATTRIBUTE,

    /**
     * An attribute finder that does not require an entity. Invoked using
     * {@code <namespace.attributeName(args)>}.
     */
    ENVIRONMENT_ATTRIBUTE

}
