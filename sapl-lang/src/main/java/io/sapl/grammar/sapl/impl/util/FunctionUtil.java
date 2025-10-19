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

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.*;
import io.sapl.interpreter.context.AuthorizationContext;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.UtilityClass;
import org.eclipse.emf.ecore.EObject;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

@UtilityClass
public class FunctionUtil {

    public record ArgumentsAndOptions(Val[] arguments, Val options) {}

    public Flux<ArgumentsAndOptions> combineArgumentFluxesAndOptions(Arguments arguments, Expression options) {
        if (options == null) {
            return combineArgumentFluxes(arguments)
                    .map(evaluatedArguments -> new ArgumentsAndOptions(evaluatedArguments, null));
        }

        return Flux.combineLatest(combineArgumentFluxes(arguments), options.evaluate(), ArgumentsAndOptions::new);
    }

    public Flux<Val[]> combineArgumentFluxes(Arguments arguments) {
        if (arguments == null || arguments.getArgs().isEmpty())
            return Mono.just(new Val[0]).flux();

        return combine(argumentFluxes(arguments));
    }

    public Mono<Val> evaluateFunctionMono(EObject location, FunctionIdentifier identifier, Val... parameters) {
        final var fullyQualifiedFunctionName = resolveFunctionIdentifierByImports(location, identifier);
        return Mono.deferContextual(ctx -> Mono.just(
                AuthorizationContext.functionContext(ctx).evaluate(location, fullyQualifiedFunctionName, parameters)));
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

    public String resolveFunctionIdentifierByImports(EObject source, FunctionIdentifier identifier) {
        return resolveFunctionReferenceByImports(source, functionIdentifierToReference(identifier));
    }

    public String resolveFunctionReferenceByImports(EObject source, String nameReference) {
        if (nameReference.contains(".")) {
            return nameReference;
        }

        var sapl = getSourceSaplDocument(source);
        if (null == sapl) {
            return nameReference;
        }

        final var imports = sapl.getImports();
        if (null == imports) {
            return nameReference;
        }

        for (var currentImport : imports) {
            final var importedFunction = fullyQualifiedFunctionName(currentImport);
            if (nameReference.equals(currentImport.getFunctionAlias()) || importedFunction.endsWith(nameReference)) {
                return importedFunction;
            }
        }
        return nameReference;
    }

    private String fullyQualifiedFunctionName(Import anImport) {
        final var library = String.join(".", anImport.getLibSteps());
        return library + "." + anImport.getFunctionName();
    }

    private String functionIdentifierToReference(FunctionIdentifier identifier) {
        if (null == identifier) {
            return "";
        }
        return String.join(".", identifier.getNameFragments());
    }

    private SAPL getSourceSaplDocument(EObject source) {
        if (null == source) {
            return null;
        }
        if (source instanceof SAPL sapl) {
            return sapl;
        }
        return getSourceSaplDocument(source.eContainer());
    }

}
