package io.sapl.grammar.sapl.impl;


import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.emf.common.util.EList;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.Response;
import io.sapl.grammar.sapl.Condition;
import io.sapl.grammar.sapl.PolicyBody;
import io.sapl.grammar.sapl.Statement;
import io.sapl.grammar.sapl.ValueDefinition;
import io.sapl.interpreter.EvaluationContext;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Slf4j
public class PolicyImplCustom extends PolicyImpl {

    private static final String STATEMENT_NOT_BOOLEAN = "Evaluation error: Statement must evaluate to a boolean value, but was: '%s'.";
    private static final String OBLIGATIONS_ERROR = "Error occurred while evaluating obligations.";
    private static final String ADVICE_ERROR = "Error occurred while evaluating advice.";
    private static final String TRANSFORMATION_ERROR = "Error occurred while evaluating transformation.";

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private static final String PERMIT = "permit";


    /**
     * Evaluates the body of the policy within the given evaluation context and
     * returns a {@link Flux} of {@link Response} objects.
     *
     * @param ctx the evaluation context in which the policy's body is evaluated.
     *            It must contain
     *            <ul>
     *            <li>the attribute context</li>
     *            <li>the function context</li>
     *            <li>the variable context holding the four request variables 'subject',
     *                'action', 'resource' and 'environment' combined with system variables
     *                from the PDP configuration and other variables e.g. obtained from the
     *                containing policy set</li>
     *            <li>the import mapping for functions and attribute finders</li>
     *            </ul>
     * @return A {@link Flux} of {@link Response} objects.
     */
    @Override
    public Flux<Response> evaluate(EvaluationContext ctx) {
        final Decision entitlement = PERMIT.equals(getEntitlement()) ? Decision.PERMIT : Decision.DENY;
        final Flux<Decision> decisionFlux;
        if (getBody() != null) {
            decisionFlux = evaluateBody(entitlement, getBody(), ctx);
        }
        else {
            decisionFlux = Flux.just(entitlement);
        }

        return decisionFlux.flatMap(decision -> {
            if (decision == Decision.PERMIT || decision == Decision.DENY) {
                return evaluateObligationsAndAdvice(ctx)
                        .map(obligationsAndAdvice -> {
                            final Optional<ArrayNode> obligations = obligationsAndAdvice
                                    .getT1();
                            final Optional<ArrayNode> advice = obligationsAndAdvice
                                    .getT2();
                            return new Response(decision, Optional.empty(), obligations,
                                    advice);
                        });
            }
            else {
                return Flux.just(new Response(decision, Optional.empty(),
                        Optional.empty(), Optional.empty()));
            }
        }).flatMap(response -> {
            final Decision decision = response.getDecision();
            if (decision == Decision.PERMIT) {
                return evaluateTransformation(ctx)
                        .map(resource -> new Response(decision, resource,
                                response.getObligations(), response.getAdvices()));
            }
            else {
                return Flux.just(response);
            }
        }).onErrorReturn(INDETERMINATE);
    }

    private Flux<Decision> evaluateBody(Decision entitlement, PolicyBody body,
                                               EvaluationContext evaluationCtx) {
        final EList<Statement> statements = body.getStatements();
        if (statements != null && !statements.isEmpty()) {
            final boolean initialResult = true;
            final List<FluxProvider<Boolean>> fluxProviders = new ArrayList<>(
                    statements.size());
            for (Statement statement : statements) {
                fluxProviders.add(() -> evaluateStatement(statement, evaluationCtx));
            }
            return cascadingSwitchMap(initialResult, fluxProviders, 0)
                    .map(result -> result ? entitlement : Decision.NOT_APPLICABLE)
                    .onErrorResume(error -> {
                        final Throwable unwrapped = Exceptions.unwrap(error);
                        LOGGER.error("Error in policy body evaluation: {}",
                                unwrapped.getMessage());
                        return Flux.just(Decision.INDETERMINATE);
                    });
        }
        else {
            return Flux.just(entitlement);
        }
    }

    private Flux<Boolean> cascadingSwitchMap(boolean currentResult,
                List<FluxProvider<Boolean>> fluxProviders, int idx) {
        if (idx < fluxProviders.size() && currentResult) {
            return fluxProviders.get(idx).getFlux().switchMap(
                    result -> cascadingSwitchMap(result, fluxProviders, idx + 1));
        }
        return Flux.just(currentResult);
    }

    private Flux<Boolean> evaluateStatement(Statement statement, EvaluationContext evaluationCtx) {
        if (statement instanceof ValueDefinition) {
            return evaluateValueDefinition((ValueDefinition) statement, evaluationCtx);
        }
        else {
            return evaluateCondition((Condition) statement, evaluationCtx);
        }
    }

    private Flux<Boolean> evaluateValueDefinition(ValueDefinition valueDefinition, EvaluationContext evaluationCtx) {
        return valueDefinition.getEval().evaluate(evaluationCtx, true, Optional.empty())
                .map(evaluatedValue -> {
                    try {
                        if (!evaluatedValue.isPresent()) {
                            throw new PolicyEvaluationException(
                                    CANNOT_ASSIGN_UNDEFINED_TO_A_VAL);
                        }
                        evaluationCtx.getVariableCtx().put(valueDefinition.getName(),
                                evaluatedValue.get());
                        return Boolean.TRUE;
                    }
                    catch (PolicyEvaluationException e) {
                        LOGGER.error("Error in value definition evaluation: {}",
                                e.getMessage());
                        throw Exceptions.propagate(e);
                    }
                }).onErrorResume(error -> Flux.error(Exceptions.unwrap(error)));
    }

    private Flux<Boolean> evaluateCondition(Condition condition, EvaluationContext evaluationCtx) {
        return condition.getExpression().evaluate(evaluationCtx, true, Optional.empty())
                .map(statementResult -> {
                    if (statementResult.isPresent()
                            && statementResult.get().isBoolean()) {
                        return statementResult.get().asBoolean();
                    }
                    else {
                        throw Exceptions.propagate(new PolicyEvaluationException(
                                String.format(STATEMENT_NOT_BOOLEAN, statementResult)));
                    }
                }).onErrorResume(error -> Flux.error(Exceptions.unwrap(error)));
    }

    private Flux<Tuple2<Optional<ArrayNode>, Optional<ArrayNode>>> evaluateObligationsAndAdvice(
            EvaluationContext evaluationCtx) {
        Flux<Optional<ArrayNode>> obligationsFlux;
        if (getObligation() != null) {
            final ArrayNode obligationArr = JSON.arrayNode();
            obligationsFlux = getObligation().evaluate(evaluationCtx, true, Optional.empty())
                    .doOnError(error -> LOGGER.error(OBLIGATIONS_ERROR, error))
                    .map(obligation -> {
                        obligation.ifPresent(obligationArr::add);
                        return obligationArr.size() > 0 ? Optional.of(obligationArr)
                                : Optional.empty();
                    });
        }
        else {
            obligationsFlux = Flux.just(Optional.empty());
        }

        Flux<Optional<ArrayNode>> adviceFlux;
        if (getAdvice() != null) {
            final ArrayNode adviceArr = JSON.arrayNode();
            adviceFlux = getAdvice().evaluate(evaluationCtx, true, Optional.empty())
                    .doOnError(error -> LOGGER.error(ADVICE_ERROR, error)).map(advice -> {
                        advice.ifPresent(adviceArr::add);
                        return adviceArr.size() > 0 ? Optional.of(adviceArr)
                                : Optional.empty();
                    });
        }
        else {
            adviceFlux = Flux.just(Optional.empty());
        }

        return Flux.combineLatest(obligationsFlux, adviceFlux, Tuples::of);
    }

    private Flux<Optional<JsonNode>> evaluateTransformation(EvaluationContext evaluationCtx) {
        if (getTransformation() != null) {
            return getTransformation().evaluate(evaluationCtx, true, Optional.empty())
                    .doOnError(error -> LOGGER.error(TRANSFORMATION_ERROR, error));
        }
        else {
            return Flux.just(Optional.empty());
        }
    }
}
