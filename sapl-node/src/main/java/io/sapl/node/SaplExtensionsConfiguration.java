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
package io.sapl.node;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.sapl.api.functions.FunctionLibraryProvider;
import io.sapl.api.stream.BlockingWebClient;
import io.sapl.api.stream.RealTimeScheduler;
import io.sapl.api.stream.TimeScheduler;
import io.sapl.extensions.mqtt.MqttFunctionLibrary;
import io.sapl.extensions.mqtt.MqttPolicyInformationPoint;
import io.sapl.extensions.mqtt.SaplMqttClient;
import io.sapl.functions.geo.GeographicFunctionLibrary;
import io.sapl.functions.geo.traccar.TraccarFunctionLibrary;
import io.sapl.pip.geo.traccar.TraccarPolicyInformationPoint;
import lombok.val;
import tools.jackson.databind.json.JsonMapper;

/**
 * Configuration for SAPL extensions including function libraries and policy
 * information points (PIPs).
 * <p>
 * Note: HttpPolicyInformationPoint and TimePolicyInformationPoint are already
 * included in the PDP defaults and should not be registered here.
 */
@Configuration
class SaplExtensionsConfiguration {

    // 5 second TCP connect cap so a stalled remote PIP host does not hang the
    // calling decision indefinitely. Per-request read timeouts are applied at
    // the HttpRequest level inside the PIPs themselves.
    private static final Duration HTTP_CONNECT_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    TimeScheduler timeScheduler(Clock clock) {
        return new RealTimeScheduler(clock);
    }

    @Bean
    BlockingWebClient blockingWebClient(JsonMapper mapper) {
        val httpClient = HttpClient.newBuilder().connectTimeout(HTTP_CONNECT_TIMEOUT).build();
        return new BlockingWebClient(mapper, httpClient);
    }

    @Bean
    TraccarPolicyInformationPoint traccarPolicyInformationPoint(BlockingWebClient blockingWebClient) {
        return new TraccarPolicyInformationPoint(blockingWebClient);
    }

    @Bean
    MqttPolicyInformationPoint mqttPolicyInformationPoint(Clock clock, TimeScheduler scheduler) {
        return new MqttPolicyInformationPoint(new SaplMqttClient(clock, scheduler));
    }

    @Bean
    FunctionLibraryProvider extensionFunctionLibraries() {
        return () -> List.of(new GeographicFunctionLibrary(), new TraccarFunctionLibrary(), new MqttFunctionLibrary());
    }

}
