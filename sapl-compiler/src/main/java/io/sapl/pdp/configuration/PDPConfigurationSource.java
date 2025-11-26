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
import reactor.core.publisher.Flux;

import java.util.Set;

/**
 * Source of PDP configurations. Implementations handle the specifics of where
 * configurations come from (filesystem directories, ZIP bundles, classpath
 * resources, push-based mechanisms) and when they change.
 */
public interface PDPConfigurationSource {

    String DEFAULT_PDP_ID = "default";

    /**
     * Configuration stream for the default PDP.
     *
     * @return stream of configurations for the default PDP
     */
    default Flux<PDPConfiguration> configurations() {
        return configurations(DEFAULT_PDP_ID);
    }

    /**
     * Configuration stream for a specific PDP ID. Emits whenever configuration
     * changes for this PDP. For directory/resource sources, pdpId maps to
     * subdirectory name. For bundle sources, pdpId maps to bundle filename (minus
     * extension).
     *
     * @param pdpId the PDP identifier
     * @return stream of configurations, empty flux if pdpId is unknown
     */
    Flux<PDPConfiguration> configurations(String pdpId);

    /**
     * Stream of available PDP IDs from this source. Emits whenever PDPs are added
     * or removed (e.g., bundle file added/deleted, subdirectory created/removed).
     * For static sources like classpath resources, emits once and completes.
     *
     * @return stream of available PDP ID sets
     */
    Flux<Set<String>> availablePdpIds();

    /**
     * Releases resources held by this source (stops file watchers, closes
     * connections).
     */
    default void dispose() {
    }

}
