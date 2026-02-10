/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.api.model.jackson;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;
import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.api.pdp.CombiningAlgorithm.DefaultDecision;
import io.sapl.api.pdp.CombiningAlgorithm.ErrorHandling;
import io.sapl.api.pdp.CombiningAlgorithm.VotingMode;

import lombok.val;

/**
 * Deserializer for {@link CombiningAlgorithm} that parses the record from JSON
 * object format.
 * <p>
 * Expected format:
 *
 * <pre>
 * {
 *   "votingMode": "PRIORITY_DENY",
 *   "defaultDecision": "ABSTAIN",
 *   "errorHandling": "PROPAGATE"
 * }
 * </pre>
 */
public class CombiningAlgorithmDeserializer extends StdDeserializer<CombiningAlgorithm> {

    /**
     * Default constructor required by Jackson 3.
     */
    public CombiningAlgorithmDeserializer() {
        super(CombiningAlgorithm.class);
    }

    private static final String ERROR_DEFAULT_DECISION_REQUIRED = "CombiningAlgorithm requires defaultDecision field.";
    private static final String ERROR_ERROR_HANDLING_REQUIRED   = "CombiningAlgorithm requires errorHandling field.";
    private static final String ERROR_EXPECTED_START_OBJECT     = "Expected START_OBJECT for CombiningAlgorithm.";
    private static final String ERROR_FIRST_NOT_ALLOWED         = "FIRST is not allowed as combining algorithm voting mode at PDP level. It implies an ordering that is not present here.";
    private static final String ERROR_VOTING_MODE_REQUIRED      = "CombiningAlgorithm requires votingMode field.";

    @Override
    public CombiningAlgorithm deserialize(JsonParser parser, DeserializationContext context) {
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            return context.reportInputMismatch(CombiningAlgorithm.class, ERROR_EXPECTED_START_OBJECT);
        }

        VotingMode      votingMode      = null;
        DefaultDecision defaultDecision = null;
        ErrorHandling   errorHandling   = null;

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            val fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
            case "votingMode"      -> votingMode = VotingMode.valueOf(parser.getString());
            case "defaultDecision" -> defaultDecision = DefaultDecision.valueOf(parser.getString());
            case "errorHandling"   -> errorHandling = ErrorHandling.valueOf(parser.getString());
            default                -> { /* ignore unknown fields */ }
            }
        }

        if (votingMode == null) {
            return context.reportInputMismatch(CombiningAlgorithm.class, ERROR_VOTING_MODE_REQUIRED);
        }
        if (votingMode == VotingMode.FIRST) {
            return context.reportInputMismatch(CombiningAlgorithm.class, ERROR_FIRST_NOT_ALLOWED);
        }
        if (defaultDecision == null) {
            return context.reportInputMismatch(CombiningAlgorithm.class, ERROR_DEFAULT_DECISION_REQUIRED);
        }
        if (errorHandling == null) {
            return context.reportInputMismatch(CombiningAlgorithm.class, ERROR_ERROR_HANDLING_REQUIRED);
        }

        return new CombiningAlgorithm(votingMode, defaultDecision, errorHandling);
    }
}
