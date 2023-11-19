package io.sapl.springdatamongoreactive.sapl.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.sapl.api.pdp.AuthorizationDecision;
import lombok.experimental.UtilityClass;

import java.util.Objects;

import static io.sapl.springdatamongoreactive.sapl.utils.Utilities.TYPE;

/**
 * This utility class provides various interactions with
 * {@link AuthorizationDecision}.
 */
@UtilityClass
public class ConstraintHandlerUtils {

    /**
     * Retrieves a specific ConstraintHandler from the Obligations using the type.
     *
     * @param obligations    are all obligations of the
     *                       {@link io.sapl.api.pdp.Decision}.
     * @param constraintType is the name of the constraint handler.
     * @return the searched constraint handler.
     */
    public static JsonNode getConstraintHandlerByTypeIfResponsible(JsonNode obligations, String constraintType) {
        for (JsonNode obligation : obligations) {
            if (obligation != null && obligation.isObject()) {
                var type = obligation.get(TYPE);
                if (!Objects.isNull(type) && type.isTextual() && constraintType.equals(type.asText())) {
                    return obligation;
                }
            }
        }
        return JsonNodeFactory.instance.nullNode();
    }

    /**
     * Fetches all obligations from an {@link AuthorizationDecision}.
     *
     * @param decision is the {@link AuthorizationDecision}
     * @return all obligations of the {@link AuthorizationDecision}.
     */
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public static JsonNode getObligations(AuthorizationDecision decision) {
        return isResponsible(decision) ? decision.getObligations().get() : JsonNodeFactory.instance.nullNode();
    }

    /**
     * Fetches all advices from an {@link AuthorizationDecision}.
     *
     * @param decision is the {@link AuthorizationDecision}
     * @return all advices of the {@link AuthorizationDecision}.
     */
    public static JsonNode getAdvices(AuthorizationDecision decision) {
        return decision.getAdvice().isPresent() ? decision.getAdvice().get() : JsonNodeFactory.instance.nullNode();
    }

    private static boolean isResponsible(AuthorizationDecision decision) {
        return decision.getObligations().isPresent() && !decision.getObligations().get().isNull();
    }
}
