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

import io.sapl.api.model.*;
import io.sapl.api.pdp.Decision;
import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.core.publisher.Flux;

import java.util.List;

@UtilityClass
public class CombiningAlgorithmCompiler {

    public static CompiledExpression firstApplicable(List<CompiledPolicy> compiledPolicies,
            CompilationContext context) {
        if (compiledPolicies.isEmpty()) {
            return SaplCompiler.buildDecisionObject(Decision.NOT_APPLICABLE, List.of(), List.of(), Value.UNDEFINED);
        }
        // Optimization: Check if all policies are pure or constant
        val allPureOrConstant = compiledPolicies.stream().allMatch(
                p -> p.decisionExpression() instanceof Value || p.decisionExpression() instanceof PureExpression);

        if (allPureOrConstant) {
            // Pure path - no streaming needed
            return new PureExpression(ctx -> {
                for (val compiledPolicy : compiledPolicies) {
                    val matches = evalValueOrPure(compiledPolicy.matchExpression(), ctx);
                    if (!(matches instanceof BooleanValue matchesBool)) {
                        return SaplCompiler.buildDecisionObject(Decision.INDETERMINATE, List.of(), List.of(),
                                Value.UNDEFINED);
                    }
                    if (matchesBool.value()) {
                        Value decision = evalValueOrPure(compiledPolicy.decisionExpression(), ctx);
                        if (!(decision instanceof ObjectValue objectValue)) {
                            return SaplCompiler.buildDecisionObject(Decision.INDETERMINATE, List.of(), List.of(),
                                    Value.UNDEFINED);
                        }
                        val decisionAttribute = objectValue.get("decision");
                        if (!(decisionAttribute instanceof TextValue textValue)) {
                            return SaplCompiler.buildDecisionObject(Decision.INDETERMINATE, List.of(), List.of(),
                                    Value.UNDEFINED);
                        }
                        try {
                            val d = Decision.valueOf(textValue.value());
                            if (d == Decision.NOT_APPLICABLE) {
                                continue;
                            }
                            return decision;
                        } catch (IllegalArgumentException e) {
                            return SaplCompiler.buildDecisionObject(Decision.INDETERMINATE, List.of(), List.of(),
                                    Value.UNDEFINED);
                        }
                    }
                }
                // No policy was applicable
                return SaplCompiler.buildDecisionObject(Decision.NOT_APPLICABLE, List.of(), List.of(), Value.UNDEFINED);
            }, true);
        }

        var decisionStream = Flux
                .just(SaplCompiler.buildDecisionObject(Decision.NOT_APPLICABLE, List.of(), List.of(), Value.UNDEFINED));
        for (var i = compiledPolicies.size() - 1; i >= 0; i--) {
            val compiledPolicy = compiledPolicies.get(i);
            val matchFlux      = ExpressionCompiler.compiledExpressionToFlux(compiledPolicy.matchExpression());
            val previousStream = decisionStream;
            decisionStream = matchFlux.switchMap(matches -> {
                if (!(matches instanceof BooleanValue matchesBool)) {
                    return Flux.just(SaplCompiler.buildDecisionObject(Decision.INDETERMINATE, List.of(), List.of(),
                            Value.UNDEFINED));
                }
                if (matchesBool.value()) {
                    return ExpressionCompiler.compiledExpressionToFlux(compiledPolicy.decisionExpression())
                            .switchMap(decision -> {
                                if (!(decision instanceof ObjectValue objectValue)) {
                                    return Flux.just(SaplCompiler.buildDecisionObject(Decision.INDETERMINATE, List.of(),
                                            List.of(), Value.UNDEFINED));
                                }
                                val decisionAttribute = objectValue.get("decision");
                                if (!(decisionAttribute instanceof TextValue textValue)) {
                                    return Flux.just(SaplCompiler.buildDecisionObject(Decision.INDETERMINATE, List.of(),
                                            List.of(), Value.UNDEFINED));
                                }
                                try {
                                    val d = Decision.valueOf(textValue.value());
                                    if (d == Decision.NOT_APPLICABLE) {
                                        return previousStream;
                                    }
                                    return Flux.just(decision);
                                } catch (IllegalArgumentException e) {
                                    return Flux.just(SaplCompiler.buildDecisionObject(Decision.INDETERMINATE, List.of(),
                                            List.of(), Value.UNDEFINED));
                                }
                            });
                }
                return previousStream;
            });
        }
        return new StreamExpression(decisionStream);
    }

    public static CompiledExpression denyUnlessPermit(List<CompiledPolicy> compiledPolicies,
                                                      CompilationContext context) {
        return Value.error("UNIMPLEMENTED");
    }

    private Value evalValueOrPure(CompiledExpression e, EvaluationContext ctx) {
        if (e instanceof Value v) {
            return v;
        }
        return ((PureExpression) e).evaluate(ctx);
    }

    public static CompiledExpression denyOverrides(List<CompiledPolicy> compiledPolicies, CompilationContext context) {
        return Value.error("UNIMPLEMENTED");
    }

    public static CompiledExpression onlyOneApplicable(List<CompiledPolicy> compiledPolicies,
            CompilationContext context) {
        return Value.error("UNIMPLEMENTED");
    }

    public static CompiledExpression permitUnlessDeny(List<CompiledPolicy> compiledPolicies,
            CompilationContext context) {
        return Value.error("UNIMPLEMENTED");
    }

    public static CompiledExpression permitOverrides(List<CompiledPolicy> compiledPolicies,
            CompilationContext context) {
        return Value.error("UNIMPLEMENTED");
    }
}
