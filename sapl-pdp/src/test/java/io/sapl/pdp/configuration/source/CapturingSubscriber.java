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
package io.sapl.pdp.configuration.source;

import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.pdp.configuration.source.PDPConfigurationSource.ConfigurationEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Test helper that subscribes to a {@link PDPConfigurationSource} and
 * records every emitted {@link ConfigurationEvent} for later inspection.
 * Loaded configurations are accessible via {@link #configs()} and removal
 * events via {@link #removedPdpIds()}. Both lists are thread-safe and
 * preserve emission order.
 */
final class CapturingSubscriber implements Consumer<ConfigurationEvent> {

    private final List<PDPConfiguration> configs       = new CopyOnWriteArrayList<>();
    private final List<String>           removedPdpIds = new CopyOnWriteArrayList<>();

    @Override
    public void accept(ConfigurationEvent event) {
        switch (event) {
        case ConfigurationEvent.Load(var config, var keepOldOnError) -> configs.add(config);
        case ConfigurationEvent.Remove(var pdpId)                    -> removedPdpIds.add(pdpId);
        }
    }

    List<PDPConfiguration> configs() {
        return configs;
    }

    List<String> removedPdpIds() {
        return removedPdpIds;
    }
}
