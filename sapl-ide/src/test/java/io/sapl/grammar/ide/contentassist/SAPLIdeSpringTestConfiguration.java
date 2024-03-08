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
package io.sapl.grammar.ide.contentassist;

import static org.mockito.Mockito.mock;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.CombiningAlgorithm;
import io.sapl.pdp.config.PDPConfiguration;
import io.sapl.pdp.config.PDPConfigurationProvider;
import io.sapl.prp.PolicyRetrievalPoint;
import lombok.SneakyThrows;
import reactor.core.publisher.Flux;

@ComponentScan
@Configuration
class SAPLIdeSpringTestConfiguration {
    private final static ObjectMapper MAPPER = new ObjectMapper();

    @Bean
    PDPConfigurationProvider pdpConfiguration() {
        var attributeContext = new TestAttributeContext();
        var functionContext  = new TestFunctionContext();
        var variables        = new HashMap<String, Val>();

        load("action_schema", variables);
        load("address_schema", variables);
        load("calendar_schema", variables);
        load("general_schema", variables);
        load("geographical_location_schema", variables);
        load("subject_schema", variables);
        load("vehicle_schema", variables);
        load("schema_with_additional_keywords", variables);

        load(List.of("action_schema", "address_schema", "calendar_schema", "general_schema",
                "geographical_location_schema", "subject_schema", "vehicle_schema", "schema_with_additional_keywords"),
                variables);

        var staticPlaygroundConfiguration = new PDPConfiguration("testConfig", attributeContext, functionContext,
                variables, CombiningAlgorithm.DENY_OVERRIDES, UnaryOperator.identity(), UnaryOperator.identity(),
                List.of(), mock(PolicyRetrievalPoint.class));
        return () -> Flux.just(staticPlaygroundConfiguration);
    }

    @SneakyThrows
    private void load(List<String> schemaFiles, Map<String, Val> variables) {
        var schemasArray = JsonNodeFactory.instance.arrayNode();
        for (var schemaFile : schemaFiles) {
            try (var is = this.getClass().getClassLoader().getResourceAsStream(schemaFile + ".json")) {
                if (is == null)
                    throw new RuntimeException(schemaFile + ".json not found");
                schemasArray.add(MAPPER.readValue(is, JsonNode.class));
            }
        }
        variables.put("SCHEMAS", Val.of(schemasArray));
    }

    @SneakyThrows
    private void load(String schemaFile, Map<String, Val> variables) {
        try (var is = this.getClass().getClassLoader().getResourceAsStream(schemaFile + ".json")) {
            if (is == null)
                throw new RuntimeException(schemaFile + ".json not found");
            var schema = MAPPER.readValue(is, JsonNode.class);
            variables.put(schemaFile, Val.of(schema));
        }
    }

}
