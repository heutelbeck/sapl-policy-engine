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

import org.eclipse.emf.ecore.EObject;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.Expression;
import io.sapl.grammar.sapl.PolicyElement;
import lombok.experimental.UtilityClass;
import reactor.core.publisher.Mono;

@UtilityClass
public class MatchingUtil {
    public static final String CONDITION_NOT_BOOLEAN_ERROR = "Evaluation error: Target condition must evaluate to a boolean value, but was: '%s'.";

    /**
     * This method validates if the target condition matches a given subscription in
     * the context.
     *
     * @param targetExpression the explicit or implicit target expression
     * @param startObject the element from where the evaluation starts
     * @return a Mono of the matching result
     */
    public static Mono<Val> matches(Expression targetExpression, EObject startObject) {
        if (targetExpression == null) {
            return Mono.just(Val.TRUE);
        }

        return targetExpression.evaluate().contextWrite(ctx -> ImportsUtil.loadImportsIntoContext(startObject, ctx))
                .onErrorResume(error -> Mono.just(ErrorFactory.error(targetExpression, error))).next()
                .defaultIfEmpty(Val.FALSE).flatMap(result -> {
                    if (result.isError() || !result.isBoolean()) {
                        return Mono.just(ErrorFactory.error(targetExpression, CONDITION_NOT_BOOLEAN_ERROR, result)
                                .withTrace(PolicyElement.class, false, result));
                    }
                    return Mono.just(result);
                });
    }
}
