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
package io.sapl.springdatacommon.handlers;

import static io.sapl.springdatacommon.sapl.utils.Utilities.CONDITIONS;
import static io.sapl.springdatacommon.sapl.utils.Utilities.TYPE;

import java.util.Objects;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

/**
 * This class takes care of extracting the correct obligation for manipulating
 * the query.
 */
public class QueryManipulationObligationProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Extracts the query CONDITION of an obligation to apply the the corresponding
     * QueryManipulation.
     *
     * @param obligation which contains query CONDITIONS.
     * @return all query CONDITIONS.
     */
    public ArrayNode getConditions(JsonNode obligation) {
        if (obligation.has(CONDITIONS) && obligation.get(CONDITIONS).isArray() && !obligation.get(CONDITIONS).isNull()
                && !obligation.get(CONDITIONS).isEmpty()) {
            return (ArrayNode) obligation.get(CONDITIONS);
        }
        return MAPPER.createArrayNode();
    }

    /**
     * Extracts the correct obligation from all obligations to apply the the
     * corresponding QueryManipulation.
     *
     * @param obligations which contains all obligations.
     * @return correct obligation.
     */
    public JsonNode getObligation(Iterable<JsonNode> obligations, String queryType) {
        var iterator = obligations.iterator();
        while (iterator.hasNext()) {
            var obligation = iterator.next();
            if (obligation != null && obligation.isObject()) {
                var type = obligation.get(TYPE);
                if (!Objects.isNull(type) && type.isTextual() && queryType.equals(type.asText())) {
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
    public boolean isResponsible(ArrayNode obligations, String queryType) {
        var iterator = obligations.iterator();
        while (iterator.hasNext()) {
            var obligation = iterator.next();
            if (obligation != null && obligation.isObject()) {
                var type = obligation.get(TYPE);
                if (!Objects.isNull(type) && type.isTextual() && queryType.equals(type.asText())) {
                    return true;
                }
            }
        }
        return false;
    }
}
