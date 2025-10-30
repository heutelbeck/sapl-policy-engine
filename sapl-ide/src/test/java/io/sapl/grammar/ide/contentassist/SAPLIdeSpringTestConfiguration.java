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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pip.Attribute;
import io.sapl.api.pip.EnvironmentAttribute;
import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.attributes.broker.impl.AnnotationPolicyInformationPointLoader;
import io.sapl.attributes.broker.impl.CachingAttributeStreamBroker;
import io.sapl.attributes.broker.impl.InMemoryAttributeRepository;
import io.sapl.attributes.broker.impl.InMemoryPolicyInformationPointDocumentationProvider;
import io.sapl.attributes.documentation.api.PolicyInformationPointDocumentationProvider;
import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.pdp.config.PDPConfiguration;
import io.sapl.pdp.config.PDPConfigurationProvider;
import io.sapl.prp.PolicyRetrievalPoint;
import io.sapl.validation.ValidatorFactory;
import lombok.experimental.UtilityClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static org.mockito.Mockito.mock;

@ComponentScan
@Configuration
public class SAPLIdeSpringTestConfiguration {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Bean
    PolicyInformationPointDocumentationProvider PolicyInformationPointDocumentationProvider() {
        return new InMemoryPolicyInformationPointDocumentationProvider();
    }

    @Bean
    PDPConfigurationProvider pdpConfiguration(PolicyInformationPointDocumentationProvider docsProvider)
            throws IOException, InitializationException {
        final var mapper                = new ObjectMapper();
        final var validatorFactory      = new ValidatorFactory(mapper);
        final var attributeRepository   = new InMemoryAttributeRepository(Clock.systemUTC());
        final var attributeStreamBroker = new CachingAttributeStreamBroker(attributeRepository);
        final var pipLoader             = new AnnotationPolicyInformationPointLoader(attributeStreamBroker,
                docsProvider, validatorFactory);

        pipLoader.loadStaticPolicyInformationPoint(TemperatureTestPip.class);
        pipLoader.loadStaticPolicyInformationPoint(ClockTestPip.class);
        pipLoader.loadStaticPolicyInformationPoint(PersonTestPip.class);

        final var functionContext = new AnnotationFunctionContext();
        functionContext.loadLibrary(SchemaTestFunctionLibrary.class);
        functionContext.loadLibrary(TimeLibrary.class);

        final var variables = new HashMap<String, Val>();
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

        variables.put("abba", Val.ofJson("""
                {
                  "a": {
                    "x": 0,
                    "y": 1
                  },
                  "b": "y"
                }
                """));

        final var staticPlaygroundConfiguration = new PDPConfiguration("testConfig", attributeStreamBroker,
                functionContext, variables, PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES, UnaryOperator.identity(),
                UnaryOperator.identity(), mock(PolicyRetrievalPoint.class));
        return () -> Flux.just(staticPlaygroundConfiguration);
    }

    private void load(List<String> schemaFiles, Map<String, Val> variables) throws IOException {
        final var schemasArray = JsonNodeFactory.instance.arrayNode();
        for (final var schemaFile : schemaFiles) {
            try (var is = this.getClass().getClassLoader().getResourceAsStream(schemaFile + ".json")) {
                if (null == is) {
                    throw new RuntimeException(schemaFile + ".json not found");
                }
                schemasArray.add(MAPPER.readValue(is, JsonNode.class));
            }
        }
        variables.put("SCHEMAS", Val.of(schemasArray));
    }

    private void load(String schemaFile, Map<String, Val> variables) throws IOException {
        try (var is = this.getClass().getClassLoader().getResourceAsStream(schemaFile + ".json")) {
            if (null == is) {
                throw new RuntimeException(schemaFile + ".json not found");
            }
            final var schema = MAPPER.readValue(is, JsonNode.class);
            variables.put(schemaFile, Val.of(schema));
        }
    }

    @UtilityClass
    @PolicyInformationPoint(name = "person")
    public static class PersonTestPip {
        static final String AGE_SCHEMA = """
                {
                  "type": "object",
                  "properties": {
                    "years": { "type": "number" },
                    "days": { "type": "number" }
                  }
                }
                """;

        @Attribute(docs = "age of person", schema = AGE_SCHEMA)
        public Flux<Val> age(Val personLeftHand) {
            return Flux.just(Val.UNDEFINED);
        }
    }

    @UtilityClass
    @PolicyInformationPoint(name = "clock")
    public static class ClockTestPip {
        @EnvironmentAttribute(docs = "current time")
        public Flux<Val> now() {
            return Flux.just(Val.of("NOW!"));
        }

        @EnvironmentAttribute(docs = "current time in millis")
        public Flux<Val> millis(Val personLeftHand) {
            return Flux.just(Val.of("NOW MILLI!"));
        }

        @EnvironmentAttribute(docs = "current time ticking")
        public Flux<Val> ticker() {
            return Flux.just(Val.of("TICK TOCK"));
        }
    }

    @UtilityClass
    @PolicyInformationPoint(name = "temperature")
    public static class TemperatureTestPip {
        @EnvironmentAttribute(docs = "current temp", schema = """
                  {
                  "type": "object",
                  "properties": {
                    "value": { "type": "number" },
                    "unit": { "type": "string"}
                  }
                }
                """)
        public Flux<Val> now() throws JsonProcessingException {
            return Flux.just(Val.ofJson("""
                    {
                      "value" : 500,
                      "unit" : "K"
                    }
                    """));
        }

        @EnvironmentAttribute(docs = "mean temp", schema = """
                {
                  "type": "object",
                  "properties": {
                    "value": { "type": "number" },
                    "period": { "type": "number"}
                  }
                }
                """)
        public Flux<Val> mean(Val a1, Val a2) throws JsonProcessingException {
            return Flux.just(Val.ofJson("""
                    {
                      "value" : 666,
                      "period" : 999
                    }
                    """));
        }

        @EnvironmentAttribute(docs = "current temp")
        public Flux<Val> predicted(Val a1) {
            return Flux.just(Val.of(789));
        }

        @Attribute(docs = "temp at location", schema = """
                {
                  "type": "object",
                  "properties": {
                    "value": { "type": "number" },
                    "unit": { "type": "string"}
                  }
                }
                """)
        public Flux<Val> atLocation(Val leftHandLocation) {
            return Flux.just(Val.of(123));
        }

        @Attribute(docs = "temp at location", schema = """
                {
                  "value" : 500,
                  "unit" : "K"
                }
                """)
        public Flux<Val> atTime(Val leftHandTime) {
            return Flux.just(Val.of(123));
        }
    }

    @UtilityClass
    @FunctionLibrary(name = "time")
    public static class TimeLibrary {
        @Function
        public Val after(Val t1, Val t2) {
            return Val.TRUE;
        }

        @Function
        public Val before(Val t1, Val t2) {
            return Val.TRUE;
        }

        @Function
        public Val between(Val t1, Val t2) {
            return Val.TRUE;
        }

        @Function
        public Val hourOf(Val t1) {
            return Val.of(10);
        }
    }

    @UtilityClass
    @FunctionLibrary(name = "schemaTest")
    public static class SchemaTestFunctionLibrary {
        static final String PERSON_SCHEMA = """
                {
                  "type": "object",
                  "properties": {
                    "name": { "type": "string" },
                    "nationality": { "type": "string" },
                    "age": { "type": "number" }
                  }
                }
                """;

        static final String DOG_SCHEMA      = """
                {
                  "type": "object",
                  "properties": {
                    "name": { "type": "string" },
                    "fur_color": { "type": "string" },
                    "species": { "type": "string" },
                    "age": { "type": "number" }
                  }
                }
                """;
        static final String LOCATION_SCHEMA = """
                {
                  "$id": "https://example.com/geographical-location.schema.json",
                  "$schema": "https://json-schema.org/draft/2020-12/schema",
                  "title": "Longitude and Latitude Values",
                  "description": "A geographical coordinate.",
                  "required": [ "latitude", "longitude" ],
                  "type": "object",
                  "properties": {
                    "latitude": {
                      "type": "number",
                      "minimum": -90,
                      "maximum": 90
                    },
                    "longitude": {
                      "type": "number",
                      "minimum": -180,
                      "maximum": 180
                    }
                  }
                }
                """;

        @Function(schema = PERSON_SCHEMA)
        public Val person(Val name, Val nationality, Val age) {
            return Val.UNDEFINED;
        }

        @Function(schema = DOG_SCHEMA)
        public Val dog(Val dogRegistryRecord) {
            return Val.UNDEFINED;
        }

        @Function
        public Val food(Val species) {
            return Val.UNDEFINED;
        }

        @Function
        public Val foodPrice(Val food) {
            return Val.UNDEFINED;
        }

        @Function(schema = LOCATION_SCHEMA)
        public Val location() {
            return Val.UNDEFINED;
        }

    }

}
