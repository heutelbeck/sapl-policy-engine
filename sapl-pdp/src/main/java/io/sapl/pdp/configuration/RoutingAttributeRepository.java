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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

@Slf4j
public class RoutingAttributeRepository implements AttributeRepository {
    private static final String ERROR_ATTRIBUTE_REPOSITORY_CONFIG  = "Failed to apply attributeRepository configuration for pdpId '{}' (configurationId '{}'): {}. The previous configuration for this pdpId, if any, remains active.";
    private static final String ERROR_ATTRIBUTE_REPOSITORY_CLOSED  = "Couldn't connect to repository with pdp id {} and configuration id {}: {}.";
    private static final String WARN_RETRYING_ATTRIBUTE_REPOSITORY = "Retrying to connect to the repository for pdp with id {} and configuration id {}: {}.";
    private static final String ERROR_REPOSITORY_STILL_BUILDING    = "Attribute Repository for configuration with id '%s' is still connecting.";
    private static final String ERROR_REPOSITORY_UNKNOWN           = "Attribute Repository for configuration with id '%s' is unknown.";

    // Retry logic timeouts
    private static final Duration RETRY_FIRST_BACKOFF = Duration.ofSeconds(1);
    private static final Duration RETRY_MAX_BACKOFF   = Duration.ofSeconds(30);

    private final ConcurrentHashMap<String, AttributeRepository> cache       = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String>              pdpToConfig = new ConcurrentHashMap<>();

    // Retry Logic
    private final ConcurrentHashMap<String, Disposable>              pendingBuilds       = new ConcurrentHashMap<>();
    private final AtomicBoolean                                      closed              = new AtomicBoolean(false);
    private final ConcurrentHashMap<String, Set<PendingObservation>> waitingObservations = new ConcurrentHashMap<>();

    private record PendingObservation(
            AttributeFinderInvocation inv,
            Consumer<Value> onValue,
            AtomicReference<Registration> liveRegistration) {}

    public RoutingAttributeRepository(PDPConfigurationSource source) {
        // Register the repository during a config change and removes the old repository
        source.subscribe(this::handleConfigurationEvent);
    }

    private static AttributeRepository createRepository(Value repoNode, String pdpId) {
        if (repoNode instanceof ObjectValue obj) {
            return AttributeRepositoryFactory.create(obj, pdpId);
        }
        return new InMemoryAttributeRepository();
    }

    @Override
    public Registration observe(@NonNull AttributeFinderInvocation inv, @NonNull Consumer<Value> onValue) {
        val configId   = inv.configurationId();
        val repository = cache.get(configId);
        if (repository != null) {
            return repository.observe(inv, onValue);
        }

        val message = pendingBuilds.containsKey(configId) ? ERROR_REPOSITORY_STILL_BUILDING.formatted(configId)
                : ERROR_REPOSITORY_UNKNOWN.formatted(configId);
        onValue.accept(Value.error(message));

        val liveRegistration = new AtomicReference<Registration>();
        val pending          = new PendingObservation(inv, onValue, liveRegistration);
        waitingObservations.computeIfAbsent(configId, k -> ConcurrentHashMap.newKeySet()).add(pending);

        return () -> {
            Optional.ofNullable(waitingObservations.get(configId)).ifPresent(set -> set.remove(pending));
            Optional.ofNullable(liveRegistration.getAndSet(null)).ifPresent(Registration::close);
        };
    }

    // RoutingAttributeRepository is only there to support routing for observe
    @Override
    public void publish(@NonNull RepositoryKey key, @NonNull Value value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void publish(@NonNull RepositoryKey key, @NonNull Value value, @NonNull Duration ttl) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void remove(@NonNull RepositoryKey key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() {
        closed.set(true);
        pendingBuilds.values().forEach(Disposable::dispose);
        pendingBuilds.clear();
        cache.values().forEach(AttributeRepository::close);
    }

    // Retries to connect the repository if the configuration is still loading
    private void buildWithRetry(String pdpId, String configId, Value repoNode) {
        val disposable = Mono.fromCallable(() -> createRepository(repoNode, pdpId))
                .retryWhen(Retry.backoff(Long.MAX_VALUE, RETRY_FIRST_BACKOFF).maxBackoff(RETRY_MAX_BACKOFF)
                        .scheduler(Schedulers.boundedElastic()).filter(failure -> !closed.get())
                        .doBeforeRetry(signal -> log.warn(WARN_RETRYING_ATTRIBUTE_REPOSITORY, pdpId, configId,
                                signal.failure().getMessage())))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(repository -> onRepositoryBuilt(pdpId, configId, repository),
                        failure -> log.error(ERROR_ATTRIBUTE_REPOSITORY_CLOSED, pdpId, configId, failure.getMessage()));
        pendingBuilds.put(configId, disposable);
    }

    // Repository creation was successful
    private void onRepositoryBuilt(String pdpId, String configId, AttributeRepository repository) {
        pendingBuilds.remove(configId);
        if (closed.get()) {
            repository.close();
            return;
        }
        cache.put(configId, repository);
        route(pdpId, configId);

        val waiting = waitingObservations.remove(configId);
        if (waiting != null) {
            waiting.forEach(
                    pending -> pending.liveRegistration().set(repository.observe(pending.inv(), pending.onValue())));
        }
    }

    // Route to the right configuration id and clean up old ones
    private void route(String pdpId, String configId) {
        val oldConfigId = pdpToConfig.put(pdpId, configId);
        if (oldConfigId != null && !oldConfigId.equals(configId)) {
            Optional.ofNullable(cache.remove(oldConfigId)).ifPresent(AttributeRepository::close);
        }
    }

    private void handleConfigurationEvent(ConfigurationEvent event) {
        switch (event) {
        case ConfigurationEvent.NewConfiguration(var configuration) -> {
            val pdpId    = configuration.pdpId();
            val configId = configuration.configurationId();
            val repoNode = configuration.data().secrets().get("attributeRepository");

            try {
                if (cache.containsKey(configId)) {
                    route(pdpId, configId);
                } else if (!pendingBuilds.containsKey(configId)) {
                    buildWithRetry(pdpId, configId, repoNode);
                }
            } catch (RuntimeException failure) {
                log.error(ERROR_ATTRIBUTE_REPOSITORY_CONFIG, pdpId, configId, failure.getMessage());
            }
        }

        case ConfigurationEvent.ConfigurationRemoved(var pdpId) -> {
            val configId = pdpToConfig.remove(pdpId);
            Optional.ofNullable(pendingBuilds.remove(configId)).ifPresent(Disposable::dispose);
            Optional.ofNullable(cache.remove(configId)).ifPresent(AttributeRepository::close);
        }

        case ConfigurationEvent.ConfigurationError(var pdpId, var reason) -> { /* ignored during startup / building */ }

        case ConfigurationEvent.ConfigurationExpired(var pdpId, var reason) ->
            { /* ignored during startup / building */ }
        }
    }
}
