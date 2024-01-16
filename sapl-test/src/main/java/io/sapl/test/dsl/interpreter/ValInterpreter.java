/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.interpreter.Val;
import io.sapl.test.SaplTestException;
import io.sapl.test.grammar.sAPLTest.Array;
import io.sapl.test.grammar.sAPLTest.FalseLiteral;
import io.sapl.test.grammar.sAPLTest.NullLiteral;
import io.sapl.test.grammar.sAPLTest.NumberLiteral;
import io.sapl.test.grammar.sAPLTest.Pair;
import io.sapl.test.grammar.sAPLTest.StringLiteral;
import io.sapl.test.grammar.sAPLTest.TrueLiteral;
import io.sapl.test.grammar.sAPLTest.UndefinedLiteral;
import io.sapl.test.grammar.sAPLTest.Value;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class ValInterpreter {

    private final ObjectMapper objectMapper;

    Val getValFromValue(final Value value) {
        if (value instanceof NumberLiteral intVal) {
            return Val.of(intVal.getNumber());
        } else if (value instanceof StringLiteral stringVal) {
            return Val.of(stringVal.getString());
        } else if (value instanceof FalseLiteral) {
            return Val.of(false);
        } else if (value instanceof TrueLiteral) {
            return Val.of(true);
        } else if (value instanceof NullLiteral) {
            return Val.NULL;
        } else if (value instanceof UndefinedLiteral) {
            return Val.UNDEFINED;
        } else if (value instanceof Array array) {
            return interpretArray(array);
        } else if (value instanceof io.sapl.test.grammar.sAPLTest.Object object) {
            return interpretObject(object);
        }
        throw new SaplTestException("Unknown type of Value");
    }

    private Val interpretArray(final Array array) {
        final var items = array.getItems();

        if (items == null || items.isEmpty()) {
            return Val.ofEmptyArray();
        }

        final var mappedItems = array.getItems().stream().map(item -> getValFromValue(item).get()).toList();

        final var arrayNode = objectMapper.createArrayNode();
        arrayNode.addAll(mappedItems);

        return Val.of(arrayNode);
    }

    private Val interpretObject(final io.sapl.test.grammar.sAPLTest.Object object) {
        final var objectProperties = destructureObject(object);

        if (objectProperties.isEmpty()) {
            return Val.ofEmptyObject();
        }

        final var objectNode = objectMapper.createObjectNode();
        objectNode.setAll(objectProperties);
        return Val.of(objectNode);
    }

    Map<String, JsonNode> destructureObject(final io.sapl.test.grammar.sAPLTest.Object object) {
        if (object == null || object.getMembers() == null) {
            return Collections.emptyMap();
        }

        return object.getMembers().stream().collect(Collectors.toMap(Pair::getKey,
                pair -> getValFromValue(pair.getValue()).get(), (oldVal, newVal) -> newVal));
    }
}
