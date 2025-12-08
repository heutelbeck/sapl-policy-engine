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
package io.sapl.util;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.Value;
import lombok.experimental.UtilityClass;
import lombok.val;

/**
 * Mock function library for testing filter compilation.
 * <p>
 * Provides utility functions for test cases, mirroring the TestFunctionLibrary
 * from sapl-lang.
 */
@UtilityClass
@FunctionLibrary(name = "mock", description = "Mock test functions")
public class MockFunctionLibrary {

    /**
     * Returns null, ignoring all parameters.
     * <p>
     * Used in tests to verify filter behavior with null-producing functions.
     *
     * @param parameters
     * ignored parameters
     *
     * @return null value
     */
    @Function(docs = "Returns null, ignoring all parameters")
    public static Value nil(Value... parameters) {
        return Value.NULL;
    }

    /**
     * Returns an empty string, ignoring all parameters.
     * <p>
     * Used in tests to verify filter replacement behavior.
     *
     * @param parameters
     * ignored parameters
     *
     * @return empty string value
     */
    @Function(docs = "Returns an empty string, ignoring all parameters")
    public static Value emptyString(Value... parameters) {
        return Value.of("");
    }

    /**
     * Returns an error value with a test error message.
     * <p>
     * Used in tests to verify error propagation in filters.
     *
     * @param parameters
     * ignored parameters
     *
     * @return error value
     */
    @Function(docs = "Returns an error value for testing")
    public static Value error(Value... parameters) {
        return Value.error("INTENTIONALLY CREATED TEST ERROR");
    }

    /**
     * Throws a runtime exception.
     * <p>
     * Used in tests to verify exception handling in filters.
     *
     * @param parameters
     * ignored parameters
     *
     * @throws RuntimeException
     * always thrown
     */
    @Function(docs = "Throws a runtime exception for testing")
    public static Value exception(Value... parameters) {
        throw new RuntimeException("INTENTIONALLY THROWN TEST EXCEPTION");
    }

    /**
     * Returns an array containing all parameters.
     * <p>
     * Used in tests to verify parameter passing in filters.
     *
     * @param parameters
     * the parameters to collect
     *
     * @return array containing all parameters
     */
    @Function(docs = "Returns an array of all parameters")
    public static Value parameters(Value... parameters) {
        val builder = ArrayValue.builder();
        for (val param : parameters) {
            builder.add(param);
        }
        return builder.build();
    }
}
