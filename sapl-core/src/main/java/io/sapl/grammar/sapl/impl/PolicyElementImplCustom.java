package io.sapl.grammar.sapl.impl;

import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.pdp.Request;
import io.sapl.grammar.sapl.Expression;
import io.sapl.interpreter.EvaluationContext;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Exceptions;

@Slf4j
public class PolicyElementImplCustom extends PolicyElementImpl {

    private static final String CONDITION_NOT_BOOLEAN = "Evaluation error: Target condition must evaluate to a boolean value, but was: '%s'.";

    @Override
    public boolean matches(Request request, EvaluationContext ctx) throws PolicyEvaluationException {
        LOGGER.trace("| | |-- PolicyElement test match '{}' with {}", getSaplName(), request);
        final Expression targetExpression = getTargetExpression();
        if (targetExpression == null) {
            LOGGER.trace("| | | |-- MATCH (no target expression, matches all)");
            LOGGER.trace("| | |");
            return true;
        }
        else {
            try {
                final Optional<JsonNode> expressionResult = targetExpression
                        .evaluate(ctx, false, Optional.empty()).blockFirst();
                if (expressionResult.isPresent() && expressionResult.get().isBoolean()) {
                    LOGGER.trace("| | | |-- {}",
                            expressionResult.get().asBoolean() ? "MATCH" : "NO MATCH");
                    LOGGER.trace("| | |");
                    return expressionResult.get().asBoolean();
                }
                else {
                    LOGGER.trace(
                            "| | | |-- ERROR in target expression did not evaluate to boolean. Was: {}",
                            expressionResult);
                    LOGGER.trace("| | |");
                    throw new PolicyEvaluationException(
                            String.format(CONDITION_NOT_BOOLEAN, expressionResult));
                }
            }
            catch (RuntimeException fluxError) {
                LOGGER.trace("| | | |-- ERROR during target expression evaluation: {} ",
                        fluxError.getMessage());
                LOGGER.trace("| | | |-- trace: ", fluxError);
                LOGGER.trace("| | |");

                final Throwable originalError = Exceptions.unwrap(fluxError);
                if (originalError instanceof PolicyEvaluationException) {
                    throw (PolicyEvaluationException) originalError;
                }
                throw fluxError;
            }
        }
    }
}
