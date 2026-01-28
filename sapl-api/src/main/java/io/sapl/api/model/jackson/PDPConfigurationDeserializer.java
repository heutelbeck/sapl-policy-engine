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
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.api.pdp.PDPConfiguration;
import io.sapl.api.pdp.PdpData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import lombok.val;

/**
 * Jackson deserializer for PDPConfiguration.
 * <p>
 * Deserializes JSON to PDPConfiguration. The combining algorithm supports both
 * uppercase with underscores
 * (DENY_OVERRIDES) and kebab-case (deny-overrides) formats for flexibility.
 * <p>
 * Required fields:
 * <ul>
 * <li>pdpId - the PDP identifier</li>
 * <li>configurationId - the configuration version/identifier</li>
 * <li>combiningAlgorithm - the policy combining algorithm</li>
 * <li>saplDocuments - array of SAPL document strings</li>
 * <li>variables - object mapping variable names to Values</li>
 * <li>secrets - object mapping secret names to Values</li>
 * </ul>
 */
public class PDPConfigurationDeserializer extends StdDeserializer<PDPConfiguration> {

    /**
     * Default constructor required by Jackson 3.
     */
    public PDPConfigurationDeserializer() {
        super(PDPConfiguration.class);
    }

    private static final String ERROR_COMBINING_ALGORITHM_REQUIRED = "PDPConfiguration requires combiningAlgorithm field.";
    private static final String ERROR_CONFIGURATION_ID_REQUIRED    = "PDPConfiguration requires configurationId field.";
    private static final String ERROR_EXPECTED_START_ARRAY         = "Expected START_ARRAY for saplDocuments.";
    private static final String ERROR_EXPECTED_START_OBJECT        = "Expected START_OBJECT for PDPConfiguration.";
    private static final String ERROR_EXPECTED_START_OBJECT_MAP    = "Expected START_OBJECT for value map.";
    private static final String ERROR_PDP_ID_REQUIRED              = "PDPConfiguration requires pdpId field.";

    private final ValueDeserializer              valueDeserializer              = new ValueDeserializer();
    private final CombiningAlgorithmDeserializer combiningAlgorithmDeserializer = new CombiningAlgorithmDeserializer();

    @Override
    public PDPConfiguration deserialize(JsonParser parser, DeserializationContext context) {
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            context.reportInputMismatch(PDPConfiguration.class, ERROR_EXPECTED_START_OBJECT);
        }

        String             pdpId              = null;
        String             configurationId    = null;
        CombiningAlgorithm combiningAlgorithm = null;
        List<String>       saplDocuments      = List.of();
        ObjectValue        variables          = Value.EMPTY_OBJECT;
        ObjectValue        secrets            = Value.EMPTY_OBJECT;

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            val fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
            case "pdpId"              -> pdpId = parser.getString();
            case "configurationId"    -> configurationId = parser.getString();
            case "combiningAlgorithm" ->
                combiningAlgorithm = combiningAlgorithmDeserializer.deserialize(parser, context);
            case "saplDocuments"      -> saplDocuments = deserializeStringList(parser, context);
            case "variables"          -> variables = deserializeObjectValue(parser, context);
            case "secrets"            -> secrets = deserializeObjectValue(parser, context);
            default                   -> parser.skipChildren();
            }
        }

        if (pdpId == null) {
            context.reportInputMismatch(PDPConfiguration.class, ERROR_PDP_ID_REQUIRED);
        }
        if (configurationId == null) {
            context.reportInputMismatch(PDPConfiguration.class, ERROR_CONFIGURATION_ID_REQUIRED);
        }
        if (combiningAlgorithm == null) {
            context.reportInputMismatch(PDPConfiguration.class, ERROR_COMBINING_ALGORITHM_REQUIRED);
        }

        return new PDPConfiguration(pdpId, configurationId, combiningAlgorithm, saplDocuments,
                new PdpData(variables, secrets));
    }

    private List<String> deserializeStringList(JsonParser parser, DeserializationContext context) {
        if (parser.currentToken() != JsonToken.START_ARRAY) {
            context.reportInputMismatch(List.class, ERROR_EXPECTED_START_ARRAY);
        }

        val strings = new ArrayList<String>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            strings.add(parser.getString());
        }
        return strings;
    }

    private ObjectValue deserializeObjectValue(JsonParser parser, DeserializationContext context) {
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            context.reportInputMismatch(ObjectValue.class, ERROR_EXPECTED_START_OBJECT_MAP);
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
