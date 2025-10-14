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
package io.sapl.grammar.sapl.impl;

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.api.interpreter.Trace;
import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.ElementOf;
import reactor.core.publisher.Flux;

import java.util.Map;

import static io.sapl.grammar.sapl.impl.util.OperatorUtil.operator;

/**
 * Implements the evaluation of the 'in-array' operation. It checks if a value
 * is contained in an array.
 * <p>
 * Grammar: {@code Comparison returns Expression: Prefixed
 * (({ElementOf.left=current} 'in') right=Prefixed)? ;}
 */
public class ElementOfImplCustom extends ElementOfImpl {

    @Override
    public Flux<Val> evaluate() {
        return operator(this, this, ElementOfImplCustom::elementOf);
    }

    public static Val elementOf(Val needle, Val haystack) {
        return rawElementOf(needle, haystack).withTrace(ElementOf.class, false,
                Map.of(Trace.NEEDLE, needle, Trace.HAYSTACK, haystack));
    }

    private static Val rawElementOf(Val needle, Val haystack) {
        if (needle.isUndefined() || haystack.isUndefined() || !haystack.isArray())
            return Val.FALSE;

        for (JsonNode arrayItem : haystack.get())
            if (needleAndArrayElementAreEquivalent(needle, arrayItem))
                return Val.TRUE;

        return Val.FALSE;
    }

    private static boolean needleAndArrayElementAreEquivalent(Val needle, JsonNode arrayItem) {
        return (bothValuesAreNumbers(needle, arrayItem) && bothNumbersAreEqual(needle, arrayItem))
                || needle.get().equals(arrayItem);
    }

    private static boolean bothValuesAreNumbers(Val needle, JsonNode arrayItem) {
        return needle.isNumber() && arrayItem.isNumber();
    }

    private static boolean bothNumbersAreEqual(Val needle, JsonNode arrayItem) {
        return needle.get().decimalValue().compareTo(arrayItem.decimalValue()) == 0;
    }

}
