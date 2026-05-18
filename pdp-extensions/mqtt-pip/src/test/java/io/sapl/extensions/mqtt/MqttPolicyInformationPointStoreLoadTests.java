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
package io.sapl.extensions.mqtt;

import io.sapl.attributes.broker.pip.PolicyInformationPointAttributeBroker;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lightweight registration test: confirms that the MQTT PIP can be
 * loaded into an {@link PolicyInformationPointAttributeBroker} without errors.
 * The
 * functional behaviour against a real broker is covered by
 * {@link MqttPolicyInformationPointIT}.
 */
@DisplayName("MqttPolicyInformationPoint broker registration")
class MqttPolicyInformationPointBrokerLoadTests {

    @Test
    @DisplayName("loads under the mqtt namespace without errors")
    void whenLoadedIntoStoreThenRegistersUnderMqttNamespace() {
        try (val broker = new PolicyInformationPointAttributeBroker()) {
            val saplMqttClient = SaplMqttClient.withDefaults();
            try {
                val handle = broker.load(new MqttPolicyInformationPoint(saplMqttClient));

                assertThat(handle.pipName()).isEqualTo(MqttPolicyInformationPoint.NAME);
                assertThat(handle.isLoaded()).isTrue();
                assertThat(broker.catalog()).containsExactly(handle);
            } finally {
                saplMqttClient.close();
            }
        }
    }
}
