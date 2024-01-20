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

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.impl.util.FunctionUtil;
import reactor.core.publisher.Flux;

/**
 * Implements the evaluation of functions.
 * <p>
 * Grammar: {BasicFunction} fsteps+=ID ('.' fsteps+=ID)* arguments=Arguments
 * steps+=Step*; {Arguments} '(' (args+=Expression (',' args+=Expression)*)?
 * ')';
 */
public class BasicFunctionImplCustom extends BasicFunctionImpl {

    @Override
    public Flux<Val> evaluate() {
        return FunctionUtil.combineArgumentFluxes(arguments)
                .concatMap(parameters -> FunctionUtil.evaluateFunctionMono(fsteps, parameters))
                .switchMap(v -> resolveStepsFiltersAndSubTemplates(steps).apply(v));
    }

}
