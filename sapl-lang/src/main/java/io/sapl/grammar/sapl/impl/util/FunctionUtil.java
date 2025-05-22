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
package io.sapl.grammar.sapl.impl.util;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.eclipse.emf.ecore.EObject;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.Arguments;
import io.sapl.grammar.sapl.Expression;
import io.sapl.grammar.sapl.FunctionIdentifier;
import io.sapl.interpreter.context.AuthorizationContext;
import lombok.experimental.UtilityClass;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@UtilityClass
public class FunctionUtil {

    public Flux<Val[]> combineArgumentFluxes(Arguments arguments) {
        if (arguments == null || arguments.getArgs().isEmpty())
            return Mono.just(new Val[0]).flux();

        return combine(argumentFluxes(arguments));
    }

    public Mono<Val> evaluateFunctionMono(EObject location, FunctionIdentifier identifier, Val... parameters) {
        return evaluateFunctionMono(location, functionIdentifierToReference(identifier), parameters);
    }

    public Mono<Val> evaluateFunctionMono(EObject location, String unresolvedFunctionName, Val... parameters) {
        return Mono.deferContextual(ctx -> Mono.just(AuthorizationContext.functionContext(ctx).evaluate(location,
                resolveAbsoluteFunctionName(location, unresolvedFunctionName),
                parameters)));
    }

    public Mono<Val> evaluateFunctionWithLeftHandArgumentMono(EObject location, FunctionIdentifier identifier,
            Val leftHandArgument, Val... parameters) {
        Val[] mergedParameters = new Val[parameters.length + 1];
        mergedParameters[0] = leftHandArgument;
        System.arraycopy(parameters, 0, mergedParameters, 1, parameters.length);
        return evaluateFunctionMono(location, identifier, mergedParameters);
    }

    private Stream<Flux<Val>> argumentFluxes(Arguments arguments) {
        return arguments.getArgs().stream().map(Expression::evaluate);
    }

    private Flux<Val[]> combine(Stream<Flux<Val>> argumentFluxes) {
        List<Flux<Val>> x = argumentFluxes.toList();
        return Flux.combineLatest(x, e -> Arrays.copyOf(e, e.length, Val[].class));
    }

    public String functionIdentifierToReference(FunctionIdentifier identifier) {
        if (null == identifier) {
            return "";
        }
        return String.join(".", identifier.getNameFragments());
    }

}
