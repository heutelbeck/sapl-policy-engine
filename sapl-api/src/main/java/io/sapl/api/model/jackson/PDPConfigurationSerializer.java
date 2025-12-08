/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import io.sapl.api.model.Value;
import io.sapl.api.pdp.PDPConfiguration;

import java.io.IOException;
import java.util.Map;

/**
 * Jackson serializer for PDPConfiguration.
 * <p>
 * Serializes a PDPConfiguration to JSON with the following structure:
 *
 * <pre>{@code
 * {
 *   "pdpId": "production",
 *   "configurationId": "v1.0",
 *   "combiningAlgorithm": "DENY_OVERRIDES",
 *   "saplDocuments": ["policy access-control...", "policy audit-log..."],
 *   "variables": {
 *     "serverUrl": "https://api.example.com",
 *     "maxRetries": 3
 *   }
 * }
 * }</pre>
 * <p>
 * The combiningAlgorithm is serialized using its enum name (uppercase with
 * underscores). The variables map is
 * serialized as a JSON object with Value serialization for each entry.
 */
public class PDPConfigurationSerializer extends JsonSerializer<PDPConfiguration> {

    private final ValueSerializer valueSerializer = new ValueSerializer();

    @Override
    public void serialize(PDPConfiguration configuration, JsonGenerator generator, SerializerProvider serializers)
            throws IOException {
        generator.writeStartObject();

        generator.writeStringField("pdpId", configuration.pdpId());
        generator.writeStringField("configurationId", configuration.configurationId());
        generator.writeStringField("combiningAlgorithm", configuration.combiningAlgorithm().name());

        generator.writeFieldName("saplDocuments");
        serializeStringList(configuration.saplDocuments(), generator);

        generator.writeFieldName("variables");
        serializeVariablesMap(configuration.variables(), generator, serializers);

        generator.writeEndObject();
    }

    private void serializeStringList(java.util.List<String> strings, JsonGenerator generator) throws IOException {
        generator.writeStartArray();
        for (String string : strings) {
            generator.writeString(string);
        }
        generator.writeEndArray();
    }

    private void serializeVariablesMap(Map<String, Value> variables, JsonGenerator generator,
            SerializerProvider serializers) throws IOException {
        generator.writeStartObject();
        for (var entry : variables.entrySet()) {
            generator.writeFieldName(entry.getKey());
            valueSerializer.serialize(entry.getValue(), generator, serializers);
        }
        generator.writeEndObject();
    }
}
