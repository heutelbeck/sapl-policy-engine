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
package io.sapl.playground.config;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import io.sapl.api.attributes.AttributeBroker;
import io.sapl.api.documentation.DocumentationBundle;
import io.sapl.api.documentation.LibraryDocumentation;
import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.attributes.CachingAttributeBroker;
import io.sapl.attributes.InMemoryAttributeRepository;
import io.sapl.attributes.libraries.HttpPolicyInformationPoint;
import io.sapl.attributes.libraries.JWTKeyProvider;
import io.sapl.attributes.libraries.JWTPolicyInformationPoint;
import io.sapl.attributes.libraries.ReactiveWebClient;
import io.sapl.attributes.libraries.TimePolicyInformationPoint;
import io.sapl.documentation.LibraryDocumentationExtractor;
import io.sapl.extensions.mqtt.MqttFunctionLibrary;
import io.sapl.extensions.mqtt.MqttPolicyInformationPoint;
import io.sapl.extensions.mqtt.SaplMqttClient;
import io.sapl.functions.DefaultFunctionBroker;
import io.sapl.functions.DefaultLibraries;
import io.sapl.functions.geo.GeographicFunctionLibrary;
import io.sapl.functions.geo.traccar.TraccarFunctionLibrary;
import io.sapl.pip.geo.traccar.TraccarPolicyInformationPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Map;

@Configuration
public class PlaygroundConfiguration {

    private static final String ERROR_EXTERNAL_DATA_SOURCE_BLOCKED = "The call to an external data source has been blocked by the playground application.";

    private static final Value PIP_CALL_BLOCKED = Value.error(ERROR_EXTERNAL_DATA_SOURCE_BLOCKED);

    @Bean
    SaplJacksonModule saplJacksonModule() {
        return new SaplJacksonModule();
    }

    @Bean
    FunctionBroker functionBroker() {
        var broker = new DefaultFunctionBroker();
        for (var libraryClass : DefaultLibraries.STATIC_LIBRARIES) {
            broker.loadStaticFunctionLibrary(libraryClass);
        }
        broker.loadStaticFunctionLibrary(GeographicFunctionLibrary.class);
        broker.loadStaticFunctionLibrary(MqttFunctionLibrary.class);
        broker.loadStaticFunctionLibrary(TraccarFunctionLibrary.class);
        return broker;
    }

    @Bean
    AttributeBroker attributeBroker(JsonMapper mapper) {
        var repository = new InMemoryAttributeRepository(Clock.systemUTC());
        var broker     = new CachingAttributeBroker(repository);
        var webClient  = new DummyReactiveWebClient(mapper);

        broker.loadPolicyInformationPointLibrary(new TimePolicyInformationPoint(Clock.systemUTC()));
        broker.loadPolicyInformationPointLibrary(new HttpPolicyInformationPoint(webClient));
        broker.loadPolicyInformationPointLibrary(new TraccarPolicyInformationPoint(webClient));
        broker.loadPolicyInformationPointLibrary(new JWTPolicyInformationPoint(new DummyJWTKeyProvider()));
        broker.loadPolicyInformationPointLibrary(new MqttPolicyInformationPoint(new DummySaplMqttClient()));
        return broker;
    }

    @Bean
    DocumentationBundle documentationBundle() {
        var libraries = new ArrayList<LibraryDocumentation>();
        for (var libraryClass : DefaultLibraries.STATIC_LIBRARIES) {
            libraries.add(LibraryDocumentationExtractor.extractFunctionLibrary(libraryClass));
        }
        libraries.add(LibraryDocumentationExtractor.extractFunctionLibrary(GeographicFunctionLibrary.class));
        libraries.add(LibraryDocumentationExtractor.extractFunctionLibrary(MqttFunctionLibrary.class));
        libraries.add(LibraryDocumentationExtractor.extractFunctionLibrary(TraccarFunctionLibrary.class));
        libraries.add(LibraryDocumentationExtractor.extractPolicyInformationPoint(TimePolicyInformationPoint.class));
        libraries.add(LibraryDocumentationExtractor.extractPolicyInformationPoint(HttpPolicyInformationPoint.class));
        libraries.add(LibraryDocumentationExtractor.extractPolicyInformationPoint(TraccarPolicyInformationPoint.class));
        libraries.add(LibraryDocumentationExtractor.extractPolicyInformationPoint(JWTPolicyInformationPoint.class));
        libraries.add(LibraryDocumentationExtractor.extractPolicyInformationPoint(MqttPolicyInformationPoint.class));
        return new DocumentationBundle(libraries);
    }

    public static class DummyJWTKeyProvider extends JWTKeyProvider {
        public DummyJWTKeyProvider() {
            super(WebClient.builder());
        }

        @Override
        public Mono<RSAPublicKey> provide(String kid, JsonNode publicKeyServer) throws CachingException {
            return Mono.error(new UnsupportedOperationException(ERROR_EXTERNAL_DATA_SOURCE_BLOCKED));
        }
    }

    public static class DummySaplMqttClient extends SaplMqttClient {
        @Override
        public Flux<Value> buildSaplMqttMessageFlux(Value topic, Map<String, Value> variables, Value qos,
                Value mqttPipConfig) {
            return Flux.just(PIP_CALL_BLOCKED);
        }
    }

    public static class DummyReactiveWebClient extends ReactiveWebClient {

        public DummyReactiveWebClient(JsonMapper mapper) {
            super(mapper);
        }

        @Override
        public Flux<Value> httpRequest(HttpMethod method, ObjectValue requestSettings) {
            return Flux.just(PIP_CALL_BLOCKED);
        }

        @Override
        public Flux<Value> consumeWebSocket(ObjectValue requestSettings) {
            return Flux.just(PIP_CALL_BLOCKED);
        }
    }

}
