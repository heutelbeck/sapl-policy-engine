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
package io.sapl.plugins;

import io.sapl.api.plugins.*;
import io.sapl.api.value.Value;
import lombok.val;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class DefaultStaticPluginsServer implements StaticPlugInsServer {

    private final Map<String, List<FunctionSpecification>> functionIndex = new ConcurrentHashMap<>();

    public void loadFunction(FunctionSpecification functionSpecification) {
        functionIndex.compute(functionSpecification.functionName(), (functionName, oldFunctions) -> {
            if (oldFunctions == null) {
                val newList = new ArrayList<FunctionSpecification>();
                newList.add(functionSpecification);
                return newList;
            }
            for (val spec : oldFunctions) {
                if (spec.collidesWith(functionSpecification)) {
                    throw new IllegalArgumentException(
                            "Function collision error. Attempted to load new function from plug-in.");
                }
                oldFunctions.add(functionSpecification);
            }
            return oldFunctions;
        });
    }

    @Override
    public Value evaluateFunction(FunctionInvocation invocation) {
        val matchingFunction = new AtomicReference<FunctionSpecification>();
        functionIndex.compute(invocation.functionName(), (name, specsAndFunctions) -> {
            if (specsAndFunctions != null) {
                var match = Match.NO_MATCH;
                for (val spec : specsAndFunctions) {
                    val newMatch = invocation.matches(spec);
                    if (newMatch.isBetterThan(match)) {
                        match = newMatch;
                        matchingFunction.set(spec);
                    }
                }

            }
            return specsAndFunctions;
        });
        val function = matchingFunction.get();
        if (matchingFunction.get() == null) {
            return Value.error("No matching function found for " + invocation + ".");
        }
        return function.function().apply(invocation);
    }

    @Override
    public Flux<Value> streamStaticAttributes(AttributeFinderInvocation invocation) {
        return null;
    }
}
