/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.grammar.sapl.impl.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import org.eclipse.emf.ecore.EObject;

import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.context.AuthorizationContext;
import lombok.experimental.UtilityClass;
import reactor.core.publisher.Flux;

@UtilityClass
public class StepAlgorithmUtil {
    private static final String ARRAY_ACCESS_TYPE_MISMATCH  = "Type mismatch. Expected an Array, but got: '%s'.";
    private static final String OBJECT_ACCESS_TYPE_MISMATCH = "Type mismatch. Expected an Object, but got: '%s'.";
    private static final String STEP_ACCESS_TYPE_MISMATCH   = "Type mismatch. Expected an Object or Array, but got: '%s'.";

    public Flux<Val> apply(Val parentValue, Supplier<Flux<Val>> selector, String stepParameters, Class<?> operationType,
            EObject location) {
        if (parentValue.isError()) {
            return Flux.just(parentValue.withParentTrace(operationType, true, parentValue));
        }
        if (parentValue.isArray()) {
            return applyOnArray(parentValue, selector, stepParameters, operationType, location);
        }
        if (parentValue.isObject()) {
            return applyOnObject(parentValue, selector, stepParameters, operationType, location);
        }
        return Flux.just(ErrorFactory.error(location, STEP_ACCESS_TYPE_MISMATCH, parentValue).withTrace(operationType,
                true, parentValue));
    }

    public static Flux<Val> applyOnArray(Val parentValue, Supplier<Flux<Val>> selector, String stepParameters,
            Class<?> operationType, EObject location) {
        if (parentValue.isError()) {
            return Flux.just(parentValue.withParentTrace(operationType, true, parentValue));
        }

        if (!parentValue.isArray()) {
            return Flux.just(ErrorFactory.error(location, ARRAY_ACCESS_TYPE_MISMATCH, parentValue)
                    .withParentTrace(operationType, true, parentValue));
        }

        if (parentValue.isEmpty()) {
            return Flux.just(Val.ofEmptyArray().withParentTrace(operationType, true, parentValue));
        }
        var array   = parentValue.getArrayNode();
        var results = new ArrayList<Flux<Val>>(array.size());
        for (int i = 0; i < array.size(); i++) {
            var element         = array.get(i);
            var elementValue    = Val.of(element);
            var index           = i;
            var condition       = selector.get().contextWrite(ctx -> AuthorizationContext.setRelativeNodeWithIndex(ctx,
                    elementValue.withTrace(operationType, true, Map.of("from", parentValue)), index));
            var selectedElement = condition.map(applySelectionToElement(elementValue, stepParameters, operationType,
                    parentValue, "array[" + index + "]"));
            results.add(selectedElement);
        }
        return Flux.combineLatest(results, RepackageUtil::recombineArray);
    }

    public static Flux<Val> applyOnObject(Val parentValue, Supplier<Flux<Val>> selector, String stepParameters,
            Class<?> operationType, EObject location) {
        if (parentValue.isError()) {
            return Flux.just(parentValue.withParentTrace(operationType, true, parentValue));
        }

        if (!parentValue.isObject()) {
            return Flux.just(ErrorFactory.error(location, OBJECT_ACCESS_TYPE_MISMATCH, parentValue)
                    .withParentTrace(operationType, true, parentValue));
        }

        if (parentValue.isEmpty()) {
            return Flux.just(Val.ofEmptyArray().withParentTrace(operationType, true, parentValue));
        }

        var object  = parentValue.getObjectNode();
        var results = new ArrayList<Flux<Val>>(object.size());
        var fields  = object.fields();
        while (fields.hasNext()) {
            var field     = fields.next();
            var key       = field.getKey();
            var value     = Val.of(field.getValue());
            var condition = selector.get().contextWrite(ctx -> AuthorizationContext.setRelativeNodeWithKey(ctx,
                    value.withTrace(operationType, true, Map.of("from", parentValue)), key));
            var selected  = condition
                    .map(applySelectionToElement(value, stepParameters, operationType, parentValue, key));
            results.add(selected);
        }
        return Flux.combineLatest(results, RepackageUtil::recombineArray);
    }

    private static Function<Val, Val> applySelectionToElement(Val elementValue, String stepParameters,
            Class<?> operationType, Val parentValue, String elementIdentifier) {
        return conditionResult -> {
            var trace = new HashMap<String, Val>();
            trace.put("parentValue", parentValue);
            trace.put("stepParameters", Val.of(stepParameters));
            trace.put(elementIdentifier, elementValue.withTrace(operationType, true, Map.of("from", parentValue)));
            trace.put("conditionResult", conditionResult);
            if (conditionResult.isError()) {
                return conditionResult.withTrace(operationType, true, trace);
            }
            if (conditionResult.isBoolean() && conditionResult.getBoolean()) {
                return elementValue.withTrace(operationType, true, trace);
            }
            // Treat non-boolean as FALSE
            return Val.UNDEFINED.withTrace(operationType, true, trace);
        };
    }

}
