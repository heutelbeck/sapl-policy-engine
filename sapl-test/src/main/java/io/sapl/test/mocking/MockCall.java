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
package io.sapl.test.mocking;

import io.sapl.api.model.Value;
import io.sapl.test.SaplTestException;

import java.util.Arrays;

public class MockCall {

    private static final String ERROR_INVALID_ARGUMENT_INDEX = "Requested index %d for function call parameters but there are only %d parameters. Did you forget to check with \"getNumberOfArguments()\"?";

    private final Value[] parameter;

    public MockCall(Value... parameter) {
        this.parameter = parameter;
    }

    public int getNumberOfArguments() {
        return this.parameter.length;
    }

    public Value getArgument(int index) {
        if (index > this.parameter.length - 1) {
            throw new SaplTestException(ERROR_INVALID_ARGUMENT_INDEX.formatted(index, getNumberOfArguments()));
        }
        return this.parameter[index];
    }

    public Value[] getListOfArguments() {
        return Arrays.copyOf(this.parameter, this.parameter.length);
    }

}
