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
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.sapl.api.attributes.Attribute;
import io.sapl.api.attributes.AttributeBroker;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.attributes.EnvironmentAttribute;
import io.sapl.api.attributes.PolicyInformationPoint;
import io.sapl.api.documentation.DocumentationBundle;
import io.sapl.api.documentation.LibraryDocumentation;
import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.functions.FunctionInvocation;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.documentation.LibraryDocumentationExtractor;
import lombok.experimental.UtilityClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ComponentScan
@Configuration
public class SAPLIdeSpringTestConfiguration {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Bean
    ContentAssistConfigurationSource contentAssistConfigurationSource() throws IOException {
        var variables = new HashMap<String, Value>();
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

        variables.put("abba", ValueJsonMarshaller.json("""
                {
                  "a": {
                    "x": 0,
                    "y": 1
                  },
                  "b": "y"
                }
                """));

        var functionLibraryDocs = new ArrayList<LibraryDocumentation>();
        functionLibraryDocs.add(LibraryDocumentationExtractor.extractFunctionLibrary(SchemaTestFunctionLibrary.class));
        functionLibraryDocs.add(LibraryDocumentationExtractor.extractFunctionLibrary(TimeLibrary.class));

        var allDocs = new ArrayList<LibraryDocumentation>(functionLibraryDocs);
        allDocs.add(LibraryDocumentationExtractor.extractPolicyInformationPoint(TemperatureTestPip.class));
        allDocs.add(LibraryDocumentationExtractor.extractPolicyInformationPoint(ClockTestPip.class));
        allDocs.add(LibraryDocumentationExtractor.extractPolicyInformationPoint(PersonTestPip.class));

        var documentationBundle = new DocumentationBundle(List.copyOf(allDocs));

        var functionBroker  = new TestFunctionBroker();
        var attributeBroker = new TestAttributeBroker();

        var configuration = new ContentAssistPDPConfiguration("testPdp", "testConfig", variables, documentationBundle,
                functionBroker, attributeBroker);

        return configId -> Optional.of(configuration);
    }

    private void load(List<String> schemaFiles, Map<String, Value> variables) throws IOException {
        var schemasArray = JsonNodeFactory.instance.arrayNode();
        for (var schemaFile : schemaFiles) {
            try (var inputStream = this.getClass().getClassLoader().getResourceAsStream(schemaFile + ".json")) {
                if (null == inputStream) {
                    throw new RuntimeException(schemaFile + ".json not found");
                }
                schemasArray.add(MAPPER.readValue(inputStream, JsonNode.class));
            }
        }
        variables.put("SCHEMAS", ValueJsonMarshaller.fromJsonNode(schemasArray));
    }

    private void load(String schemaFile, Map<String, Value> variables) throws IOException {
        try (var inputStream = this.getClass().getClassLoader().getResourceAsStream(schemaFile + ".json")) {
            if (null == inputStream) {
                throw new RuntimeException(schemaFile + ".json not found");
            }
            var schema = MAPPER.readValue(inputStream, JsonNode.class);
            variables.put(schemaFile, ValueJsonMarshaller.fromJsonNode(schema));
        }
    }

    static class TestFunctionBroker implements FunctionBroker {
        @Override
        public Value evaluateFunction(FunctionInvocation invocation) {
            return Value.UNDEFINED;
        }

        @Override
        public List<Class<?>> getRegisteredLibraries() {
            return List.of(SchemaTestFunctionLibrary.class, TimeLibrary.class);
        }
    }

    static class TestAttributeBroker implements AttributeBroker {
        @Override
        public Flux<Value> attributeStream(AttributeFinderInvocation invocation) {
            return Flux.just(Value.UNDEFINED);
        }

        @Override
        public List<Class<?>> getRegisteredLibraries() {
            return List.of(TemperatureTestPip.class, ClockTestPip.class, PersonTestPip.class);
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
        public Flux<Value> age(Value personLeftHand) {
            return Flux.just(Value.UNDEFINED);
        }
    }

    @UtilityClass
    @PolicyInformationPoint(name = "clock")
    public static class ClockTestPip {
        @EnvironmentAttribute(docs = "current time")
        public Flux<Value> now() {
            return Flux.just(Value.of("NOW!"));
        }

        @EnvironmentAttribute(docs = "current time in millis")
        public Flux<Value> millis(Value personLeftHand) {
            return Flux.just(Value.of("NOW MILLI!"));
        }

        @EnvironmentAttribute(docs = "current time ticking")
        public Flux<Value> ticker() {
            return Flux.just(Value.of("TICK TOCK"));
        }
    }

    @UtilityClass
    @PolicyInformationPoint(name = "temperature")
    public static class TemperatureTestPip {
        @EnvironmentAttribute(docs = "current temperature reading", schema = """
                  {
                  "type": "object",
                  "properties": {
                    "value": { "type": "number" },
                    "unit": { "type": "string"}
                  }
                }
                """)
        public Flux<Value> now() {
            return Flux.just(ValueJsonMarshaller.json("""
                    {
                      "value" : 500,
                      "unit" : "K"
                    }
                    """));
        }

        @EnvironmentAttribute(docs = "mean temperature over period", schema = """
                {
                  "type": "object",
                  "properties": {
                    "value": { "type": "number" },
                    "period": { "type": "number"}
                  }
                }
                """)
        public Flux<Value> mean(Value a1, Value a2) {
            return Flux.just(ValueJsonMarshaller.json("""
                    {
                      "value" : 666,
                      "period" : 999
                    }
                    """));
        }

        @EnvironmentAttribute(docs = "predicted temperature forecast")
        public Flux<Value> predicted(Value a1) {
            return Flux.just(Value.of(789));
        }

        @Attribute(docs = "temperature at geographic location", schema = """
                {
                  "type": "object",
                  "properties": {
                    "value": { "type": "number" },
                    "unit": { "type": "string"}
                  }
                }
                """)
        public Flux<Value> atLocation(Value leftHandLocation) {
            return Flux.just(Value.of(123));
        }

        @Attribute(docs = "temperature at specific time", schema = """
                {
                  "value" : 500,
                  "unit" : "K"
                }
                """)
        public Flux<Value> atTime(Value leftHandTime) {
            return Flux.just(Value.of(123));
        }
    }

    @UtilityClass
    @FunctionLibrary(name = "time")
    public static class TimeLibrary {
        @Function(docs = "checks if first time is after second time")
        public Value after(Value time1, Value time2) {
            return Value.TRUE;
        }

        @Function(docs = "checks if first time is before second time")
        public Value before(Value time1, Value time2) {
            return Value.TRUE;
        }

        @Function(docs = "checks if time is between two times")
        public Value between(Value time1, Value time2) {
            return Value.TRUE;
        }

        @Function(docs = "extracts hour from time value")
        public Value hourOf(Value t1) {
            return Value.of(10);
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

        @Function(docs = "creates a person object", schema = PERSON_SCHEMA)
        public Value person(Value name, Value nationality, Value age) {
            return Value.UNDEFINED;
        }

        @Function(docs = "creates a dog object", schema = DOG_SCHEMA)
        public Value dog(Value dogRegistryRecord) {
            return Value.UNDEFINED;
        }

        @Function(docs = "gets food for species")
        public Value food(Value species) {
            return Value.UNDEFINED;
        }

        @Function(docs = "gets price of food")
        public Value foodPrice(Value food) {
            return Value.UNDEFINED;
        }

        @Function(docs = "creates geographic location", schema = LOCATION_SCHEMA)
        public Value location() {
            return Value.UNDEFINED;
        }

    }

}
