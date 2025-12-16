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
package io.sapl.server.ce.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.attributes.libraries.HttpPolicyInformationPoint;
import io.sapl.attributes.libraries.ReactiveWebClient;
import io.sapl.extensions.mqtt.MqttPolicyInformationPoint;
import io.sapl.extensions.mqtt.SaplMqttClient;
import io.sapl.pip.geo.traccar.TraccarPolicyInformationPoint;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
    HttpPolicyInformationPoint httpPolicyInformationPoint(ReactiveWebClient reactiveWebClient) {
        return new HttpPolicyInformationPoint(reactiveWebClient);
    }

    @Bean
    MqttPolicyInformationPoint mqttPolicyInformationPoint() {
        return new MqttPolicyInformationPoint(new SaplMqttClient());
    }

}
