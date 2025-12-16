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
package io.sapl.test.junit;

/**
 * Types of imports that can be registered with a test fixture.
 * <p>
 * Used by {@link JUnitTestAdapter#getFixtureRegistrations()} to register
 * function libraries and policy information points for test execution.
 */
public enum ImportType {

    /**
     * A policy information point (PIP) instance.
     */
    PIP,

    /**
     * A static PIP class.
     */
    STATIC_PIP,

    /**
     * A function library instance.
     */
    FUNCTION_LIBRARY,

    /**
     * A static function library class.
     */
    STATIC_FUNCTION_LIBRARY

}
