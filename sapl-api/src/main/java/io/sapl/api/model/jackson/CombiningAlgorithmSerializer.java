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

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;
import io.sapl.api.pdp.CombiningAlgorithm;

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
public class CombiningAlgorithmSerializer extends StdSerializer<CombiningAlgorithm> {

    public CombiningAlgorithmSerializer() {
        super(CombiningAlgorithm.class);
    }

    @Override
    public void serialize(CombiningAlgorithm algorithm, JsonGenerator generator, SerializationContext serializers) {
        generator.writeStartObject();
        generator.writeStringProperty("votingMode", algorithm.votingMode().name());
        generator.writeStringProperty("defaultDecision", algorithm.defaultDecision().name());
        generator.writeStringProperty("errorHandling", algorithm.errorHandling().name());
        generator.writeEndObject();
    }
}
