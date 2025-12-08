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
package io.sapl.grammar.ide.contentassist;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.attributes.AttributeBroker;
import io.sapl.api.documentation.DocumentationBundle;
import io.sapl.api.documentation.EntryType;
import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.model.Value;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration data for content assist in the SAPL IDE.
 *
 * @param pdpId
 * unique identifier for the PDP instance
 * @param configurationId
 * identifier for this specific configuration
 * @param variables
 * environment variables available during policy evaluation
 * @param documentationBundle
 * documentation for all registered function libraries and PIPs
 * @param functionBroker
 * broker for function evaluation during expression analysis
 * @param attributeBroker
 * broker for attribute resolution during expression analysis
 */
public record ContentAssistPDPConfiguration(
        String pdpId,
        String configurationId,
        Map<String, Value> variables,
        DocumentationBundle documentationBundle,
        FunctionBroker functionBroker,
        AttributeBroker attributeBroker) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Builds a map from function code templates to their return type schemas.
     *
     * @return map of code template to schema, or empty map if no schemas defined
     */
    public Map<String, JsonNode> getFunctionSchemas() {
        var schemas = new HashMap<String, JsonNode>();
        for (var library : documentationBundle.functionLibraries()) {
            for (var entry : library.entries()) {
                var schemaString = entry.schema();
                if (schemaString != null && !schemaString.isBlank()) {
                    var jsonSchema = parseSchema(schemaString);
                    if (jsonSchema != null) {
                        schemas.put(entry.codeTemplate(library.name()), jsonSchema);
                    }
                }
            }
        }
        return schemas;
    }

    /**
     * Builds a map from attribute code templates to their return type schemas.
     *
     * @return map of code template to schema, or empty map if no schemas defined
     */
    public Map<String, JsonNode> getAttributeSchemas() {
        var schemas = new HashMap<String, JsonNode>();
        for (var pip : documentationBundle.policyInformationPoints()) {
            for (var entry : pip.entries()) {
                if (entry.type() == EntryType.ATTRIBUTE || entry.type() == EntryType.ENVIRONMENT_ATTRIBUTE) {
                    var schemaString = entry.schema();
                    if (schemaString != null && !schemaString.isBlank()) {
                        var jsonSchema = parseSchema(schemaString);
                        if (jsonSchema != null) {
                            schemas.put(entry.codeTemplate(pip.name()), jsonSchema);
                        }
                    }
                }
            }
        }
        return schemas;
    }

    private JsonNode parseSchema(String schemaString) {
        try {
            return MAPPER.readTree(schemaString);
        } catch (Exception e) {
            return null;
        }
    }
}
