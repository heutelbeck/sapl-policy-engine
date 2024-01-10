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
package io.sapl.springdatacommon.sapl.utils;

import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.AuthorizationDecision;
import lombok.experimental.UtilityClass;

/**
 * This utility class provides various interactions with
 * {@link AuthorizationDecision}.
 */
@UtilityClass
public class ConstraintHandlerUtils {

    private static final ObjectMapper MAPPER           = new ObjectMapper();
    private static final ArrayNode    EMPTY_ARRAY_NODE = MAPPER.createArrayNode();

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
                var type = obligation.get(Utilities.TYPE);
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
    public static ArrayNode getObligations(AuthorizationDecision decision) {
        var possibleObligations = decision.getObligations();
        if (possibleObligations.isEmpty()) {
            return EMPTY_ARRAY_NODE;
        }
        return possibleObligations.get();
    }

    /**
     * Fetches all advice from an {@link AuthorizationDecision}.
     *
     * @param decision is the {@link AuthorizationDecision}
     * @return all advice of the {@link AuthorizationDecision}.
     */
    public static ArrayNode getAdvice(AuthorizationDecision decision) {
        var advice = decision.getAdvice();
        return advice.isPresent() ? advice.get() : EMPTY_ARRAY_NODE;
    }
}
