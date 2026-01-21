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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.api.pdp.CombiningAlgorithm.DefaultDecision;
import io.sapl.api.pdp.CombiningAlgorithm.ErrorHandling;
import io.sapl.api.pdp.CombiningAlgorithm.VotingMode;

import java.io.IOException;

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
public class CombiningAlgorithmDeserializer extends JsonDeserializer<CombiningAlgorithm> {

    @Override
    public CombiningAlgorithm deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            throw new IOException("Expected START_OBJECT for CombiningAlgorithm.");
        }

        VotingMode      votingMode      = null;
        DefaultDecision defaultDecision = null;
        ErrorHandling   errorHandling   = null;

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            var fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
            case "votingMode"      -> votingMode = VotingMode.valueOf(parser.getText());
            case "defaultDecision" -> defaultDecision = DefaultDecision.valueOf(parser.getText());
            case "errorHandling"   -> errorHandling = ErrorHandling.valueOf(parser.getText());
            default                -> { /* ignore unknown fields */ }
            }
        }

        if (votingMode == null) {
            throw new IOException("CombiningAlgorithm requires votingMode field.");
        }
        if (defaultDecision == null) {
            throw new IOException("CombiningAlgorithm requires defaultDecision field.");
        }
        if (errorHandling == null) {
            throw new IOException("CombiningAlgorithm requires errorHandling field.");
        }

        return new CombiningAlgorithm(votingMode, defaultDecision, errorHandling);
    }
}
