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
package io.sapl.pdp.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.sapl.api.model.Value;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.api.pdp.configuration.PdpData;
import io.sapl.functions.DefaultFunctionBroker;
import io.sapl.pdp.plugins.PluginsBundle;
import io.sapl.pdp.plugins.StaticPluginsSource;

import lombok.val;

@DisplayName("Subscriptions started by an update listener receive the current voter once")
class PdpVoterSourceSubscriptionTests {

    @Test
    @DisplayName("A listener registered during publication receives only its initial delivery")
    void whenListenerSubscribesDuringPublicationThenCurrentVoterIsDeliveredOnce() {
        val plugins = new StaticPluginsSource(new PluginsBundle(new DefaultFunctionBroker()));
        try (val source = new PdpVoterSource(plugins, Clock.systemUTC())) {
            val received = new ArrayList<String>();
            val newcomer = new Listener(2, event -> {
                             if (event instanceof PdpUpdateEvent.Voter(var ignoredId, var voter)) {
                                 received.add(voter.metadata().configurationId());
                             }
                         });
            source.subscribeToUpdates("orders", new Listener(0, event -> {
                if (event instanceof PdpUpdateEvent.Voter) {
                    source.subscribeToUpdates("orders", newcomer);
                }
            }));
            source.subscribeToUpdates("orders", new Listener(1, ignored -> {}));
            val configuration = new PDPConfiguration("orders", "original", CombiningAlgorithm.DEFAULT, List.of("""
                    policy "access" permit"""), new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT));

            source.loadConfiguration(configuration);

            assertThat(received).containsExactly("original");
        }
    }

    private record Listener(int hash, Consumer<PdpUpdateEvent> consumer) implements Consumer<PdpUpdateEvent> {
        @Override
        public void accept(PdpUpdateEvent event) {
            consumer.accept(event);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
