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

import io.sapl.compiler.pdp.CompiledPdpVoter;
import reactor.core.publisher.Flux;

import java.util.Optional;

public interface PdpVoterSource {
    /**
     * Returns a reactive stream of configuration updates for the specified PDP. Use
     * this for long-lived streaming
     * subscriptions that need to react to configuration changes in real-time.
     *
     * @param pdpId
     * the PDP identifier
     *
     * @return a Flux emitting configuration updates
     */
    Flux<Optional<CompiledPdpVoter>> getPDPConfigurations(String pdpId);

    /**
     * Returns the current configuration for the specified PDP synchronously. This
     * is a lock-free read operation
     * optimized for high-throughput scenarios where configuration changes are rare
     * but reads are frequent.
     *
     * @param pdpId
     * the PDP identifier
     *
     * @return the current configuration, or empty if no configuration is loaded
     */
    Optional<CompiledPdpVoter> getCurrentConfiguration(String pdpId);
}
