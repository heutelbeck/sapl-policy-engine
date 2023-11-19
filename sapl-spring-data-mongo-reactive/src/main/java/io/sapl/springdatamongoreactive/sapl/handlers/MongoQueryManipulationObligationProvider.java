package io.sapl.springdatamongoreactive.sapl.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.util.Objects;

import static io.sapl.springdatamongoreactive.sapl.utils.Utilities.*;

/**
 * This class takes care of extracting the correct obligation for manipulating
 * the query from a mongodb.
 */
public class MongoQueryManipulationObligationProvider {

    /**
     * Extracts the query conditions of an obligation to apply the
     * MongoQueryManipulation.
     *
     * @param obligation which contains query conditions.
     * @return all query conditions.
     */
    public JsonNode getConditions(JsonNode obligation) {
        if (obligation.has(CONDITIONS) && obligation.get(CONDITIONS).isArray() && !obligation.get(CONDITIONS).isNull()
                && !obligation.get(CONDITIONS).isEmpty()) {
            return obligation.get(CONDITIONS);
        }
        return JsonNodeFactory.instance.nullNode();
    }

    /**
     * Extracts the correct obligation from all obligations to apply the
     * MongoQueryManipulation.
     *
     * @param obligations which contains all obligations.
     * @return correct obligation.
     */
    public JsonNode getObligation(JsonNode obligations) {
        for (JsonNode obligation : obligations) {
            if (obligation != null && obligation.isObject()) {
                JsonNode type = obligation.get(TYPE);
                if (!Objects.isNull(type) && type.isTextual() && MONGO_QUERY_MANIPULATION.equals(type.asText())) {
                    return obligation;
                }
            }
        }
        return JsonNodeFactory.instance.nullNode();
    }

    /**
     * Checks if an obligation of a {@link io.sapl.api.pdp.Decision} is responsible
     * and can be applied.
     *
     * @param obligations are the obligations of a {@link io.sapl.api.pdp.Decision}
     * @return true if an obligation can be applied.
     */
    public boolean isResponsible(JsonNode obligations) {
        for (JsonNode obligation : obligations) {
            if (obligation != null && obligation.isObject()) {
                JsonNode type = obligation.get(TYPE);
                if (!Objects.isNull(type) && type.isTextual() && MONGO_QUERY_MANIPULATION.equals(type.asText())) {
                    return true;
                }
            }
        }
        return false;
    }
}
