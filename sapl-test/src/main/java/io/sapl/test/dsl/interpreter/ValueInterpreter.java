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
package io.sapl.test.dsl.interpreter;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.test.SaplTestException;
import io.sapl.test.grammar.sapltest.Array;
import io.sapl.test.grammar.sapltest.FalseLiteral;
import io.sapl.test.grammar.sapltest.NullLiteral;
import io.sapl.test.grammar.sapltest.NumberLiteral;
import io.sapl.test.grammar.sapltest.Pair;
import io.sapl.test.grammar.sapltest.StringLiteral;
import io.sapl.test.grammar.sapltest.TrueLiteral;
import io.sapl.test.grammar.sapltest.UndefinedLiteral;

import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

class ValueInterpreter {

    Value getValueFromDslValue(io.sapl.test.grammar.sapltest.Value value) {
        if (value instanceof NumberLiteral numberLiteral) {
            var number = numberLiteral.getNumber();

            if (number == null) {
                throw new SaplTestException("Number is null.");
            }

            return Value.of(number);
        } else if (value instanceof StringLiteral stringLiteral) {
            var string = stringLiteral.getString();

            if (string == null) {
                throw new SaplTestException("String is null.");
            }

            return Value.of(string);
        } else if (value instanceof FalseLiteral) {
            return Value.FALSE;
        } else if (value instanceof TrueLiteral) {
            return Value.TRUE;
        } else if (value instanceof NullLiteral) {
            return Value.NULL;
        } else if (value instanceof UndefinedLiteral) {
            return Value.UNDEFINED;
        } else if (value instanceof io.sapl.test.grammar.sapltest.ErrorValue errorValue) {
            var message = errorValue.getMessage();
            return new ErrorValue(message);
        } else if (value instanceof Array array) {
            return interpretArray(array);
        } else if (value instanceof io.sapl.test.grammar.sapltest.Object object) {
            return interpretObject(object);
        }
        throw new SaplTestException("Unknown type of Value.");
    }

    private Value interpretArray(Array array) {
        var items = array.getItems();

        if (items == null || items.isEmpty()) {
            return Value.EMPTY_ARRAY;
        }

        var builder = ArrayValue.builder();
        for (var item : items) {
            builder.add(getValueFromDslValue(item));
        }
        return builder.build();
    }

    private Value interpretObject(io.sapl.test.grammar.sapltest.Object object) {
        var objectProperties = destructureObject(object);

        if (objectProperties.isEmpty()) {
            return Value.EMPTY_OBJECT;
        }

        var builder = ObjectValue.builder();
        objectProperties.forEach(builder::put);
        return builder.build();
    }

    Map<String, Value> destructureObject(io.sapl.test.grammar.sapltest.Object object) {
        if (object == null || object.getMembers() == null) {
            return Collections.emptyMap();
        }

        return object.getMembers().stream().collect(Collectors.toMap(Pair::getKey,
                pair -> getValueFromDslValue(pair.getValue()), (oldVal, newVal) -> newVal));
    }
}
