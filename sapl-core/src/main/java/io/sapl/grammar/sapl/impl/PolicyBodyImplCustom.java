package io.sapl.grammar.sapl.impl;


import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.emf.common.util.EList;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.Response;
import io.sapl.grammar.sapl.Condition;
import io.sapl.grammar.sapl.Statement;
import io.sapl.grammar.sapl.ValueDefinition;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.FluxProvider;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
public class PolicyBodyImplCustom extends PolicyBodyImpl {

    private static final String STATEMENT_NOT_BOOLEAN = "Evaluation error: Statement must evaluate to a boolean value, but was: '%s'.";
    protected static final String CANNOT_ASSIGN_UNDEFINED_TO_A_VAL = "Cannot assign undefined to a val.";


    /**
     * Evaluates all statements of this policy body within the given evaluation context and
     * returns a {@link Flux} of {@link Decision} objects.
     *
     * @param entitlement the entitlement of the enclosing policy.
     * @param ctx the evaluation context in which the statements are evaluated.
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
    public Flux<Decision> evaluate(Decision entitlement, EvaluationContext ctx) {
        final EList<Statement> statements = getStatements();
        if (statements != null && !statements.isEmpty()) {
            final List<FluxProvider<Boolean>> fluxProviders = new ArrayList<>(
                    statements.size());
            for (Statement statement : statements) {
                fluxProviders.add(currentResult -> evaluateStatement(statement, ctx));
            }
            return sequentialSwitchMap(Boolean.TRUE, fluxProviders)
            //return nestedSwitchMap(Boolean.TRUE, fluxProviders)
                    .map(result -> result ? entitlement : Decision.NOT_APPLICABLE)
                    .onErrorResume(error -> {
                        LOGGER.error("Error in policy body evaluation: {}",
                                error.getMessage());
                        return Flux.just(Decision.INDETERMINATE);
                    });
        }
        else {
            return Flux.just(entitlement);
        }
    }

    private Flux<Boolean> sequentialSwitchMap(Boolean input, List<FluxProvider<Boolean>> fluxProviders) {
        Flux<Boolean> flux = fluxProviders.isEmpty()
                ? Flux.just(input)
                : fluxProviders.get(0).getFlux(input);
        for (int i = 1; i < fluxProviders.size(); i++) {
            final int idx = i;
            flux = flux.switchMap(currentResult -> currentResult
                    ? fluxProviders.get(idx).getFlux(currentResult)
                    : Flux.just(Boolean.FALSE));
        }
        return flux;
    }

    private Flux<Boolean> nestedSwitchMap(Boolean currentResult,
                                             List<FluxProvider<Boolean>> fluxProviders, int idx) {
        if (idx < fluxProviders.size() && currentResult) {
            return fluxProviders.get(idx).getFlux(currentResult).switchMap(
                    result -> nestedSwitchMap(result, fluxProviders, idx + 1));
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
                .flatMap(evaluatedValue -> {
                    try {
                        if (evaluatedValue.isPresent()) {
                            evaluationCtx.getVariableCtx().put(valueDefinition.getName(),
                                    evaluatedValue.get());
                            return Flux.just(Boolean.TRUE);
                        }
                        else {
                            return Flux.error(new PolicyEvaluationException(
                                    CANNOT_ASSIGN_UNDEFINED_TO_A_VAL));
                        }
                    }
                    catch (PolicyEvaluationException e) {
                        LOGGER.error("Error in value definition evaluation: {}", e.getMessage());
                        return Flux.error(e);
                    }
                });
    }

    private Flux<Boolean> evaluateCondition(Condition condition, EvaluationContext evaluationCtx) {
        return condition.getExpression().evaluate(evaluationCtx, true, Optional.empty())
                .flatMap(statementResult -> {
                    if (statementResult.isPresent()
                            && statementResult.get().isBoolean()) {
                        return Flux.just(statementResult.get().asBoolean());
                    }
                    else {
                        return Flux.error(new PolicyEvaluationException(
                                String.format(STATEMENT_NOT_BOOLEAN, statementResult)));
                    }
                });
    }
}
