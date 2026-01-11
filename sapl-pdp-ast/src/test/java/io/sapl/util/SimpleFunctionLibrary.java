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
package io.sapl.util;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.model.*;
import lombok.val;

import java.math.BigDecimal;

/**
 * Simple function library for testing filter compilation.
 */
@FunctionLibrary(name = "simple", description = "Simple test functions")
public class SimpleFunctionLibrary {

    @Function(docs = "Appends strings to the input string")
    public static Value append(Value input, Value... parts) {
        if (!(input instanceof TextValue(String value))) {
            return Value.error("append requires text input");
        }
        val builder = new StringBuilder(value);
        for (val part : parts) {
            if (!(part instanceof TextValue(String value1))) {
                return Value.error("append requires text arguments");
            }
            builder.append(value1);
        }
        return Value.of(builder.toString());
    }

    @Function(docs = "Returns the length of a string or array")
    public static Value length(Value input) {
        return switch (input) {
        case TextValue text   -> Value.of(text.value().length());
        case ArrayValue array -> Value.of(array.size());
        default               -> Value.error("length requires text or array");
        };
    }

    @Function(name = "doubleValue", docs = "Doubles a number")
    public static Value doubleValue(Value input) {
        if (!(input instanceof NumberValue(BigDecimal value))) {
            return Value.error("double requires number input got:" + input);
        }
        return Value.of(value.multiply(BigDecimal.valueOf(2)));
    }

    @Function(docs = "Negates a boolean")
    public static Value negate(Value input) {
        if (!(input instanceof BooleanValue(boolean value))) {
            return Value.error("negate requires boolean input");
        }
        return Value.of(!value);
    }

    @Function(docs = "Adds a value to a number")
    public static Value addValue(Value input, Value addend) {
        if (!(input instanceof NumberValue(BigDecimal value))) {
            return Value.error("addValue requires number input");
        }
        if (!(addend instanceof NumberValue(BigDecimal value1))) {
            return Value.error("addValue requires number addend");
        }
        return Value.of(value.add(value1));
    }

    @Function(docs = "Returns the input unchanged")
    public static Value identity(Value input) {
        return input;
    }
}
