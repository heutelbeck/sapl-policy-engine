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
import io.sapl.api.model.ObjectValue;
import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.api.pdp.PDPConfiguration;

import java.util.List;

import lombok.val;

/**
 * Jackson serializer for PDPConfiguration.
 * <p>
 * Serializes a PDPConfiguration to JSON with the following structure:
 *
 * <pre>{@code
 * {
 *   "pdpId": "production",
 *   "configurationId": "v1.0",
 *   "combiningAlgorithm": {
 *     "votingMode": "PRIORITY_DENY",
 *     "defaultDecision": "ABSTAIN",
 *     "errorHandling": "PROPAGATE"
 *   },
 *   "saplDocuments": ["policy access-control...", "policy audit-log..."],
 *   "variables": {
 *     "serverUrl": "https://api.example.com",
 *     "maxRetries": 3
 *   },
 *   "secrets": {
 *     "apiKey": "secret-value"
 *   }
 * }
 * }</pre>
 */
public class PDPConfigurationSerializer extends StdSerializer<PDPConfiguration> {

    private final ValueSerializer valueSerializer = new ValueSerializer();

    public PDPConfigurationSerializer() {
        super(PDPConfiguration.class);
    }

    @Override
    public void serialize(PDPConfiguration configuration, JsonGenerator generator, SerializationContext serializers) {
        generator.writeStartObject();

        generator.writeStringProperty("pdpId", configuration.pdpId());
        generator.writeStringProperty("configurationId", configuration.configurationId());

        generator.writeName("combiningAlgorithm");
        serializeCombiningAlgorithm(configuration.combiningAlgorithm(), generator);

        generator.writeName("saplDocuments");
        serializeStringList(configuration.saplDocuments(), generator);

        generator.writeName("variables");
        serializeValueMap(configuration.data().variables(), generator, serializers);

        generator.writeName("secrets");
        serializeValueMap(configuration.data().secrets(), generator, serializers);

        generator.writeEndObject();
    }

    private void serializeCombiningAlgorithm(CombiningAlgorithm algorithm, JsonGenerator generator) {
        generator.writeStartObject();
        generator.writeStringProperty("votingMode", algorithm.votingMode().name());
        generator.writeStringProperty("defaultDecision", algorithm.defaultDecision().name());
        generator.writeStringProperty("errorHandling", algorithm.errorHandling().name());
        generator.writeEndObject();
    }

    private void serializeStringList(List<String> strings, JsonGenerator generator) {
        generator.writeStartArray();
        for (String string : strings) {
            generator.writeString(string);
        }
        generator.writeEndArray();
    }

    private void serializeValueMap(ObjectValue map, JsonGenerator generator, SerializationContext serializers) {
        generator.writeStartObject();
        for (val entry : map.entrySet()) {
            generator.writeName(entry.getKey());
            valueSerializer.serialize(entry.getValue(), generator, serializers);
        }
        generator.writeEndObject();
    }
}
