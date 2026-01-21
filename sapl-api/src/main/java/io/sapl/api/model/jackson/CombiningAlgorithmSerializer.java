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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import io.sapl.api.pdp.CombiningAlgorithm;

import java.io.IOException;

/**
 * Serializer for {@link CombiningAlgorithm} that outputs JSON object format.
 * <p>
 * Output format:
 *
 * <pre>
 * {
 *   "votingMode": "PRIORITY_DENY",
 *   "defaultDecision": "ABSTAIN",
 *   "errorHandling": "PROPAGATE"
 * }
 * </pre>
 */
public class CombiningAlgorithmSerializer extends JsonSerializer<CombiningAlgorithm> {

    @Override
    public void serialize(CombiningAlgorithm algorithm, JsonGenerator generator, SerializerProvider serializers)
            throws IOException {
        generator.writeStartObject();
        generator.writeStringField("votingMode", algorithm.votingMode().name());
        generator.writeStringField("defaultDecision", algorithm.defaultDecision().name());
        generator.writeStringField("errorHandling", algorithm.errorHandling().name());
        generator.writeEndObject();
    }
}
