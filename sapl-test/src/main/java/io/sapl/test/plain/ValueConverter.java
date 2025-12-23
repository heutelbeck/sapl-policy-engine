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
package io.sapl.test.plain;

import io.sapl.api.model.Value;
import io.sapl.test.grammar.antlr.SAPLTestParser.*;
import lombok.experimental.UtilityClass;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.sapl.compiler.StringsUtil.unquoteString;

/**
 * Converts grammar value nodes to SAPL Value objects.
 */
@UtilityClass
class ValueConverter {

    /**
     * Converts a grammar value context to a SAPL Value.
     *
     * @param ctx the value context from the grammar
     * @return the corresponding SAPL Value
     */
    static Value convert(ValueContext ctx) {
        return switch (ctx) {
        case ObjectValContext obj        -> convertObject(obj.objectValue());
        case ArrayValContext arr         -> convertArray(arr.arrayValue());
        case NumberValContext num        -> convertNumber(num.numberLiteral());
        case StringValContext str        -> convertString(str.stringLiteral());
        case BooleanValContext bool      -> convertBoolean(bool.booleanLiteral());
        case NullValContext ignored      -> Value.NULL;
        case UndefinedValContext ignored -> Value.UNDEFINED;
        default                          ->
            throw new IllegalArgumentException("Unknown value type: " + ctx.getClass().getSimpleName());
        };
    }

    /**
     * Converts a valueOrError context to a SAPL Value.
     * For error values, returns a special error marker.
     */
    static ValueOrError convertValueOrError(ValueOrErrorContext ctx) {
        if (ctx instanceof RegularValueContext regular) {
            return new ValueOrError(convert(regular.value()), false, null);
        } else if (ctx instanceof ErrorValContext error) {
            var errorValue = error.errorValue();
            var message    = errorValue.message != null ? unquoteString(errorValue.message.getText()) : null;
            return new ValueOrError(null, true, message);
        }
        throw new IllegalArgumentException("Unknown valueOrError type: " + ctx.getClass().getSimpleName());
    }

    /**
     * Converts an object value context to a SAPL Value.
     */
    static Value convertObject(ObjectValueContext ctx) {
        Map<String, Value> map = new LinkedHashMap<>();
        for (var pair : ctx.pair()) {
            var key   = unquoteString(pair.key.getText());
            var value = convert(pair.pairValue);
            map.put(key, value);
        }
        return Value.ofObject(map);
    }

    /**
     * Converts an object value context to a Map.
     */
    static Map<String, Value> convertObjectToMap(ObjectValueContext ctx) {
        Map<String, Value> map = new LinkedHashMap<>();
        for (var pair : ctx.pair()) {
            var key   = unquoteString(pair.key.getText());
            var value = convert(pair.pairValue);
            map.put(key, value);
        }
        return map;
    }

    /**
     * Converts an array value context to a SAPL Value.
     */
    static Value convertArray(ArrayValueContext ctx) {
        List<Value> items = new ArrayList<>();
        for (var item : ctx.items) {
            items.add(convert(item));
        }
        return Value.ofArray(items);
    }

    /**
     * Converts a number literal to a SAPL Value.
     */
    static Value convertNumber(NumberLiteralContext ctx) {
        var text = ctx.NUMBER().getText();
        return Value.of(new BigDecimal(text));
    }

    /**
     * Converts a string literal to a SAPL Value.
     */
    static Value convertString(StringLiteralContext ctx) {
        return Value.of(unquoteString(ctx.STRING().getText()));
    }

    /**
     * Converts a boolean literal to a SAPL Value.
     */
    static Value convertBoolean(BooleanLiteralContext ctx) {
        return ctx instanceof TrueLiteralContext ? Value.TRUE : Value.FALSE;
    }

    /**
     * Represents a value or an error marker.
     */
    record ValueOrError(Value value, boolean isError, String errorMessage) {
        boolean hasValue() {
            return !isError && value != null;
        }
    }
}
