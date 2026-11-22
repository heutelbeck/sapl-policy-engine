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
package io.sapl.functions;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.sapl.api.functions.FunctionInvocation;
import io.sapl.api.functions.FunctionSpecification;
import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueRepresentation;
import lombok.val;

/** Successful calls in one configuration. Keys preserve array order, object iteration order and decimal scale. */
final class FunctionResultCache {
    private static final int ENTRY_BYTES    = 256;
    private static final int MAX_CALL_BYTES = 64 * 1024;
    private static final int MAX_DEPTH      = 48;

    private final @Nullable Cache<Key, Result> results;
    private final Cache<Value, Object>         constants;
    private final int                          callBudget;

    FunctionResultCache(int capacity) {
        long bytes = Math.max(0L, capacity) * 1024L;
        callBudget = (int) Math.min(bytes, MAX_CALL_BYTES);
        constants  = Caffeine.newBuilder().weakKeys().maximumSize(Math.max(0, capacity)).build();
        if (capacity > 0) {
            results = Caffeine.newBuilder().maximumWeight(bytes)
                    .weigher((Key key, Result result) -> Math.max(1024, result.bytes())).build();
        } else {
            results = null;
        }
    }

    void registerConstant(Value value) {
        if (results != null && (value instanceof ObjectValue || value instanceof ArrayValue)
                && !ValueRepresentation.containsError(value)) {
            constants.get(value, ignored -> new Object());
        }
    }

    Value evaluate(FunctionSpecification function, FunctionInvocation invocation) {
        if (results == null) {
            return function.function().apply(invocation);
        }
        int remaining = callBudget - ENTRY_BYTES;
        val arguments = new ArrayList<Object>(invocation.arguments().size());
        for (val argument : invocation.arguments()) {
            val token = argument instanceof ObjectValue || argument instanceof ArrayValue
                    ? constants.getIfPresent(argument)
                    : null;
            if (token == null) {
                remaining = remainingAfter(argument, remaining, 0);
                arguments.add(new ValueRepresentation(argument));
            } else {
                remaining -= 64;
                arguments.add(token);
            }
            if (remaining < 0) {
                return function.function().apply(invocation);
            }
        }
        val key    = new Key(function, arguments);
        val cached = results.getIfPresent(key);
        if (cached != null) {
            return cached.value();
        }
        val value = function.function().apply(invocation);
        remaining = remainingAfter(value, remaining, 0);
        if (remaining >= 0) {
            results.put(key, new Result(value, callBudget - remaining));
        }
        return value;
    }

    // Bound the walk before hashing or comparing inputs. Errors keep their own source location.
    private static int remainingAfter(Value value, int budget, int depth) {
        if (depth >= MAX_DEPTH || budget < 64 || value instanceof ErrorValue) {
            return -1;
        }
        int left = budget - 64;
        return switch (value) {
        case TextValue(var text)     -> afterText(left, text);
        case NumberValue(var number) -> left - (number.unscaledValue().bitLength() / Byte.SIZE + 1);
        case ArrayValue array        -> {
            for (val element : array) {
                left = remainingAfter(element, left, depth + 1);
                if (left < 0) {
                    break;
                }
            }
            yield left;
        }
        case ObjectValue object      -> {
            for (val field : object.entrySet()) {
                left = remainingAfter(field.getValue(), afterText(left, field.getKey()), depth + 1);
                if (left < 0) {
                    break;
                }
            }
            yield left;
        }
        default                      -> left;
        };
    }

    private static int afterText(int budget, String text) {
        return text.length() <= budget / 2 ? budget - 2 * text.length() : -1;
    }

    private record Key(FunctionSpecification function, List<Object> arguments) {}

    private record Result(Value value, int bytes) {}
}
