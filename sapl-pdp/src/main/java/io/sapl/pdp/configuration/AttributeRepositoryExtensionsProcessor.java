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

import io.sapl.api.pdp.configuration.PDPConfiguration;

/**
 * An {@link ExtensionsProcessor} adapter for the attribute repository configuration.
 * Translates calls from {@link PdpVoterSource} into the corresponding methods on
 * {@link RoutingAttributeRepository}, adding no behavior of its own.
 */
public final class AttributeRepositoryExtensionsProcessor implements ExtensionsProcessor {
    private final RoutingAttributeRepository repository;

    public AttributeRepositoryExtensionsProcessor(RoutingAttributeRepository repository) {
        this.repository = repository;
    }

    /**
     * A structural validation of the configuration that is delegated to {@link RoutingAttributeRepository#canPrepare}.
     * Returns {@code true} if the "attributeRepository" extension is absent (InMemory fallback
     * remains valid) or structurally valid; {@code false} if it is present but malformed.
     */
    @Override
    public boolean prepare(String pdpId, PDPConfiguration configuration) {
        return repository.canPrepare(configuration);
    }

    /**
     * Called only after the policy compiled successfully. Delegates to
     * {@link RoutingAttributeRepository#buildOrRoute}, which builds or re-routes
     * the backend repository for this {@code pdpId}.
     */
    @Override
    public void commit(String pdpId, PDPConfiguration configuration) {
        repository.buildOrRoute(pdpId, configuration);
    }

    /**
     * Called when the configuration for this {@code pdpId} is removed or expired.
     * Delegates to {@link RoutingAttributeRepository#removeForPdp}, which closes
     * and evicts the corresponding repository.
     */
    @Override
    public void remove(String pdpId) {
        repository.removeForPdp(pdpId);
    }
}
