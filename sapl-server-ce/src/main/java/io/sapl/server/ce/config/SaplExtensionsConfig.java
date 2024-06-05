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
package io.sapl.server.ce.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.sapl.api.functions.StaticFunctionLibrarySupplier;
import io.sapl.api.pip.StaticPolicyInformationPointSupplier;
import io.sapl.extensions.mqtt.MqttFunctionLibrary;
import io.sapl.extensions.mqtt.MqttPolicyInformationPoint;

@Configuration
public class SaplExtensionsConfig {

    @Bean
    StaticPolicyInformationPointSupplier mqttPolicyInformationPoint() {
        return () -> List.of(MqttPolicyInformationPoint.class);
    }

    @Bean
    StaticFunctionLibrarySupplier additionalLibraries() {
        return () -> List.of(MqttFunctionLibrary.class);
    }
}
