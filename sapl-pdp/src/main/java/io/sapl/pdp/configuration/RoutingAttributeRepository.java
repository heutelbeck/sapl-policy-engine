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

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.attributes.broker.AttributeRepository;
import io.sapl.attributes.broker.repository.InMemoryAttributeRepository;
import io.sapl.attributes.broker.repository.RepositoryKey;
import io.sapl.pdp.configuration.source.PDPConfigurationSource;
import io.sapl.pdp.configuration.source.PDPConfigurationSource.ConfigurationEvent;
import lombok.NonNull;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RoutingAttributeRepository implements AttributeRepository {
    private static final String ERROR_ATTRIBUTE_REPOSITORY_CONFIG = "Failed to apply attributeRepository configuration for pdpId '{}' (configurationId '{}'): {}. The previous configuration for this pdpId, if any, remains active.";

    // Shared fallback for observe() on an unknown configId. One instance for the whole process
    // lifetime, not one per call - InMemoryAttributeRepository starts a scheduler thread in its
    // constructor, so Map.getOrDefault(..., new InMemoryAttributeRepository()) would leak a
    // thread on every call because getOrDefault evaluates its default argument eagerly.
    private static final AttributeRepository EMPTY_REPOSITORY = new InMemoryAttributeRepository();

    private final ConcurrentHashMap<String, AttributeRepository> cache       = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String>              pdpToConfig = new ConcurrentHashMap<>();

    public RoutingAttributeRepository(PDPConfigurationSource source) {
        // Register the repository to the config change and removes the old repository
        source.subscribe(event -> {
            if (event instanceof ConfigurationEvent.NewConfiguration(io.sapl.api.pdp.configuration.PDPConfiguration configuration)) {
                val pdpId    = configuration.pdpId();
                val configId = configuration.configurationId();
                val repoNode = configuration.data().secrets().get("attributeRepository");

                // Build (or reuse) the repository for this configId BEFORE touching
                // pdpToConfig/evicting the old entry. If construction fails (e.g. an
                // unsupported attributeRepository.type), this pdpId must keep pointing
                // at whatever it pointed at before - not silently fall back to the
                // shared EMPTY_REPOSITORY, and not lose its previous, working repository.
                try {
                    cache.computeIfAbsent(configId, k -> createRepository(repoNode, pdpId));
                } catch (RuntimeException failure) {
                    log.error(ERROR_ATTRIBUTE_REPOSITORY_CONFIG, pdpId, configId, failure.getMessage());
                    return;
                }

                val oldConfigId = pdpToConfig.put(pdpId, configId);

                if (oldConfigId != null && !oldConfigId.equals(configId)) {
                    Optional.ofNullable(cache.remove(oldConfigId)).ifPresent(AttributeRepository::close);
                }
            } else if (event instanceof ConfigurationEvent.ConfigurationRemoved(String pdpId)) {

                val configId = pdpToConfig.remove(pdpId);
                Optional.ofNullable(cache.remove(configId)).ifPresent(AttributeRepository::close);
            }
        });
    }

    private static AttributeRepository createRepository(Value repoNode, String pdpId) {
        if (repoNode instanceof ObjectValue obj) {
            return AttributeRepositoryFactory.create(obj, pdpId);
        }
        return new InMemoryAttributeRepository();
    }

    @Override
    public Registration observe(@NonNull AttributeFinderInvocation inv, @NonNull Consumer<Value> onValue) {
        return cache.getOrDefault(inv.configurationId(), EMPTY_REPOSITORY).observe(inv, onValue);
    }

    @Override
    public void publish(@NonNull RepositoryKey key, @NonNull Value value) {
        // RoutingAttributeRepository is only there to support routing for observe
        throw new UnsupportedOperationException();
    }

    @Override
    public void publish(@NonNull RepositoryKey key, @NonNull Value value, @NonNull Duration ttl) {
        // RoutingAttributeRepository is only there to support routing for observe
        throw new UnsupportedOperationException();
    }

    @Override
    public void remove(@NonNull RepositoryKey key) {
        // RoutingAttributeRepository is only there to support routing for observe
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() {
        cache.values().forEach(AttributeRepository::close);
    }
}
