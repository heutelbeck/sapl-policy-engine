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
 * Callback interface for receiving PDP configuration updates.
 * <p>
 * Configuration sources notify consumers of configuration changes through this
 * interface. The callback is invoked:
 * <ul>
 * <li>Once during source construction with the initial configuration</li>
 * <li>On each subsequent configuration change (file modified, bundle
 * updated)</li>
 * </ul>
 * <p>
 * Implementations must be thread-safe as callbacks may be invoked from file
 * watcher threads or other background
 * threads.
 * <p>
 * Example usage:
 *
 * <pre>{@code
 * // Direct method reference
 * var source = new DirectoryPDPConfigurationSource(path, register::loadConfiguration);
 *
 * // Lambda with additional processing
 * var source = new DirectoryPDPConfigurationSource(path, config -> {
 *     log.info("Received configuration: {}", config.pdpId());
 *     register.loadConfiguration(config);
 * });
 * }</pre>
 */
@FunctionalInterface
public interface ConfigurationUpdateCallback {

    /**
     * Called when a configuration is loaded or updated.
     *
     * @param configuration
     * the new or updated PDP configuration
     */
    void onConfigurationUpdate(PDPConfiguration configuration);

}
