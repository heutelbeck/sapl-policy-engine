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
package io.sapl.pdp.configuration;

import io.sapl.api.pdp.PDPConfiguration;

/**
 * Extension of {@link PDPConfigurationSource} that accepts pushed
 * configurations. Typically used with REST endpoints or
 * webhook integrations where configurations are pushed from external systems
 * rather than loaded from files.
 */
public interface PushablePDPConfigurationSource extends PDPConfigurationSource {

    /**
     * Push a configuration update. The configuration stream for the corresponding
     * pdpId will emit this configuration.
     *
     * @param configuration
     * the configuration to push
     */
    void pushConfiguration(PDPConfiguration configuration);

    /**
     * Remove configuration for a PDP. Subscribers to the configuration stream for
     * this pdpId will receive appropriate
     * notification.
     *
     * @param pdpId
     * the PDP identifier to remove
     */
    void removeConfiguration(String pdpId);

    /**
     * Trigger reload from underlying source for a specific PDP. Useful for
     * pull-based scenarios where a webhook
     * notification triggers fetching updated configuration from a remote source.
     *
     * @param pdpId
     * the PDP identifier to reload
     */
    default void triggerReload(String pdpId) {
    }

    /**
     * Trigger reload for all PDPs from underlying source.
     */
    default void triggerReloadAll() {
    }

}
