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

/**
 * A multi-tenant {@link AttributeRepository} that routes observe request's to the
 * right backend repository configured for the pdp id (tenant). The repositories
 * are built lazy during startup with a tolerance using a retry-with-backoff
 * logic to reconnect. The class is just a routing facade and not a concrete
 * repository implementation itself and only observe requests are routed to
 * the concrete {@code AttributeRepository}.
 * 
 * @since 4.2.0
 */
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

    // Retry Logic: pendingBuild = not build yet, 
    // closed = thread-safe indicator for an open/closed repository
    // waitingObservations = cache request in case a repository is not done yet
    private final ConcurrentHashMap<String, Disposable>              pendingBuilds       = new ConcurrentHashMap<>();
    private final AtomicBoolean                                      closed              = new AtomicBoolean(false);
    private final ConcurrentHashMap<String, Set<PendingObservation>> waitingObservations = new ConcurrentHashMap<>();

    private record PendingObservation(
            AttributeFinderInvocation inv,
            Consumer<Value> onValue,
            AtomicReference<Registration> liveRegistration) {}

    /**
     * Subscribes to {@code source} to get the pdp id (tenant) configuration
     * as they are published. Building the actual backend repositories happens
     * asynchronously as {@link PDPConfigurationSource.ConfigurationEvent}
     * are received.
     * @param source The configuration source to subscribe to.
     */
    public RoutingAttributeRepository(PDPConfigurationSource source) {
        source.subscribe(this::handleConfigurationEvent);
    }
    
    // Builds the concrete repository from the given config block for the pdp id. Falls
    // back to an InMemoryAttributeRepository if the tenant has no such block configured.
    private static AttributeRepository createRepository(Value repoNode, String pdpId) {
        if (repoNode instanceof ObjectValue obj) {
            return AttributeRepositoryFactory.create(obj, pdpId);
        }
        return new InMemoryAttributeRepository();
    }
    
    
    /**
     * Routes the invocation {@code inv} to the repository built for the right
     * configuration id {@code inv.configurationId()}. Queues the invocation
     * if the repository is unknown or not connected.
     */
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

    /**
     * Not supported. This class only routes {@link #observe} calls.
     * @throws UnsupportedOperationException
     */
    @Override
    public void publish(@NonNull RepositoryKey key, @NonNull Value value) {
        throw new UnsupportedOperationException();
    }
    
    /**
     * Not supported. This class only routes {@link #observe} calls.
     * @throws UnsupportedOperationException
     */
    @Override
    public void publish(@NonNull RepositoryKey key, @NonNull Value value, @NonNull Duration ttl) {
        throw new UnsupportedOperationException();
    }
    
    /**
     * Not supported. This class only routes {@link #observe} calls.
     * @throws UnsupportedOperationException
     */
    @Override
    public void remove(@NonNull RepositoryKey key) {
        throw new UnsupportedOperationException();
    }
    
    /**
     * Set this router as closed and cancels every pending repository build
     * and closes every cached backend repository.
     */
    @Override
    public void close() {
        closed.set(true);
        pendingBuilds.values().forEach(Disposable::dispose);
        pendingBuilds.clear();
        cache.values().forEach(AttributeRepository::close);
    }

    // Builds the repository aynchronously, using a retry-with-backoff to tolerate
    // transient connection failures, that a slow connection never blocks  the
    // configuration event processing.
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

    // After the repository is built this method is called. Remove it from the pending builds, mark it as
    // active, put it in the repository cache, route pdp id and config id and replays all observe calls
    // that were missed during build time.
    private void onRepositoryBuilt(String pdpId, String configId, AttributeRepository repository) {
        pendingBuilds.remove(configId);
        
        // Closed the repository immediately if a closed() did run in the meantime.
        // Discards the freshly built repository immediately instead of caching it.
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

    // Updates the routing information in case of configuration id change 
    // e.g. secret changed. Closes the repository with the old config id
    // and updates to the new one.
    private void route(String pdpId, String configId) {
        val oldConfigId = pdpToConfig.put(pdpId, configId);
        if (oldConfigId != null && !oldConfigId.equals(configId)) {
            Optional.ofNullable(cache.remove(oldConfigId)).ifPresent(AttributeRepository::close);
        }
    }
    
    // Reacts to the events received by the PDPConfigurationSource events. New configurations
    // are either re-routed to an already built repository or a fresh build is started when
    // the configuration id is seen the first time. Error or expired events are ignored on
    // purpose to not tear down a repository working repository in case of a wrong configuration.
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
