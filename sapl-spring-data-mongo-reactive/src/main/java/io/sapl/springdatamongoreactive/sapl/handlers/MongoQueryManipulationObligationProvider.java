/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.springdatamongoreactive.sapl.handlers;

import static io.sapl.springdatamongoreactive.sapl.utils.Utilities.CONDITIONS;
import static io.sapl.springdatamongoreactive.sapl.utils.Utilities.MONGO_QUERY_MANIPULATION;
import static io.sapl.springdatamongoreactive.sapl.utils.Utilities.TYPE;

import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

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
