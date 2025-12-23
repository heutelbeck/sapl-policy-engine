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
package io.sapl.server.lt;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.functions.FunctionLibraryClassProvider;
import io.sapl.attributes.libraries.ReactiveWebClient;
import io.sapl.extensions.mqtt.MqttFunctionLibrary;
import io.sapl.extensions.mqtt.MqttPolicyInformationPoint;
import io.sapl.extensions.mqtt.SaplMqttClient;
import io.sapl.functions.geo.GeographicFunctionLibrary;
import io.sapl.functions.geo.traccar.TraccarFunctionLibrary;
import io.sapl.pip.geo.traccar.TraccarPolicyInformationPoint;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuration for SAPL extensions including function libraries and policy
 * information points (PIPs).
 * <p>
 * Note: HttpPolicyInformationPoint and TimePolicyInformationPoint are already
 * included in the PDP defaults and should not be registered here.
 */
@Configuration
@EnableAutoConfiguration(exclude = { R2dbcAutoConfiguration.class })
public class SaplExtensionsConfig {

    @Bean
    ReactiveWebClient reactiveWebClient(ObjectMapper mapper) {
        return new ReactiveWebClient(mapper);
    }

    @Bean
    TraccarPolicyInformationPoint traccarPolicyInformationPoint(ReactiveWebClient reactiveWebClient) {
        return new TraccarPolicyInformationPoint(reactiveWebClient);
    }

    @Bean
    MqttPolicyInformationPoint mqttPolicyInformationPoint() {
        return new MqttPolicyInformationPoint(new SaplMqttClient());
    }

    @Bean
    FunctionLibraryClassProvider extensionFunctionLibraries() {
        return () -> List.of(GeographicFunctionLibrary.class, TraccarFunctionLibrary.class, MqttFunctionLibrary.class);
    }

}
