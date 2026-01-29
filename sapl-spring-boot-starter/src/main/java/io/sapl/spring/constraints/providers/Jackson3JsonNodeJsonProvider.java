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
package io.sapl.spring.constraints.providers;

import com.jayway.jsonpath.InvalidJsonException;
import com.jayway.jsonpath.JsonPathException;
import com.jayway.jsonpath.spi.json.AbstractJsonProvider;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;

/**
 * JsonProvider implementation for jayway-jsonpath using Jackson 3
 * (tools.jackson namespace).
 * <p>
 * This provider enables JSONPath operations directly on Jackson 3's JsonNode
 * tree model, avoiding the need for Jackson 2 dependencies.
 * <p>
 * <b>Workaround:</b> This class exists because jayway-jsonpath does not yet
 * natively support Jackson 3. Once jayway adds official Jackson 3 support,
 * this class can be removed in favor of the official provider.
 *
 * @see <a href="https://github.com/json-path/JsonPath">jayway-jsonpath</a>
 */
public class Jackson3JsonNodeJsonProvider extends AbstractJsonProvider {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private final ObjectMapper objectMapper;

    public Jackson3JsonNodeJsonProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Object parse(String json) throws InvalidJsonException {
        try {
            return objectMapper.readTree(json);
        } catch (JacksonException e) {
            throw new InvalidJsonException(e, json);
        }
    }

    @Override
    public Object parse(InputStream jsonStream, String charset) throws InvalidJsonException {
        try {
            return objectMapper.readTree(jsonStream);
        } catch (JacksonException e) {
            throw new InvalidJsonException(e);
        }
    }

    @Override
    public String toJson(Object obj) {
        if (obj instanceof JsonNode node) {
            return node.toString();
        }
        throw new JsonPathException("Cannot convert object to JSON: " + obj.getClass().getName());
    }

    @Override
    public Object createArray() {
        return JSON.arrayNode();
    }

    @Override
    public Object createMap() {
        return JSON.objectNode();
    }

    @Override
    public boolean isArray(Object obj) {
        return obj instanceof ArrayNode;
    }

    @Override
    public boolean isMap(Object obj) {
        return obj instanceof ObjectNode;
    }

    @Override
    public int length(Object obj) {
        if (obj instanceof ArrayNode array) {
            return array.size();
        }
        if (obj instanceof ObjectNode object) {
            return object.size();
        }
        if (obj instanceof String string) {
            return string.length();
        }
        throw new JsonPathException("Cannot determine length of: " + obj.getClass().getName());
    }

    @Override
    public Iterable<?> toIterable(Object obj) {
        if (obj instanceof ArrayNode array) {
            return array;
        }
        if (obj instanceof ObjectNode object) {
            return new ArrayList<>(object.values());
        }
        throw new JsonPathException("Cannot iterate over: " + obj.getClass().getName());
    }

    @Override
    public Collection<String> getPropertyKeys(Object obj) {
        if (obj instanceof ObjectNode object) {
            return new ArrayList<>(object.propertyNames());
        }
        throw new JsonPathException("Cannot get property keys from: " + obj.getClass().getName());
    }

    @Override
    public Object getArrayIndex(Object obj, int idx) {
        if (obj instanceof ArrayNode array) {
            return array.get(idx);
        }
        throw new JsonPathException("Cannot get array index from: " + obj.getClass().getName());
    }

    @Override
    public void setArrayIndex(Object array, int index, Object newValue) {
        if (!(array instanceof ArrayNode arrayNode)) {
            throw new JsonPathException("Cannot set array index on: " + array.getClass().getName());
        }
        JsonNode node = toJsonNode(newValue);
        if (index == arrayNode.size()) {
            arrayNode.add(node);
        } else {
            arrayNode.set(index, node);
        }
    }

    @Override
    public Object getMapValue(Object obj, String key) {
        if (obj instanceof ObjectNode object) {
            JsonNode value = object.get(key);
            if (value == null) {
                return UNDEFINED;
            }
            return value;
        }
        throw new JsonPathException("Cannot get map value from: " + obj.getClass().getName());
    }

    @Override
    public void setProperty(Object obj, Object key, Object value) {
        if (obj instanceof ObjectNode object) {
            object.set(key.toString(), toJsonNode(value));
            return;
        }
        throw new JsonPathException("Cannot set property on: " + obj.getClass().getName());
    }

    @Override
    public void removeProperty(Object obj, Object key) {
        if (obj instanceof ObjectNode object) {
            object.remove(key.toString());
            return;
        }
        if (obj instanceof ArrayNode array) {
            int index = key instanceof Integer integer ? integer : Integer.parseInt(key.toString());
            array.remove(index);
            return;
        }
        throw new JsonPathException("Cannot remove property from: " + obj.getClass().getName());
    }

    @Override
    public Object unwrap(Object obj) {
        if (obj == null) {
            return null;
        }
        if (!(obj instanceof JsonNode node)) {
            return obj;
        }
        if (node.isNull()) {
            return null;
        }
        if (node.isString()) {
            return node.asString();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isInt()) {
            return node.asInt();
        }
        if (node.isLong()) {
            return node.asLong();
        }
        if (node.isBigInteger()) {
            return node.bigIntegerValue();
        }
        if (node.isDouble()) {
            return node.asDouble();
        }
        if (node.isBigDecimal()) {
            return node.decimalValue();
        }
        if (node.isFloat()) {
            return node.floatValue();
        }
        return obj;
    }

    private JsonNode toJsonNode(Object value) {
        if (value == null) {
            return JSON.nullNode();
        }
        if (value instanceof JsonNode node) {
            return node;
        }
        if (value instanceof String s) {
            return JSON.stringNode(s);
        }
        if (value instanceof Boolean b) {
            return JSON.booleanNode(b);
        }
        if (value instanceof Integer i) {
            return JSON.numberNode(i);
        }
        if (value instanceof Long l) {
            return JSON.numberNode(l);
        }
        if (value instanceof Double d) {
            return JSON.numberNode(d);
        }
        if (value instanceof Float f) {
            return JSON.numberNode(f);
        }
        if (value instanceof BigDecimal bd) {
            return JSON.numberNode(bd);
        }
        if (value instanceof BigInteger bi) {
            return JSON.numberNode(bi);
        }
        if (value instanceof Collection<?> collection) {
            ArrayNode array = JSON.arrayNode();
            for (Object item : collection) {
                array.add(toJsonNode(item));
            }
            return array;
        }
        return objectMapper.valueToTree(value);
    }

}
