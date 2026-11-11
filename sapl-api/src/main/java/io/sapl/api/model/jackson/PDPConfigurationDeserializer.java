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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.api.pdp.configuration.PdpData;
import io.sapl.api.pdp.configuration.UnknownProvenance;

import lombok.val;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

/**
 * Jackson deserializer for PDPConfiguration.
 * <p>
 * Deserializes JSON to PDPConfiguration. The combining algorithm is
 * deserialized as a structured object with votingMode, defaultDecision, and
 * errorHandling fields via {@link CombiningAlgorithmDeserializer}.
 * <p>
 * Required fields:
 * <ul>
 * <li>pdpId - the PDP identifier</li>
 * <li>configurationId - the configuration version/identifier</li>
 * <li>combiningAlgorithm - the policy combining algorithm</li>
 * </ul>
 * Optional fields:
 * <ul>
 * <li>compilerOptions: compiler tuning (indexing, unrollInOperator, etc.).
 * Defaults to an empty ObjectValue</li>
 * <li>saplDocuments: policy sources, defaulting to an empty list</li>
 * <li>variables, secrets, extensions and extensionSecrets: value maps,
 * defaulting to empty objects</li>
 * <li>requiredExtensions: required extension names, defaulting to an empty set</li>
 * <li>requiredEngineVersion: optional engine version range. Defaults to null</li>
 * </ul>
 */
public class PDPConfigurationDeserializer extends StdDeserializer<PDPConfiguration> {

    private static final String ERROR_COMBINING_ALGORITHM_REQUIRED   = "PDPConfiguration requires combiningAlgorithm field.";
    private static final String ERROR_CONFIGURATION_ID_REQUIRED      = "PDPConfiguration requires configurationId field.";
    private static final String ERROR_EXPECTED_ENGINE_VERSION_STRING = "requiredEngineVersion must be a string or null.";
    private static final String ERROR_EXPECTED_START_ARRAY           = "Field '%s' must be an array of strings.";
    private static final String ERROR_EXPECTED_START_OBJECT          = "Expected START_OBJECT for PDPConfiguration.";
    private static final String ERROR_EXPECTED_START_OBJECT_MAP      = "Expected START_OBJECT for value map.";
    private static final String ERROR_EXPECTED_STRING_ELEMENT        = "Field '%s' must contain only strings.";
    private static final String ERROR_PDP_ID_REQUIRED                = "PDPConfiguration requires pdpId field.";
    private static final String ERROR_UNKNOWN_FIELD                  = "Unknown PDPConfiguration field '%s'.";

    private static final Set<String> OBJECT_FIELDS = Set.of("compilerOptions", "extensionSecrets", "extensions",
            "secrets", "variables");

    private final ValueDeserializer              valueDeserializer              = new ValueDeserializer();
    private final CombiningAlgorithmDeserializer combiningAlgorithmDeserializer = new CombiningAlgorithmDeserializer();

    /** Default constructor required by Jackson 3. */
    public PDPConfigurationDeserializer() {
        super(PDPConfiguration.class);
    }

    @Override
    public PDPConfiguration deserialize(JsonParser parser, DeserializationContext context) {
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            return context.reportInputMismatch(PDPConfiguration.class, ERROR_EXPECTED_START_OBJECT);
        }

        String             pdpId                 = null;
        String             configurationId       = null;
        String             requiredEngineVersion = null;
        CombiningAlgorithm combiningAlgorithm    = null;
        List<String>       saplDocuments         = List.of();
        Set<String>        requiredExtensions    = Set.of();
        val                objectFields          = new HashMap<String, ObjectValue>();

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            val fieldName = parser.currentName();
            parser.nextToken();

            if (OBJECT_FIELDS.contains(fieldName)) {
                objectFields.put(fieldName, deserializeObjectValue(parser, context));
                continue;
            }

            switch (fieldName) {
            case "pdpId"                 -> pdpId = parser.getString();
            case "configurationId"       -> configurationId = parser.getString();
            case "requiredEngineVersion" -> requiredEngineVersion = deserializeEngineRequirement(parser, context);
            case "combiningAlgorithm"    ->
                combiningAlgorithm = combiningAlgorithmDeserializer.deserialize(parser, context);
            case "saplDocuments"         -> saplDocuments = deserializeStringList(parser, context, fieldName);
            case "requiredExtensions"    ->
                requiredExtensions = Set.copyOf(deserializeStringList(parser, context, fieldName));
            default                      -> {
                return context.reportInputMismatch(PDPConfiguration.class, ERROR_UNKNOWN_FIELD.formatted(fieldName));
            }
            }
        }

        if (pdpId == null) {
            return context.reportInputMismatch(PDPConfiguration.class, ERROR_PDP_ID_REQUIRED);
        }
        if (configurationId == null) {
            return context.reportInputMismatch(PDPConfiguration.class, ERROR_CONFIGURATION_ID_REQUIRED);
        }
        if (combiningAlgorithm == null) {
            return context.reportInputMismatch(PDPConfiguration.class, ERROR_COMBINING_ALGORITHM_REQUIRED);
        }

        return new PDPConfiguration(pdpId, configurationId, combiningAlgorithm,
                objectFields.getOrDefault("compilerOptions", Value.EMPTY_OBJECT), saplDocuments,
                new PdpData(objectFields.getOrDefault("variables", Value.EMPTY_OBJECT),
                        objectFields.getOrDefault("secrets", Value.EMPTY_OBJECT)),
                objectFields.getOrDefault("extensions", Value.EMPTY_OBJECT),
                objectFields.getOrDefault("extensionSecrets", Value.EMPTY_OBJECT), requiredExtensions,
                UnknownProvenance.INSTANCE, requiredEngineVersion);
    }

    private static @Nullable String deserializeEngineRequirement(JsonParser parser, DeserializationContext context) {
        if (parser.currentToken() == JsonToken.VALUE_NULL) {
            return null;
        }
        if (parser.currentToken() != JsonToken.VALUE_STRING) {
            return context.reportInputMismatch(PDPConfiguration.class, ERROR_EXPECTED_ENGINE_VERSION_STRING);
        }
        return parser.getString();
    }

    private static List<String> deserializeStringList(JsonParser parser, DeserializationContext context,
            String fieldName) {
        if (parser.currentToken() != JsonToken.START_ARRAY) {
            return context.reportInputMismatch(List.class, ERROR_EXPECTED_START_ARRAY.formatted(fieldName));
        }

        val strings = new ArrayList<String>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (parser.currentToken() != JsonToken.VALUE_STRING) {
                return context.reportInputMismatch(List.class, ERROR_EXPECTED_STRING_ELEMENT.formatted(fieldName));
            }
            strings.add(parser.getString());
        }
        return strings;
    }

    private ObjectValue deserializeObjectValue(JsonParser parser, DeserializationContext context) {
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            return context.reportInputMismatch(ObjectValue.class, ERROR_EXPECTED_START_OBJECT_MAP);
        }

        val map = new HashMap<String, Value>();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            val fieldName = parser.currentName();
            parser.nextToken();
            map.put(fieldName, valueDeserializer.deserialize(parser, context));
        }
        return Value.ofObject(map);
    }
}
