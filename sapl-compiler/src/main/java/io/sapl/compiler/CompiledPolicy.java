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
package io.sapl.compiler;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.model.*;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import lombok.val;
import reactor.core.publisher.Flux;

import java.util.List;

public record CompiledPolicy(
        String name,
        Decision entitlement,
        CompiledExpression targetExpression,
        CompiledExpression body,
        List<CompiledExpression> obligations,
        List<CompiledExpression> advice,
        CompiledExpression transformation) {

    public boolean matches(EvaluationContext evaluationContext) {
        if (BooleanValue.TRUE.equals(targetExpression)) {
            return true;
        }
        if (targetExpression instanceof PureExpression pureTargetExpression) {
            val targetResult = pureTargetExpression.evaluate(evaluationContext);
            if (targetResult instanceof BooleanValue booleanValue) {
                return booleanValue.value();
            }
            throw new PolicyEvaluationException(
                    "Target expression did not return a boolean value, but: %s.".formatted(targetResult));
        }
        throw new PolicyEvaluationException(
                "Target expression not constant or pure expression. Should have been blocked by compilation.");
    }

    public Flux<AuthorizationDecision> evaluate(EvaluationContext evaluationContext) {
        val bodyFlux       = ExpressionCompiler.compiledExpressionToFlux(body);
        val evaluationFlux = bodyFlux.switchMap(bodyResult -> {
                               if (!(bodyResult instanceof BooleanValue)) {
                                   return Flux.just(AuthorizationDecision.INDETERMINATE);
                               }
                               if (BooleanValue.FALSE.equals(bodyResult)) {
                                   return Flux.just(AuthorizationDecision.NOT_APPLICABLE);
                               }

                               // TODO: evaluate the advice, obligations and tranformation via combine latest
                               // and merge the results into the decisions. If any of them yields an ErrorValue
                               // return AuthorizationDecision.INDETERMINATE
                               // create some utility methods for this
                               return Flux.just(
                                       new AuthorizationDecision(entitlement, List.of(), List.of(), Value.UNDEFINED));
                           });
        return evaluationFlux.contextWrite(ctx -> ctx.put(EvaluationContext.class, evaluationContext));
    }
}
