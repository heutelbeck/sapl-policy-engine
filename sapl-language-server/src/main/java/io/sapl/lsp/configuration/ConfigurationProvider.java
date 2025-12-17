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
package io.sapl.lsp.configuration;

/**
 * Strategy interface for resolving LSP configurations by ID.
 * Implementations provide the mapping from configuration IDs to
 * LSPConfiguration instances.
 *
 * <p>
 * For standalone LSP server usage, use {@link DefaultConfigurationProvider}
 * which
 * returns the minimal configuration with standard libraries for any ID.
 *
 * <p>
 * For embedded usage (e.g., Vaadin web editor), implement this interface to
 * provide configurations based on the actual PDP configuration, allowing
 * different editors to use different function libraries and PIPs.
 */
@FunctionalInterface
public interface ConfigurationProvider {

    /**
     * Resolves a configuration by its ID.
     *
     * @param configurationId the configuration identifier (may be null or empty)
     * @return the LSP configuration for the given ID, never null
     */
    LSPConfiguration getConfiguration(String configurationId);

}
