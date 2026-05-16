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
import io.sapl.api.attributes.AttributeAccessContext;
import io.sapl.api.documentation.DocumentationBundle;
import io.sapl.api.documentation.LibraryDocumentation;
import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.api.stream.BlockingWebClient;
import io.sapl.api.stream.RealTimeScheduler;
import io.sapl.api.stream.Stream;
import io.sapl.api.stream.Streams;
import io.sapl.api.stream.TimeScheduler;
import io.sapl.attributes.libraries.HttpPolicyInformationPoint;
import io.sapl.attributes.libraries.JWTKeyProvider;
import io.sapl.attributes.libraries.JWTPolicyInformationPoint;
import io.sapl.attributes.libraries.TimePolicyInformationPoint;
import io.sapl.attributes.libraries.X509PolicyInformationPoint;
import io.sapl.attributes.broker.AttributeRepository;
import io.sapl.attributes.broker.AttributeBroker;
import io.sapl.attributes.broker.pip.PolicyInformationPointAttributeBroker;
import io.sapl.attributes.broker.layered.LayeredAttributeBroker;
import io.sapl.attributes.broker.repository.InMemoryAttributeRepository;
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
import org.springframework.context.annotation.Primary;

import java.net.http.HttpClient;
import java.security.Key;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Optional;

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
        for (var library : DefaultLibraries.defaults()) {
            broker.load(library);
        }
        broker.load(new GeographicFunctionLibrary());
        broker.load(new MqttFunctionLibrary());
        broker.load(new TraccarFunctionLibrary());
        return broker;
    }

    @Bean
    PolicyInformationPointAttributeBroker policyInformationPointAttributeBroker(JsonMapper mapper) {
        var clock     = Clock.systemUTC();
        var scheduler = new RealTimeScheduler(clock);
        var webClient = new DummyBlockingWebClient(mapper, HttpClient.newHttpClient(), clock, scheduler);
        var mqtt      = new DummySaplMqttClient(clock, scheduler);
        var keys      = new DummyJWTKeyProvider(clock);

        var broker = new PolicyInformationPointAttributeBroker();
        broker.load(new TimePolicyInformationPoint(clock, scheduler));
        broker.load(new HttpPolicyInformationPoint(webClient));
        broker.load(new TraccarPolicyInformationPoint(webClient));
        broker.load(new JWTPolicyInformationPoint(keys, clock, scheduler));
        broker.load(new MqttPolicyInformationPoint(mqtt));
        broker.load(new X509PolicyInformationPoint(clock, scheduler));
        return broker;
    }

    @Bean
    InMemoryAttributeRepository inMemoryAttributeRepository() {
        return new InMemoryAttributeRepository();
    }

    @Bean
    AttributeRepository attributeRepository(InMemoryAttributeRepository inMemoryAttributeRepository) {
        return inMemoryAttributeRepository;
    }

    @Bean
    @Primary
    AttributeBroker attributeBroker(PolicyInformationPointAttributeBroker policyInformationPointAttributeBroker,
            InMemoryAttributeRepository inMemoryAttributeRepository) {
        return new LayeredAttributeBroker(policyInformationPointAttributeBroker, inMemoryAttributeRepository);
    }

    @Bean
    DocumentationBundle documentationBundle() {
        var libraries = new ArrayList<LibraryDocumentation>();
        for (var library : DefaultLibraries.defaults()) {
            libraries.add(LibraryDocumentationExtractor.extractFunctionLibrary(library.getClass()));
        }
        libraries.add(LibraryDocumentationExtractor.extractFunctionLibrary(GeographicFunctionLibrary.class));
        libraries.add(LibraryDocumentationExtractor.extractFunctionLibrary(MqttFunctionLibrary.class));
        libraries.add(LibraryDocumentationExtractor.extractFunctionLibrary(TraccarFunctionLibrary.class));
        libraries.add(LibraryDocumentationExtractor.extractPolicyInformationPoint(TimePolicyInformationPoint.class));
        libraries.add(LibraryDocumentationExtractor.extractPolicyInformationPoint(HttpPolicyInformationPoint.class));
        libraries.add(LibraryDocumentationExtractor.extractPolicyInformationPoint(TraccarPolicyInformationPoint.class));
        libraries.add(LibraryDocumentationExtractor.extractPolicyInformationPoint(JWTPolicyInformationPoint.class));
        libraries.add(LibraryDocumentationExtractor.extractPolicyInformationPoint(MqttPolicyInformationPoint.class));
        libraries.add(LibraryDocumentationExtractor.extractPolicyInformationPoint(X509PolicyInformationPoint.class));
        return new DocumentationBundle(libraries);
    }

    public static class DummyJWTKeyProvider extends JWTKeyProvider {
        public DummyJWTKeyProvider(Clock clock) {
            super(clock);
        }

        @Override
        public Optional<Key> provide(String kid, JsonNode jPublicKeyServer) {
            return Optional.empty();
        }
    }

    public static class DummySaplMqttClient extends SaplMqttClient {
        public DummySaplMqttClient(Clock clock, TimeScheduler scheduler) {
            super(clock, scheduler);
        }

        @Override
        public Stream<Value> buildSaplMqttMessageStream(Value topic, AttributeAccessContext ctx, Value qos,
                Value mqttPipConfig) {
            return Streams.just(PIP_CALL_BLOCKED);
        }
    }

    public static class DummyBlockingWebClient extends BlockingWebClient {

        public DummyBlockingWebClient(JsonMapper mapper, HttpClient httpClient, Clock clock, TimeScheduler scheduler) {
            super(mapper, httpClient, clock, scheduler);
        }

        @Override
        public Stream<Value> httpRequest(String httpMethod, ObjectValue requestSettings) {
            return Streams.just(PIP_CALL_BLOCKED);
        }

        @Override
        public Stream<Value> consumeWebSocket(ObjectValue requestSettings) {
            return Streams.just(PIP_CALL_BLOCKED);
        }
    }

}
