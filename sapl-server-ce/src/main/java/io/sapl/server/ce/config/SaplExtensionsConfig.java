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
package io.sapl.server.ce.config;

import java.time.Clock;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.attributes.AttributeBroker;
import io.sapl.api.functions.FunctionBroker;
import io.sapl.attributes.CachingAttributeBroker;
import io.sapl.attributes.InMemoryAttributeRepository;
import io.sapl.attributes.libraries.HttpPolicyInformationPoint;
import io.sapl.attributes.libraries.ReactiveWebClient;
import io.sapl.attributes.libraries.TimePolicyInformationPoint;
import io.sapl.extensions.mqtt.MqttFunctionLibrary;
import io.sapl.extensions.mqtt.MqttPolicyInformationPoint;
import io.sapl.extensions.mqtt.SaplMqttClient;
import io.sapl.functions.DefaultFunctionBroker;
import io.sapl.functions.DefaultLibraries;
import io.sapl.functions.geo.GeographicFunctionLibrary;
import io.sapl.functions.geo.traccar.TraccarFunctionLibrary;
import io.sapl.pip.geo.traccar.TraccarPolicyInformationPoint;

@Configuration
@EnableAutoConfiguration(exclude = { R2dbcAutoConfiguration.class })
public class SaplExtensionsConfig {

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
    AttributeBroker attributeBroker(HttpPolicyInformationPoint httpPip, TraccarPolicyInformationPoint traccarPip,
            MqttPolicyInformationPoint mqttPip) {
        var repository = new InMemoryAttributeRepository(Clock.systemUTC());
        var broker     = new CachingAttributeBroker(repository);
        broker.loadPolicyInformationPointLibrary(new TimePolicyInformationPoint(Clock.systemUTC()));
        broker.loadPolicyInformationPointLibrary(httpPip);
        broker.loadPolicyInformationPointLibrary(traccarPip);
        broker.loadPolicyInformationPointLibrary(mqttPip);
        return broker;
    }

    @Bean
    ReactiveWebClient reactiveWebClient(ObjectMapper mapper) {
        return new ReactiveWebClient(mapper);
    }

    @Bean
    TraccarPolicyInformationPoint traccarPolicyInformationPoint(ReactiveWebClient reactiveWebClient) {
        return new TraccarPolicyInformationPoint(reactiveWebClient);
    }

    @Bean
    HttpPolicyInformationPoint httpPolicyInformationPoint(ReactiveWebClient reactiveWebClient) {
        return new HttpPolicyInformationPoint(reactiveWebClient);
    }

    @Bean
    MqttPolicyInformationPoint mqttPolicyInformationPoint() {
        return new MqttPolicyInformationPoint(new SaplMqttClient());
    }

}
