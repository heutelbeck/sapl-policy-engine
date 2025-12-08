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
package io.sapl.playground.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.interpreter.Val;
import io.sapl.attributes.broker.api.AttributeStreamBroker;
import io.sapl.attributes.broker.impl.AnnotationPolicyInformationPointLoader;
import io.sapl.attributes.broker.impl.CachingAttributeStreamBroker;
import io.sapl.attributes.broker.impl.InMemoryAttributeRepository;
import io.sapl.attributes.broker.impl.InMemoryPolicyInformationPointDocumentationProvider;
import io.sapl.attributes.documentation.api.PolicyInformationPointDocumentationProvider;
import io.sapl.attributes.pips.http.HttpPolicyInformationPoint;
import io.sapl.attributes.pips.http.ReactiveWebClient;
import io.sapl.attributes.pips.jwt.JWTPolicyInformationPoint;
import io.sapl.attributes.pips.time.TimePolicyInformationPoint;
import io.sapl.extensions.mqtt.MqttFunctionLibrary;
import io.sapl.extensions.mqtt.MqttPolicyInformationPoint;
import io.sapl.extensions.mqtt.SaplMqttClient;
import io.sapl.functions.DefaultLibraries;
import io.sapl.functions.geo.GeographicFunctionLibrary;
import io.sapl.functions.geo.traccar.TraccarFunctionLibrary;
import io.sapl.functions.util.jwt.JWTKeyProvider;
import io.sapl.grammar.web.SAPLServlet;
import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.pdp.config.PDPConfiguration;
import io.sapl.pdp.config.PDPConfigurationProvider;
import io.sapl.pip.geo.traccar.TraccarPolicyInformationPoint;
import io.sapl.validation.ValidatorFactory;
import lombok.val;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.boot.web.servlet.filter.OrderedFormContentFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Configuration
@ComponentScan("io.sapl.grammar.ide.contentassist")
public class PlaygroundConfiguration {
    private static final Val PIP_CALL_BLOCKED = Val
            .error("The call to an external data source has been blocked by the playground application.");

    @Bean
    ServletRegistrationBean<SAPLServlet> xTextRegistrationBean() {
        ServletRegistrationBean<SAPLServlet> registration = new ServletRegistrationBean<>(new SAPLServlet(),
                "/xtext-service/*");
        registration.setName("XtextServices");
        registration.setAsyncSupported(true);
        return registration;
    }

    @Bean
    FilterRegistrationBean<OrderedFormContentFilter> registration1(OrderedFormContentFilter filter) {
        FilterRegistrationBean<OrderedFormContentFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    PolicyInformationPointDocumentationProvider policyInformationPointDocumentationProvider() {
        return new InMemoryPolicyInformationPointDocumentationProvider();
    }

    @Bean
    FunctionContext functionContext(ObjectMapper mapper) throws InitializationException {
        val staticLibraries = new ArrayList<Class<?>>(DefaultLibraries.STATIC_LIBRARIES);
        staticLibraries.addAll(
                List.of(GeographicFunctionLibrary.class, MqttFunctionLibrary.class, TraccarFunctionLibrary.class));
        return new AnnotationFunctionContext(() -> List.of(), () -> staticLibraries);
    }

    @Bean
    AttributeStreamBroker attributeStreamBroker(ObjectMapper mapper,
            PolicyInformationPointDocumentationProvider policyInformationPointDocumentationProvider) {
        val validatorFactory      = new ValidatorFactory(mapper);
        val attributeStreamBroker = new CachingAttributeStreamBroker(
                new InMemoryAttributeRepository(Clock.systemUTC()));
        val pipLoader             = new AnnotationPolicyInformationPointLoader(attributeStreamBroker,
                policyInformationPointDocumentationProvider, validatorFactory);
        val webClient             = new DummyReactiveWebClient(mapper);
        pipLoader.loadPolicyInformationPoint(new TimePolicyInformationPoint(Clock.systemUTC()));
        pipLoader.loadPolicyInformationPoint(new HttpPolicyInformationPoint(webClient));
        pipLoader.loadPolicyInformationPoint(new TraccarPolicyInformationPoint(webClient));
        pipLoader.loadPolicyInformationPoint(new JWTPolicyInformationPoint(new DummyJWTKeyProvider()));
        pipLoader.loadPolicyInformationPoint(new MqttPolicyInformationPoint(new DummySaplMqttClient()));
        return attributeStreamBroker;
    }

    public static class DummyJWTKeyProvider extends JWTKeyProvider {
        public DummyJWTKeyProvider() {
            super(WebClient.builder());
        }

        @Override
        public Mono<RSAPublicKey> provide(String kid, JsonNode jPublicKeyServer) throws CachingException {
            return Mono.error(new UnsupportedOperationException(
                    "The call to an external data source has been blocked by the playground application."));
        }
    }

    public static class DummySaplMqttClient extends SaplMqttClient {
        @Override
        public Flux<Val> buildSaplMqttMessageFlux(Val topic, Map<String, Val> variables, Val qos, Val mqttPipConfig) {
            return Flux.just(PIP_CALL_BLOCKED);
        }
    }

    public static class DummyReactiveWebClient extends ReactiveWebClient {

        public DummyReactiveWebClient(ObjectMapper mapper) {
            super(mapper);
        }

        @Override
        public Flux<Val> httpRequest(HttpMethod method, Val requestSettings) {
            return Flux.just(PIP_CALL_BLOCKED);
        }

        @Override
        public Flux<Val> consumeWebSocket(Val requestSettings) {
            return Flux.just(PIP_CALL_BLOCKED);
        }
    }

    @Bean
    PDPConfigurationProvider pdpConfigurationProvider(AttributeStreamBroker attributeStreamBroker,
            FunctionContext functionContext) {
        return () -> Flux.just(new PDPConfiguration("playground", attributeStreamBroker, functionContext, Map.of(),
                PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES, td -> td, as -> as, null));
    }
}
