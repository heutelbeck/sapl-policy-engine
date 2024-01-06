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
package io.sapl.grammar.sapl.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.IntStream;

import io.sapl.api.interpreter.Traced;
import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.Pair;
import reactor.core.publisher.Flux;

/**
 * Implementation of an object in SAPL.
 * <p>
 * Grammar: Object returns Value: {Object} '{' (members+=Pair (','
 * members+=Pair)*)? '}' ;
 */
public class ObjectImplCustom extends ObjectImpl {

    /**
     * The semantics of evaluating an object is as follows:
     * <p>
     * An object may contain a list of attribute name-value pairs. To get the values
     * of the individual attributes, these have to be recursively evaluated.
     * <p>
     * Returning a Flux this means to subscribe to all attribute-value expression
     * result Fluxes and to combineLatest into a new object each time one of the
     * expression Fluxes emits a new value.
     */
    @Override
    public Flux<Val> evaluate() {
        // collect all attribute names (keys) and fluxes providing the evaluated values
        final List<String>    keys        = new ArrayList<>(getMembers().size());
        final List<Flux<Val>> valueFluxes = new ArrayList<>(getMembers().size());
        for (Pair member : getMembers()) {
            keys.add(member.getKey());

            valueFluxes.add(member.getValue().evaluate());
        }

        // handle the empty object
        if (valueFluxes.isEmpty()) {
            return Flux.just(Val.of(Val.JSON.objectNode()).withTrace(Object.class));
        }

        // the indices of the keys correspond to the indices of the values, because
        // combineLatest() preserves the order of the given list of fluxes in the array
        // of values passed to the combinator function
        return Flux.combineLatest(valueFluxes, values -> {
            var result       = Val.JSON.objectNode();
            var tracedValues = new HashMap<String, Traced>();
            // omit undefined fields
            IntStream.range(0, values.length).forEach(idx -> {
                var key   = keys.get(idx);
                var value = ((Val) values[idx]);
                value.ifDefined(val -> result.set(key, val));
                tracedValues.put(key, value);
            });
            return Val.of(result).withTrace(Object.class, tracedValues);
        });
    }

}
