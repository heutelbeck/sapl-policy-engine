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
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.sapl.api.model.Value;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.api.pdp.configuration.PdpData;
import io.sapl.pdp.plugins.MutablePluginsSource;
import io.sapl.pdp.plugins.PluginsBundle;
import io.sapl.pdp.plugins.StaticPluginsSource;
import io.sapl.util.SaplTesting;
import lombok.val;

@DisplayName("PdpVoterSource extension coordination")
class PdpVoterSourceExtensionsTests {

    private static final Clock   CLOCK      = Clock.fixed(Instant.parse("2026-02-13T12:00:00Z"), ZoneOffset.UTC);
    private static final PdpData EMPTY_DATA = new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT);

    private static PDPConfiguration validConfig(String pdpId) {
        return new PDPConfiguration(pdpId, "cfg-1", CombiningAlgorithm.DEFAULT, List.of("policy \"p\" permit"),
                EMPTY_DATA);
    }

    private static PluginsBundle pluginsBundle() {
        return new PluginsBundle(SaplTesting.FUNCTION_BROKER);
    }

    @Test
    @DisplayName("a configuration that goes live is prepared and then committed")
    void whenConfigurationLoadedThenPreparedAndCommitted() {
        val recorder = new Recorder();
        try (val source = new PdpVoterSource(new StaticPluginsSource(pluginsBundle()), CLOCK, recorder)) {
            source.loadConfiguration(validConfig("default"));

            assertThat(recorder.prepared).containsExactly("default");
            assertThat(recorder.committed).containsExactly("default");
        }
    }

    @Test
    @DisplayName("a configuration deferred for missing plugins is committed once the plugins arrive")
    void whenDeferredThenCommittedOnPluginsArrival() {
        val recorder = new Recorder();
        val plugins  = new MutablePluginsSource();
        try (val source = new PdpVoterSource(plugins, CLOCK, recorder)) {
            source.loadConfiguration(validConfig("default"));
            assertThat(recorder.prepared).containsExactly("default");
            assertThat(recorder.committed).isEmpty();

            plugins.publish(pluginsBundle());

            assertThat(recorder.committed).containsExactly("default");
        }
    }

    @Test
    @DisplayName("removing a configuration tears down its extensions")
    void whenConfigurationRemovedThenExtensionsRemoved() {
        val recorder = new Recorder();
        try (val source = new PdpVoterSource(new StaticPluginsSource(pluginsBundle()), CLOCK, recorder)) {
            source.loadConfiguration(validConfig("default"));
            source.removeConfigurationForPdp("default");

            assertThat(recorder.removed).containsExactly("default");
        }
    }

    @Test
    @DisplayName("closing the source tears down extensions for every live pdpId")
    void whenClosedThenExtensionsRemovedForAll() {
        val recorder = new Recorder();
        val source   = new PdpVoterSource(new StaticPluginsSource(pluginsBundle()), CLOCK, recorder);
        source.loadConfiguration(validConfig("orders"));
        source.loadConfiguration(validConfig("billing"));

        source.close();

        assertThat(recorder.removed).containsExactlyInAnyOrder("orders", "billing");
    }

    @Test
    @DisplayName("a configuration the processor rejects is neither committed nor served")
    void whenPrepareRejectsThenNotCommittedAndFailsClosed() {
        val recorder = new Recorder();
        recorder.accept = false;
        try (val source = new PdpVoterSource(new StaticPluginsSource(pluginsBundle()), CLOCK, recorder)) {
            source.loadConfiguration(validConfig("default"));

            assertThat(recorder.committed).isEmpty();
            assertThat(source.getCurrentConfiguration("default")).isEmpty();
            assertThat(source.getPdpStatus("default"))
                    .hasValueSatisfying(status -> assertThat(status.state()).isEqualTo(PdpState.ERROR));
        }
    }

    private static final class Recorder implements ExtensionsProcessor {

        private final List<String> prepared  = new CopyOnWriteArrayList<>();
        private final List<String> committed = new CopyOnWriteArrayList<>();
        private final List<String> removed   = new CopyOnWriteArrayList<>();
        private volatile boolean   accept    = true;

        @Override
        public boolean prepare(String pdpId, PDPConfiguration configuration) {
            prepared.add(pdpId);
            return accept;
        }

        @Override
        public void commit(String pdpId, PDPConfiguration configuration) {
            committed.add(pdpId);
        }

        @Override
        public void remove(String pdpId) {
            removed.add(pdpId);
        }
    }
}
