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

import java.util.ArrayList;
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
        if (compiledPolicies.isEmpty()) {
            return SaplCompiler.buildDecisionObject(Decision.DENY, List.of(), List.of(), Value.UNDEFINED);
        }

        // Check if all decision expressions are pure or constant
        val allPureOrConstant = compiledPolicies.stream().allMatch(
                p -> p.decisionExpression() instanceof Value || p.decisionExpression() instanceof PureExpression);

        if (allPureOrConstant) {
            // Pure path - evaluate all decisions synchronously
            return new PureExpression(ctx -> evaluateDenyUnlessPermitPure(compiledPolicies, ctx), true);
        } else {
            // Streaming path - use Flux.combineLatest
            return new StreamExpression(buildDenyUnlessPermitStream(compiledPolicies));
        }
    }

    private static Value evaluateDenyUnlessPermitPure(List<CompiledPolicy> compiledPolicies, EvaluationContext ctx) {
        var   entitlement       = Decision.DENY; // Default for deny-unless-permit
        Value resource          = Value.UNDEFINED;
        var   hasResource       = false;
        val   permitObligations = new ArrayList<Value>();
        val   permitAdvice      = new ArrayList<Value>();
        val   denyObligations   = new ArrayList<Value>();
        val   denyAdvice        = new ArrayList<Value>();

        for (val compiledPolicy : compiledPolicies) {
            // Evaluate match expression
            val matches = evalValueOrPure(compiledPolicy.matchExpression(), ctx);
            if (!(matches instanceof BooleanValue matchesBool)) {
                // Match expression error - treat as non-matching
                continue;
            }
            if (!matchesBool.value()) {
                // Policy doesn't match
                continue;
            }

            // Policy matches - evaluate decision expression
            val decision = evalValueOrPure(compiledPolicy.decisionExpression(), ctx);
            if (!(decision instanceof ObjectValue decisionObj)) {
                // Invalid decision structure - skip
                continue;
            }

            // Extract decision value
            val decisionAttr = decisionObj.get("decision");
            if (!(decisionAttr instanceof TextValue decisionText)) {
                continue;
            }

            Decision policyDecision;
            try {
                policyDecision = Decision.valueOf(decisionText.value());
            } catch (IllegalArgumentException e) {
                continue;
            }

            // Collect obligations and advice
            val obligations = decisionObj.get("obligations");
            val advice      = decisionObj.get("advice");

            if (policyDecision == Decision.PERMIT) {
                entitlement = Decision.PERMIT;

                // Check for transformation uncertainty
                val policyResource = decisionObj.get("resource");
                if (!(policyResource instanceof UndefinedValue)) {
                    if (hasResource) {
                        // Transformation uncertainty - revert to DENY
                        entitlement = Decision.DENY;
                    } else {
                        resource    = policyResource;
                        hasResource = true;
                    }
                }

                // Collect PERMIT obligations and advice
                if (obligations instanceof ArrayValue obligationsArray) {
                    permitObligations.addAll(obligationsArray);
                }
                if (advice instanceof ArrayValue adviceArray) {
                    permitAdvice.addAll(adviceArray);
                }
            } else if (policyDecision == Decision.DENY) {
                // Collect DENY obligations and advice
                if (obligations instanceof ArrayValue obligationsArray) {
                    denyObligations.addAll(obligationsArray);
                }
                if (advice instanceof ArrayValue adviceArray) {
                    denyAdvice.addAll(adviceArray);
                }
            }
        }

        // Build final decision with appropriate obligations/advice
        val finalObligations = entitlement == Decision.PERMIT ? permitObligations : denyObligations;
        val finalAdvice      = entitlement == Decision.PERMIT ? permitAdvice : denyAdvice;

        return SaplCompiler.buildDecisionObject(entitlement, finalObligations, finalAdvice, resource);
    }

    private static Flux<Value> buildDenyUnlessPermitStream(List<CompiledPolicy> compiledPolicies) {
        // Convert each policy to a Flux<PolicyEvaluation>
        val policyFluxes = new ArrayList<Flux<PolicyEvaluation>>();

        for (val compiledPolicy : compiledPolicies) {
            val matchFlux  = ExpressionCompiler.compiledExpressionToFlux(compiledPolicy.matchExpression());
            val policyFlux = matchFlux.switchMap(matches -> {
                               if (!(matches instanceof BooleanValue matchesBool) || !matchesBool.value()) {
                                   // Not matched - return NOT_APPLICABLE
                                   return Flux.just(new PolicyEvaluation(Decision.NOT_APPLICABLE, Value.UNDEFINED,
                                           new ArrayValue(List.of(), false), new ArrayValue(List.of(), false)));
                               }

                               // Matched - evaluate decision
                               return ExpressionCompiler.compiledExpressionToFlux(compiledPolicy.decisionExpression())
                                       .map(decision -> {
                                                          if (!(decision instanceof ObjectValue decisionObj)) {
                                                              return new PolicyEvaluation(Decision.NOT_APPLICABLE,
                                                                      Value.UNDEFINED, new ArrayValue(List.of(), false),
                                                                      new ArrayValue(List.of(), false));
                                                          }

                                                          val decisionAttr = decisionObj.get("decision");
                                                          if (!(decisionAttr instanceof TextValue decisionText)) {
                                                              return new PolicyEvaluation(Decision.NOT_APPLICABLE,
                                                                      Value.UNDEFINED, new ArrayValue(List.of(), false),
                                                                      new ArrayValue(List.of(), false));
                                                          }

                                                          Decision policyDecision;
                                                          try {
                                                              policyDecision = Decision.valueOf(decisionText.value());
                                                          } catch (IllegalArgumentException e) {
                                                              return new PolicyEvaluation(Decision.NOT_APPLICABLE,
                                                                      Value.UNDEFINED, new ArrayValue(List.of(), false),
                                                                      new ArrayValue(List.of(), false));
                                                          }

                                                          val resource = decisionObj.get("resource");
                                                          val obligations = decisionObj.get("obligations");
                                                          val advice = decisionObj.get("advice");

                                                          val obligationsArray = obligations instanceof ArrayValue arr ? arr
                                                                  : new ArrayValue(List.of(), false);
                                                          val adviceArray = advice instanceof ArrayValue arr ? arr
                                                                  : new ArrayValue(List.of(), false);

                                                          return new PolicyEvaluation(policyDecision, resource,
                                                                  obligationsArray, adviceArray);
                                                      });
                           });
            policyFluxes.add(policyFlux);
        }

        // Combine all policy evaluations
        return Flux.combineLatest(policyFluxes, evaluations -> {
            var   entitlement       = Decision.DENY; // Default for deny-unless-permit
            Value resource          = Value.UNDEFINED;
            var   hasResource       = false;
            val   permitObligations = new ArrayList<Value>();
            val   permitAdvice      = new ArrayList<Value>();
            val   denyObligations   = new ArrayList<Value>();
            val   denyAdvice        = new ArrayList<Value>();

            for (val evaluation : evaluations) {
                val policyEval = (PolicyEvaluation) evaluation;

                if (policyEval.decision == Decision.PERMIT) {
                    entitlement = Decision.PERMIT;

                    // Check for transformation uncertainty
                    if (!(policyEval.resource instanceof UndefinedValue)) {
                        if (hasResource) {
                            // Transformation uncertainty - revert to DENY
                            entitlement = Decision.DENY;
                        } else {
                            resource    = policyEval.resource;
                            hasResource = true;
                        }
                    }

                    // Collect PERMIT obligations and advice
                    permitObligations.addAll(policyEval.obligations);
                    permitAdvice.addAll(policyEval.advice);
                } else if (policyEval.decision == Decision.DENY) {
                    // Collect DENY obligations and advice
                    denyObligations.addAll(policyEval.obligations);
                    denyAdvice.addAll(policyEval.advice);
                }
            }

            // Build final decision with appropriate obligations/advice
            val finalObligations = entitlement == Decision.PERMIT ? permitObligations : denyObligations;
            val finalAdvice      = entitlement == Decision.PERMIT ? permitAdvice : denyAdvice;

            return SaplCompiler.buildDecisionObject(entitlement, finalObligations, finalAdvice, resource);
        });
    }

    // Helper record for streaming evaluation
    private record PolicyEvaluation(Decision decision, Value resource, ArrayValue obligations, ArrayValue advice) {}

    private static Value evalValueOrPure(CompiledExpression e, EvaluationContext ctx) {
        if (e instanceof Value v) {
            return v;
        }
        return ((PureExpression) e).evaluate(ctx);
    }

    public static CompiledExpression denyOverrides(List<CompiledPolicy> compiledPolicies, CompilationContext context) {
        if (compiledPolicies.isEmpty()) {
            return SaplCompiler.buildDecisionObject(Decision.NOT_APPLICABLE, List.of(), List.of(), Value.UNDEFINED);
        }

        // Check if all decision expressions are pure or constant
        val allPureOrConstant = compiledPolicies.stream().allMatch(
                p -> p.decisionExpression() instanceof Value || p.decisionExpression() instanceof PureExpression);

        if (allPureOrConstant) {
            // Pure path - evaluate all decisions synchronously
            return new PureExpression(ctx -> evaluateDenyOverridesPure(compiledPolicies, ctx), true);
        } else {
            // Streaming path - use Flux.combineLatest
            return new StreamExpression(buildDenyOverridesStream(compiledPolicies));
        }
    }

    private static Value evaluateDenyOverridesPure(List<CompiledPolicy> compiledPolicies, EvaluationContext ctx) {
        var   entitlement       = Decision.NOT_APPLICABLE;
        Value resource          = Value.UNDEFINED;
        var   hasResource       = false;
        val   permitObligations = new ArrayList<Value>();
        val   permitAdvice      = new ArrayList<Value>();
        val   denyObligations   = new ArrayList<Value>();
        val   denyAdvice        = new ArrayList<Value>();
        var   hasIndeterminate  = false;

        for (val compiledPolicy : compiledPolicies) {
            // Evaluate match expression
            val matches = evalValueOrPure(compiledPolicy.matchExpression(), ctx);
            if (!(matches instanceof BooleanValue matchesBool)) {
                // Match expression error - treat as non-matching
                continue;
            }
            if (!matchesBool.value()) {
                // Policy doesn't match
                continue;
            }

            // Policy matches - evaluate decision expression
            val decision = evalValueOrPure(compiledPolicy.decisionExpression(), ctx);
            if (!(decision instanceof ObjectValue decisionObj)) {
                // Invalid decision structure - skip
                continue;
            }

            // Extract decision value
            val decisionAttr = decisionObj.get("decision");
            if (!(decisionAttr instanceof TextValue decisionText)) {
                continue;
            }

            Decision policyDecision;
            try {
                policyDecision = Decision.valueOf(decisionText.value());
            } catch (IllegalArgumentException e) {
                continue;
            }

            // Collect obligations and advice
            val obligations = decisionObj.get("obligations");
            val advice      = decisionObj.get("advice");

            // Apply deny-overrides logic
            if (policyDecision == Decision.DENY) {
                entitlement = Decision.DENY;
            }
            if (policyDecision == Decision.INDETERMINATE && entitlement != Decision.DENY) {
                entitlement      = Decision.INDETERMINATE;
                hasIndeterminate = true;
            }
            if (policyDecision == Decision.PERMIT && entitlement == Decision.NOT_APPLICABLE) {
                entitlement = Decision.PERMIT;
            }

            // Handle transformations
            val policyResource = decisionObj.get("resource");
            if (!(policyResource instanceof UndefinedValue)) {
                if (hasResource && entitlement != Decision.DENY) {
                    // Transformation uncertainty - set to INDETERMINATE unless DENY
                    entitlement = Decision.INDETERMINATE;
                } else if (!hasResource) {
                    resource    = policyResource;
                    hasResource = true;
                }
            }

            // Collect obligations and advice
            if (policyDecision == Decision.PERMIT) {
                if (obligations instanceof ArrayValue obligationsArray) {
                    permitObligations.addAll(obligationsArray);
                }
                if (advice instanceof ArrayValue adviceArray) {
                    permitAdvice.addAll(adviceArray);
                }
            } else if (policyDecision == Decision.DENY) {
                if (obligations instanceof ArrayValue obligationsArray) {
                    denyObligations.addAll(obligationsArray);
                }
                if (advice instanceof ArrayValue adviceArray) {
                    denyAdvice.addAll(adviceArray);
                }
            }
        }

        // Build final decision with appropriate obligations/advice
        val finalObligations = entitlement == Decision.PERMIT ? permitObligations
                : entitlement == Decision.DENY ? denyObligations : new ArrayList<Value>();
        val finalAdvice      = entitlement == Decision.PERMIT ? permitAdvice
                : entitlement == Decision.DENY ? denyAdvice : new ArrayList<Value>();

        return SaplCompiler.buildDecisionObject(entitlement, finalObligations, finalAdvice, resource);
    }

    private static Flux<Value> buildDenyOverridesStream(List<CompiledPolicy> compiledPolicies) {
        // Convert each policy to a Flux<PolicyEvaluation>
        val policyFluxes = new ArrayList<Flux<PolicyEvaluation>>();

        for (val compiledPolicy : compiledPolicies) {
            val matchFlux  = ExpressionCompiler.compiledExpressionToFlux(compiledPolicy.matchExpression());
            val policyFlux = matchFlux.switchMap(matches -> {
                               if (!(matches instanceof BooleanValue matchesBool) || !matchesBool.value()) {
                                   // Not matched - return NOT_APPLICABLE
                                   return Flux.just(new PolicyEvaluation(Decision.NOT_APPLICABLE, Value.UNDEFINED,
                                           new ArrayValue(List.of(), false), new ArrayValue(List.of(), false)));
                               }

                               // Matched - evaluate decision
                               return ExpressionCompiler.compiledExpressionToFlux(compiledPolicy.decisionExpression())
                                       .map(decision -> {
                                                          if (!(decision instanceof ObjectValue decisionObj)) {
                                                              return new PolicyEvaluation(Decision.NOT_APPLICABLE,
                                                                      Value.UNDEFINED, new ArrayValue(List.of(), false),
                                                                      new ArrayValue(List.of(), false));
                                                          }

                                                          val decisionAttr = decisionObj.get("decision");
                                                          if (!(decisionAttr instanceof TextValue decisionText)) {
                                                              return new PolicyEvaluation(Decision.NOT_APPLICABLE,
                                                                      Value.UNDEFINED, new ArrayValue(List.of(), false),
                                                                      new ArrayValue(List.of(), false));
                                                          }

                                                          Decision policyDecision;
                                                          try {
                                                              policyDecision = Decision.valueOf(decisionText.value());
                                                          } catch (IllegalArgumentException e) {
                                                              return new PolicyEvaluation(Decision.NOT_APPLICABLE,
                                                                      Value.UNDEFINED, new ArrayValue(List.of(), false),
                                                                      new ArrayValue(List.of(), false));
                                                          }

                                                          val resource = decisionObj.get("resource");
                                                          val obligations = decisionObj.get("obligations");
                                                          val advice = decisionObj.get("advice");

                                                          val obligationsArray = obligations instanceof ArrayValue arr ? arr
                                                                  : new ArrayValue(List.of(), false);
                                                          val adviceArray = advice instanceof ArrayValue arr ? arr
                                                                  : new ArrayValue(List.of(), false);

                                                          return new PolicyEvaluation(policyDecision, resource,
                                                                  obligationsArray, adviceArray);
                                                      });
                           });
            policyFluxes.add(policyFlux);
        }

        // Combine all policy evaluations
        return Flux.combineLatest(policyFluxes, evaluations -> {
            var   entitlement       = Decision.NOT_APPLICABLE;
            Value resource          = Value.UNDEFINED;
            var   hasResource       = false;
            val   permitObligations = new ArrayList<Value>();
            val   permitAdvice      = new ArrayList<Value>();
            val   denyObligations   = new ArrayList<Value>();
            val   denyAdvice        = new ArrayList<Value>();

            for (val evaluation : evaluations) {
                val policyEval = (PolicyEvaluation) evaluation;

                // Apply deny-overrides logic
                if (policyEval.decision == Decision.DENY) {
                    entitlement = Decision.DENY;
                }
                if (policyEval.decision == Decision.INDETERMINATE && entitlement != Decision.DENY) {
                    entitlement = Decision.INDETERMINATE;
                }
                if (policyEval.decision == Decision.PERMIT && entitlement == Decision.NOT_APPLICABLE) {
                    entitlement = Decision.PERMIT;
                }

                // Handle transformation uncertainty
                if (!(policyEval.resource instanceof UndefinedValue)) {
                    if (hasResource && entitlement != Decision.DENY) {
                        // Transformation uncertainty
                        entitlement = Decision.INDETERMINATE;
                    } else if (!hasResource) {
                        resource    = policyEval.resource;
                        hasResource = true;
                    }
                }

                // Collect obligations and advice
                if (policyEval.decision == Decision.PERMIT) {
                    permitObligations.addAll(policyEval.obligations);
                    permitAdvice.addAll(policyEval.advice);
                } else if (policyEval.decision == Decision.DENY) {
                    denyObligations.addAll(policyEval.obligations);
                    denyAdvice.addAll(policyEval.advice);
                }
            }

            // Build final decision with appropriate obligations/advice
            val finalObligations = entitlement == Decision.PERMIT ? permitObligations
                    : entitlement == Decision.DENY ? denyObligations : new ArrayList<Value>();
            val finalAdvice      = entitlement == Decision.PERMIT ? permitAdvice
                    : entitlement == Decision.DENY ? denyAdvice : new ArrayList<Value>();

            return SaplCompiler.buildDecisionObject(entitlement, finalObligations, finalAdvice, resource);
        });
    }

    public static CompiledExpression permitOverrides(List<CompiledPolicy> compiledPolicies,
            CompilationContext context) {
        if (compiledPolicies.isEmpty()) {
            return SaplCompiler.buildDecisionObject(Decision.NOT_APPLICABLE, List.of(), List.of(), Value.UNDEFINED);
        }

        val allPureOrConstant = compiledPolicies.stream().allMatch(
                p -> p.decisionExpression() instanceof Value || p.decisionExpression() instanceof PureExpression);

        if (allPureOrConstant) {
            return new PureExpression(ctx -> evaluatePermitOverridesPure(compiledPolicies, ctx), true);
        } else {
            return new StreamExpression(buildPermitOverridesStream(compiledPolicies));
        }
    }

    private static Value evaluatePermitOverridesPure(List<CompiledPolicy> compiledPolicies, EvaluationContext ctx) {
        var   entitlement       = Decision.NOT_APPLICABLE;
        Value resource          = Value.UNDEFINED;
        var   hasResource       = false;
        val   permitObligations = new ArrayList<Value>();
        val   permitAdvice      = new ArrayList<Value>();
        val   denyObligations   = new ArrayList<Value>();
        val   denyAdvice        = new ArrayList<Value>();

        for (val compiledPolicy : compiledPolicies) {
            val matches = evalValueOrPure(compiledPolicy.matchExpression(), ctx);
            if (!(matches instanceof BooleanValue matchesBool) || !matchesBool.value()) {
                continue;
            }

            val decision = evalValueOrPure(compiledPolicy.decisionExpression(), ctx);
            if (!(decision instanceof ObjectValue decisionObj)) {
                continue;
            }

            val decisionAttr = decisionObj.get("decision");
            if (!(decisionAttr instanceof TextValue decisionText)) {
                continue;
            }

            Decision policyDecision;
            try {
                policyDecision = Decision.valueOf(decisionText.value());
            } catch (IllegalArgumentException e) {
                continue;
            }

            val obligations = decisionObj.get("obligations");
            val advice      = decisionObj.get("advice");

            // Apply permit-overrides logic
            if (policyDecision == Decision.PERMIT) {
                entitlement = Decision.PERMIT;
            }
            if (policyDecision == Decision.INDETERMINATE && entitlement != Decision.PERMIT) {
                entitlement = Decision.INDETERMINATE;
            }
            if (policyDecision == Decision.DENY && entitlement == Decision.NOT_APPLICABLE) {
                entitlement = Decision.DENY;
            }

            // Handle transformations
            val policyResource = decisionObj.get("resource");
            if (!(policyResource instanceof UndefinedValue)) {
                if (hasResource) {
                    // Transformation uncertainty
                    entitlement = Decision.INDETERMINATE;
                } else {
                    resource    = policyResource;
                    hasResource = true;
                }
            }

            // Collect obligations and advice
            if (policyDecision == Decision.PERMIT) {
                if (obligations instanceof ArrayValue obligationsArray) {
                    permitObligations.addAll(obligationsArray);
                }
                if (advice instanceof ArrayValue adviceArray) {
                    permitAdvice.addAll(adviceArray);
                }
            } else if (policyDecision == Decision.DENY) {
                if (obligations instanceof ArrayValue obligationsArray) {
                    denyObligations.addAll(obligationsArray);
                }
                if (advice instanceof ArrayValue adviceArray) {
                    denyAdvice.addAll(adviceArray);
                }
            }
        }

        val finalObligations = entitlement == Decision.PERMIT ? permitObligations
                : entitlement == Decision.DENY ? denyObligations : new ArrayList<Value>();
        val finalAdvice      = entitlement == Decision.PERMIT ? permitAdvice
                : entitlement == Decision.DENY ? denyAdvice : new ArrayList<Value>();

        return SaplCompiler.buildDecisionObject(entitlement, finalObligations, finalAdvice, resource);
    }

    private static Flux<Value> buildPermitOverridesStream(List<CompiledPolicy> compiledPolicies) {
        val policyFluxes = new ArrayList<Flux<PolicyEvaluation>>();

        for (val compiledPolicy : compiledPolicies) {
            val matchFlux  = ExpressionCompiler.compiledExpressionToFlux(compiledPolicy.matchExpression());
            val policyFlux = matchFlux.switchMap(matches -> {
                               if (!(matches instanceof BooleanValue matchesBool) || !matchesBool.value()) {
                                   return Flux.just(new PolicyEvaluation(Decision.NOT_APPLICABLE, Value.UNDEFINED,
                                           new ArrayValue(List.of(), false), new ArrayValue(List.of(), false)));
                               }

                               return ExpressionCompiler.compiledExpressionToFlux(compiledPolicy.decisionExpression())
                                       .map(decision -> {
                                                          if (!(decision instanceof ObjectValue decisionObj)) {
                                                              return new PolicyEvaluation(Decision.NOT_APPLICABLE,
                                                                      Value.UNDEFINED, new ArrayValue(List.of(), false),
                                                                      new ArrayValue(List.of(), false));
                                                          }

                                                          val decisionAttr = decisionObj.get("decision");
                                                          if (!(decisionAttr instanceof TextValue decisionText)) {
                                                              return new PolicyEvaluation(Decision.NOT_APPLICABLE,
                                                                      Value.UNDEFINED, new ArrayValue(List.of(), false),
                                                                      new ArrayValue(List.of(), false));
                                                          }

                                                          Decision policyDecision;
                                                          try {
                                                              policyDecision = Decision.valueOf(decisionText.value());
                                                          } catch (IllegalArgumentException e) {
                                                              return new PolicyEvaluation(Decision.NOT_APPLICABLE,
                                                                      Value.UNDEFINED, new ArrayValue(List.of(), false),
                                                                      new ArrayValue(List.of(), false));
                                                          }

                                                          val resource = decisionObj.get("resource");
                                                          val obligations = decisionObj.get("obligations");
                                                          val advice = decisionObj.get("advice");
                                                          val obligationsArray = obligations instanceof ArrayValue arr ? arr
                                                                  : new ArrayValue(List.of(), false);
                                                          val adviceArray = advice instanceof ArrayValue arr ? arr
                                                                  : new ArrayValue(List.of(), false);

                                                          return new PolicyEvaluation(policyDecision, resource,
                                                                  obligationsArray, adviceArray);
                                                      });
                           });
            policyFluxes.add(policyFlux);
        }

        return Flux.combineLatest(policyFluxes, evaluations -> {
            var   entitlement       = Decision.NOT_APPLICABLE;
            Value resource          = Value.UNDEFINED;
            var   hasResource       = false;
            val   permitObligations = new ArrayList<Value>();
            val   permitAdvice      = new ArrayList<Value>();
            val   denyObligations   = new ArrayList<Value>();
            val   denyAdvice        = new ArrayList<Value>();

            for (val evaluation : evaluations) {
                val policyEval = (PolicyEvaluation) evaluation;

                // Apply permit-overrides logic
                if (policyEval.decision == Decision.PERMIT) {
                    entitlement = Decision.PERMIT;
                }
                if (policyEval.decision == Decision.INDETERMINATE && entitlement != Decision.PERMIT) {
                    entitlement = Decision.INDETERMINATE;
                }
                if (policyEval.decision == Decision.DENY && entitlement == Decision.NOT_APPLICABLE) {
                    entitlement = Decision.DENY;
                }

                // Handle transformation uncertainty
                if (!(policyEval.resource instanceof UndefinedValue)) {
                    if (hasResource) {
                        entitlement = Decision.INDETERMINATE;
                    } else {
                        resource    = policyEval.resource;
                        hasResource = true;
                    }
                }

                // Collect obligations and advice
                if (policyEval.decision == Decision.PERMIT) {
                    permitObligations.addAll(policyEval.obligations);
                    permitAdvice.addAll(policyEval.advice);
                } else if (policyEval.decision == Decision.DENY) {
                    denyObligations.addAll(policyEval.obligations);
                    denyAdvice.addAll(policyEval.advice);
                }
            }

            val finalObligations = entitlement == Decision.PERMIT ? permitObligations
                    : entitlement == Decision.DENY ? denyObligations : new ArrayList<Value>();
            val finalAdvice      = entitlement == Decision.PERMIT ? permitAdvice
                    : entitlement == Decision.DENY ? denyAdvice : new ArrayList<Value>();

            return SaplCompiler.buildDecisionObject(entitlement, finalObligations, finalAdvice, resource);
        });
    }

    public static CompiledExpression permitUnlessDeny(List<CompiledPolicy> compiledPolicies,
            CompilationContext context) {
        if (compiledPolicies.isEmpty()) {
            return SaplCompiler.buildDecisionObject(Decision.PERMIT, List.of(), List.of(), Value.UNDEFINED);
        }

        val allPureOrConstant = compiledPolicies.stream().allMatch(
                p -> p.decisionExpression() instanceof Value || p.decisionExpression() instanceof PureExpression);

        if (allPureOrConstant) {
            return new PureExpression(ctx -> evaluatePermitUnlessDenyPure(compiledPolicies, ctx), true);
        } else {
            return new StreamExpression(buildPermitUnlessDenyStream(compiledPolicies));
        }
    }

    private static Value evaluatePermitUnlessDenyPure(List<CompiledPolicy> compiledPolicies, EvaluationContext ctx) {
        var   entitlement       = Decision.PERMIT; // Default for permit-unless-deny
        Value resource          = Value.UNDEFINED;
        var   hasResource       = false;
        val   permitObligations = new ArrayList<Value>();
        val   permitAdvice      = new ArrayList<Value>();
        val   denyObligations   = new ArrayList<Value>();
        val   denyAdvice        = new ArrayList<Value>();

        for (val compiledPolicy : compiledPolicies) {
            val matches = evalValueOrPure(compiledPolicy.matchExpression(), ctx);
            if (!(matches instanceof BooleanValue matchesBool) || !matchesBool.value()) {
                continue;
            }

            val decision = evalValueOrPure(compiledPolicy.decisionExpression(), ctx);
            if (!(decision instanceof ObjectValue decisionObj)) {
                continue;
            }

            val decisionAttr = decisionObj.get("decision");
            if (!(decisionAttr instanceof TextValue decisionText)) {
                continue;
            }

            Decision policyDecision;
            try {
                policyDecision = Decision.valueOf(decisionText.value());
            } catch (IllegalArgumentException e) {
                continue;
            }

            val obligations = decisionObj.get("obligations");
            val advice      = decisionObj.get("advice");

            // Apply permit-unless-deny logic
            if (policyDecision == Decision.DENY) {
                entitlement = Decision.DENY;
            }

            // Handle transformations
            val policyResource = decisionObj.get("resource");
            if (!(policyResource instanceof UndefinedValue)) {
                if (hasResource) {
                    // Transformation uncertainty - revert to DENY
                    entitlement = Decision.DENY;
                } else {
                    resource    = policyResource;
                    hasResource = true;
                }
            }

            // Collect obligations and advice
            if (policyDecision == Decision.PERMIT) {
                if (obligations instanceof ArrayValue obligationsArray) {
                    permitObligations.addAll(obligationsArray);
                }
                if (advice instanceof ArrayValue adviceArray) {
                    permitAdvice.addAll(adviceArray);
                }
            } else if (policyDecision == Decision.DENY) {
                if (obligations instanceof ArrayValue obligationsArray) {
                    denyObligations.addAll(obligationsArray);
                }
                if (advice instanceof ArrayValue adviceArray) {
                    denyAdvice.addAll(adviceArray);
                }
            }
        }

        val finalObligations = entitlement == Decision.PERMIT ? permitObligations : denyObligations;
        val finalAdvice      = entitlement == Decision.PERMIT ? permitAdvice : denyAdvice;

        return SaplCompiler.buildDecisionObject(entitlement, finalObligations, finalAdvice, resource);
    }

    private static Flux<Value> buildPermitUnlessDenyStream(List<CompiledPolicy> compiledPolicies) {
        val policyFluxes = new ArrayList<Flux<PolicyEvaluation>>();

        for (val compiledPolicy : compiledPolicies) {
            val matchFlux  = ExpressionCompiler.compiledExpressionToFlux(compiledPolicy.matchExpression());
            val policyFlux = matchFlux.switchMap(matches -> {
                               if (!(matches instanceof BooleanValue matchesBool) || !matchesBool.value()) {
                                   return Flux.just(new PolicyEvaluation(Decision.NOT_APPLICABLE, Value.UNDEFINED,
                                           new ArrayValue(List.of(), false), new ArrayValue(List.of(), false)));
                               }

                               return ExpressionCompiler.compiledExpressionToFlux(compiledPolicy.decisionExpression())
                                       .map(decision -> {
                                                          if (!(decision instanceof ObjectValue decisionObj)) {
                                                              return new PolicyEvaluation(Decision.NOT_APPLICABLE,
                                                                      Value.UNDEFINED, new ArrayValue(List.of(), false),
                                                                      new ArrayValue(List.of(), false));
                                                          }

                                                          val decisionAttr = decisionObj.get("decision");
                                                          if (!(decisionAttr instanceof TextValue decisionText)) {
                                                              return new PolicyEvaluation(Decision.NOT_APPLICABLE,
                                                                      Value.UNDEFINED, new ArrayValue(List.of(), false),
                                                                      new ArrayValue(List.of(), false));
                                                          }

                                                          Decision policyDecision;
                                                          try {
                                                              policyDecision = Decision.valueOf(decisionText.value());
                                                          } catch (IllegalArgumentException e) {
                                                              return new PolicyEvaluation(Decision.NOT_APPLICABLE,
                                                                      Value.UNDEFINED, new ArrayValue(List.of(), false),
                                                                      new ArrayValue(List.of(), false));
                                                          }

                                                          val resource = decisionObj.get("resource");
                                                          val obligations = decisionObj.get("obligations");
                                                          val advice = decisionObj.get("advice");
                                                          val obligationsArray = obligations instanceof ArrayValue arr ? arr
                                                                  : new ArrayValue(List.of(), false);
                                                          val adviceArray = advice instanceof ArrayValue arr ? arr
                                                                  : new ArrayValue(List.of(), false);

                                                          return new PolicyEvaluation(policyDecision, resource,
                                                                  obligationsArray, adviceArray);
                                                      });
                           });
            policyFluxes.add(policyFlux);
        }

        return Flux.combineLatest(policyFluxes, evaluations -> {
            var   entitlement       = Decision.PERMIT; // Default for permit-unless-deny
            Value resource          = Value.UNDEFINED;
            var   hasResource       = false;
            val   permitObligations = new ArrayList<Value>();
            val   permitAdvice      = new ArrayList<Value>();
            val   denyObligations   = new ArrayList<Value>();
            val   denyAdvice        = new ArrayList<Value>();

            for (val evaluation : evaluations) {
                val policyEval = (PolicyEvaluation) evaluation;

                // Apply permit-unless-deny logic
                if (policyEval.decision == Decision.DENY) {
                    entitlement = Decision.DENY;
                }

                // Handle transformation uncertainty
                if (!(policyEval.resource instanceof UndefinedValue)) {
                    if (hasResource) {
                        entitlement = Decision.DENY;
                    } else {
                        resource    = policyEval.resource;
                        hasResource = true;
                    }
                }

                // Collect obligations and advice
                if (policyEval.decision == Decision.PERMIT) {
                    permitObligations.addAll(policyEval.obligations);
                    permitAdvice.addAll(policyEval.advice);
                } else if (policyEval.decision == Decision.DENY) {
                    denyObligations.addAll(policyEval.obligations);
                    denyAdvice.addAll(policyEval.advice);
                }
            }

            val finalObligations = entitlement == Decision.PERMIT ? permitObligations : denyObligations;
            val finalAdvice      = entitlement == Decision.PERMIT ? permitAdvice : denyAdvice;

            return SaplCompiler.buildDecisionObject(entitlement, finalObligations, finalAdvice, resource);
        });
    }

    public static CompiledExpression onlyOneApplicable(List<CompiledPolicy> compiledPolicies,
            CompilationContext context) {
        if (compiledPolicies.isEmpty()) {
            return SaplCompiler.buildDecisionObject(Decision.NOT_APPLICABLE, List.of(), List.of(), Value.UNDEFINED);
        }

        val allPureOrConstant = compiledPolicies.stream().allMatch(
                p -> p.decisionExpression() instanceof Value || p.decisionExpression() instanceof PureExpression);

        if (allPureOrConstant) {
            return new PureExpression(ctx -> evaluateOnlyOneApplicablePure(compiledPolicies, ctx), true);
        } else {
            return new StreamExpression(buildOnlyOneApplicableStream(compiledPolicies));
        }
    }

    private static Value evaluateOnlyOneApplicablePure(List<CompiledPolicy> compiledPolicies, EvaluationContext ctx) {
        var   applicableCount    = 0;
        var   hasIndeterminate   = false;
        Value applicableDecision = null;

        for (val compiledPolicy : compiledPolicies) {
            val matches = evalValueOrPure(compiledPolicy.matchExpression(), ctx);
            if (!(matches instanceof BooleanValue matchesBool) || !matchesBool.value()) {
                continue;
            }

            val decision = evalValueOrPure(compiledPolicy.decisionExpression(), ctx);
            if (!(decision instanceof ObjectValue decisionObj)) {
                continue;
            }

            val decisionAttr = decisionObj.get("decision");
            if (!(decisionAttr instanceof TextValue decisionText)) {
                continue;
            }

            Decision policyDecision;
            try {
                policyDecision = Decision.valueOf(decisionText.value());
            } catch (IllegalArgumentException e) {
                continue;
            }

            if (policyDecision != Decision.NOT_APPLICABLE) {
                applicableCount++;
                applicableDecision = decision;
                if (policyDecision == Decision.INDETERMINATE) {
                    hasIndeterminate = true;
                }
            }
        }

        // Return INDETERMINATE if multiple applicable or any INDETERMINATE
        if (hasIndeterminate || applicableCount > 1) {
            return SaplCompiler.buildDecisionObject(Decision.INDETERMINATE, List.of(), List.of(), Value.UNDEFINED);
        }

        // Return the single applicable decision or NOT_APPLICABLE
        if (applicableCount == 1) {
            return applicableDecision;
        }

        return SaplCompiler.buildDecisionObject(Decision.NOT_APPLICABLE, List.of(), List.of(), Value.UNDEFINED);
    }

    private static Flux<Value> buildOnlyOneApplicableStream(List<CompiledPolicy> compiledPolicies) {
        val policyFluxes = new ArrayList<Flux<PolicyEvaluation>>();

        for (val compiledPolicy : compiledPolicies) {
            val matchFlux  = ExpressionCompiler.compiledExpressionToFlux(compiledPolicy.matchExpression());
            val policyFlux = matchFlux.switchMap(matches -> {
                               if (!(matches instanceof BooleanValue matchesBool) || !matchesBool.value()) {
                                   return Flux.just(new PolicyEvaluation(Decision.NOT_APPLICABLE, Value.UNDEFINED,
                                           new ArrayValue(List.of(), false), new ArrayValue(List.of(), false)));
                               }

                               return ExpressionCompiler.compiledExpressionToFlux(compiledPolicy.decisionExpression())
                                       .map(decision -> {
                                                          if (!(decision instanceof ObjectValue decisionObj)) {
                                                              return new PolicyEvaluation(Decision.NOT_APPLICABLE,
                                                                      Value.UNDEFINED, new ArrayValue(List.of(), false),
                                                                      new ArrayValue(List.of(), false));
                                                          }

                                                          val decisionAttr = decisionObj.get("decision");
                                                          if (!(decisionAttr instanceof TextValue decisionText)) {
                                                              return new PolicyEvaluation(Decision.NOT_APPLICABLE,
                                                                      Value.UNDEFINED, new ArrayValue(List.of(), false),
                                                                      new ArrayValue(List.of(), false));
                                                          }

                                                          Decision policyDecision;
                                                          try {
                                                              policyDecision = Decision.valueOf(decisionText.value());
                                                          } catch (IllegalArgumentException e) {
                                                              return new PolicyEvaluation(Decision.NOT_APPLICABLE,
                                                                      Value.UNDEFINED, new ArrayValue(List.of(), false),
                                                                      new ArrayValue(List.of(), false));
                                                          }

                                                          val resource = decisionObj.get("resource");
                                                          val obligations = decisionObj.get("obligations");
                                                          val advice = decisionObj.get("advice");
                                                          val obligationsArray = obligations instanceof ArrayValue arr ? arr
                                                                  : new ArrayValue(List.of(), false);
                                                          val adviceArray = advice instanceof ArrayValue arr ? arr
                                                                  : new ArrayValue(List.of(), false);

                                                          return new PolicyEvaluation(policyDecision, resource,
                                                                  obligationsArray, adviceArray);
                                                      });
                           });
            policyFluxes.add(policyFlux);
        }

        return Flux.combineLatest(policyFluxes, evaluations -> {
            var   applicableCount    = 0;
            var   hasIndeterminate   = false;
            Value applicableDecision = null;

            for (val evaluation : evaluations) {
                val policyEval = (PolicyEvaluation) evaluation;

                if (policyEval.decision != Decision.NOT_APPLICABLE) {
                    applicableCount++;
                    if (policyEval.decision == Decision.INDETERMINATE) {
                        hasIndeterminate = true;
                    }
                    // Store the full decision object for potential return
                    if (applicableDecision == null) {
                        applicableDecision = SaplCompiler.buildDecisionObject(policyEval.decision,
                                List.copyOf(policyEval.obligations), List.copyOf(policyEval.advice),
                                policyEval.resource);
                    }
                }
            }

            // Return INDETERMINATE if multiple applicable or any INDETERMINATE
            if (hasIndeterminate || applicableCount > 1) {
                return SaplCompiler.buildDecisionObject(Decision.INDETERMINATE, List.of(), List.of(), Value.UNDEFINED);
            }

            // Return the single applicable decision or NOT_APPLICABLE
            if (applicableCount == 1) {
                return applicableDecision;
            }

            return SaplCompiler.buildDecisionObject(Decision.NOT_APPLICABLE, List.of(), List.of(), Value.UNDEFINED);
        });
    }
}
