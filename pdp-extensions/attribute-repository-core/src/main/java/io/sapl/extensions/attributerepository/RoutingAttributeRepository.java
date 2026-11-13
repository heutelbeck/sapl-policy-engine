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
package io.sapl.extensions.attributerepository;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.attributes.broker.AttributeRepository;
import io.sapl.attributes.broker.repository.InMemoryAttributeRepository;
import io.sapl.attributes.broker.repository.RepositoryKey;
import lombok.NonNull;
import lombok.val;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;
import io.sapl.api.model.TextValue;
import io.sapl.pdp.configuration.PreparationResult;

/**
 * A multi-tenant {@link AttributeRepository} that routes observe requests to
 * the right repository configured for the pdpId. The repositories are built
 * lazy during startup with a tolerance using a retry-with-backoff logic to
 * reconnect. The class is just a routing facade and not a concrete repository
 * implementation itself and only observe requests are routed to the concrete
 * {@code AttributeRepository}.
 *
 * @since 4.2.0
 */
@Slf4j
public class RoutingAttributeRepository implements AttributeRepository {
    private static final String ERROR_ATTRIBUTE_REPOSITORY_CLOSED  = "Couldn't connect to repository with pdp id {} and configuration id {}: {}.";
    private static final String WARN_RETRYING_ATTRIBUTE_REPOSITORY = "Retrying to connect to the repository for pdp with id {} and configuration id {}: {}.";
    private static final String ERROR_REPOSITORY_STILL_BUILDING    = "Attribute Repository for configuration with id '%s' is still connecting.";
    private static final String ERROR_REPOSITORY_UNKNOWN           = "Attribute Repository for configuration with id '%s' is unknown.";
    private static final String REASON_TYPE_MISSING                = "the field 'type' is missing";
    private static final String REASON_NO_PROVIDER                 = "no backend module for type '%s' on the classpath. Available types: %s";
    private static final String REASON_INVALID_FIELDS              = "required fields for type '%s' are missing or invalid";
    private static final String WARN_CLOSE_FAILED                  = "Could not close the old attribute repository of pdp id {} (configuration id {}): {}";
    private static final String ATTRIBUTE_REPOSITORY_EXTENSION     = "attributerepository";

    private static final Duration RETRY_FIRST_BACKOFF = Duration.ofSeconds(1);
    private static final Duration RETRY_MAX_BACKOFF   = Duration.ofSeconds(30);

    private final ConcurrentHashMap<RouteKey, AttributeRepository>     cache               = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String>                    pdpToConfig         = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<RouteKey, Value>                   rawConfig           = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<RouteKey, Disposable>              pendingBuilds       = new ConcurrentHashMap<>();
    private final AtomicBoolean                                        closed              = new AtomicBoolean(false);
    private final ConcurrentHashMap<RouteKey, Set<PendingObservation>> waitingObservations = new ConcurrentHashMap<>();
    private final AtomicReference<Consumer<Runnable>>                  batchRunner         = new AtomicReference<>(
            Runnable::run);

    // Holds back a observe call if the repository is still building
    private record PendingObservation(
            AttributeFinderInvocation inv,
            Consumer<Value> onValue,
            AtomicReference<Registration> liveRegistration) {}

    // Routing key used by the router to identify the right repository from a
    // request
    private record RouteKey(String pdpId, String configurationId) {}

    // Builds the repository from the config and falls back to an In-Memory
    // Repository.
    private static AttributeRepository createRepository(Value repoNode, String pdpId) {
        if (repoNode instanceof ObjectValue obj) {
            return AttributeRepositoryFactory.create(obj, pdpId);
        }
        return new InMemoryAttributeRepository();
    }

    /**
     * Routes the invocation {@code inv} to the repository built for its pdp id and
     * configuration id. Queues the invocation if the repository is unknown or not
     * connected.
     */
    @Override
    public Registration observe(@NonNull AttributeFinderInvocation inv, @NonNull Consumer<Value> onValue) {
        val key        = new RouteKey(inv.pdpId(), inv.configurationId());
        val repository = cache.get(key);

        if (repository != null) {
            return repository.observe(inv, onValue);
        }

        val configId = key.configurationId();
        val message  = pendingBuilds.containsKey(key) ? ERROR_REPOSITORY_STILL_BUILDING.formatted(configId)
                : ERROR_REPOSITORY_UNKNOWN.formatted(configId);
        onValue.accept(Value.error(message));

        val liveRegistration = new AtomicReference<Registration>();
        val pending          = new PendingObservation(inv, onValue, liveRegistration);
        waitingObservations.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(pending);

        return () -> {
            Optional.ofNullable(waitingObservations.get(key)).ifPresent(set -> set.remove(pending));
            Optional.ofNullable(liveRegistration.getAndSet(null)).ifPresent(Registration::close);
        };
    }

    /**
     * Not supported. This class only routes {@link #observe} calls.
     *
     * @throws UnsupportedOperationException The current implementation doesn't need
     * a publish on that level.
     */
    @Override
    public void publish(@NonNull RepositoryKey key, @NonNull Value value) {
        throw new UnsupportedOperationException();
    }

    /**
     * Not supported. This class only routes {@link #observe} calls.
     *
     * @throws UnsupportedOperationException The current implementation doesn't need
     * a publish on that level.
     */
    @Override
    public void publish(@NonNull RepositoryKey key, @NonNull Value value, @NonNull Duration ttl) {
        throw new UnsupportedOperationException();
    }

    /**
     * Not supported. This class only routes {@link #observe} calls.
     *
     * @throws UnsupportedOperationException The current implementation doesn't need
     * a publish on that level.
     */
    @Override
    public void remove(@NonNull RepositoryKey key) {
        throw new UnsupportedOperationException();
    }

    /**
     * Set this router as closed and cancels every pending repository build and
     * closes every cached repository.
     */
    @Override
    public void close() {
        closed.set(true);
        pendingBuilds.values().forEach(Disposable::dispose);
        pendingBuilds.clear();
        cache.values().forEach(AttributeRepository::close);
    }

    // Builds the repository asynchronously, using a retry-with-backoff to tolerate
    // transient connection failures, that a slow connection never blocks the
    // configuration event processing.
    private void buildWithRetry(RouteKey key, Value repoNode) {
        // @formatter:off
        val disposable = Mono.fromCallable(() -> createRepository(repoNode, key.pdpId()))
                .retryWhen(Retry.backoff(Long.MAX_VALUE, RETRY_FIRST_BACKOFF)
                		.maxBackoff(RETRY_MAX_BACKOFF)
                        .scheduler(Schedulers.boundedElastic())
                        .filter(failure -> !closed.get())
                        .doBeforeRetry(signal -> log.warn(WARN_RETRYING_ATTRIBUTE_REPOSITORY,
                        		key.pdpId(),
                        		key.configurationId(),
                        		signal.failure().getMessage())))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(repository -> onRepositoryBuilt(key, repository), failure -> log.error(ERROR_ATTRIBUTE_REPOSITORY_CLOSED,
                		key.pdpId(),
                		key.configurationId(),
                		failure.getMessage()));
        // @formatter:on
        pendingBuilds.put(key, disposable);
    }

    // Repository is built, put it in the repo cache, re-run the missed observe
    // calls
    private void onRepositoryBuilt(RouteKey key, AttributeRepository repository) {
        pendingBuilds.remove(key);

        // If repository was closed during the creation: close it.
        if (closed.get()) {
            repository.close();
            return;
        }

        cache.put(key, repository);
        repository.setBatchRunner(batchRunner.get());
        activateConfiguration(key.pdpId(), key.configurationId());

        val waiting = waitingObservations.remove(key);
        if (waiting != null) {
            waiting.forEach(
                    pending -> pending.liveRegistration().set(repository.observe(pending.inv(), pending.onValue())));
        }
    }

    /*
     * Updates the routing information in case of a config change. Closes the old
     * repository. Used when a config changes.
     */
    private void activateConfiguration(String pdpId, String configId) {
        val oldConfigId = pdpToConfig.put(pdpId, configId);

        if (oldConfigId != null && !oldConfigId.equals(configId)) {
            val oldKey = new RouteKey(pdpId, oldConfigId);
            Optional.ofNullable(cache.remove(oldKey)).ifPresent(old -> {
                try {
                    old.close();
                } catch (RuntimeException failure) {
                    log.warn(WARN_CLOSE_FAILED, pdpId, oldConfigId, failure.getMessage());
                }
            });
            rawConfig.remove(oldKey);
        }
    }

    /*
     * Combines the config from both ext-* configs to one object value.
     */
    private static Value extractRepositoryConfig(PDPConfiguration configuration) {
        val extConfig  = configuration.extensions().get(ATTRIBUTE_REPOSITORY_EXTENSION);
        val extSecrets = configuration.extensionSecrets().get(ATTRIBUTE_REPOSITORY_EXTENSION);

        if (extConfig == null && extSecrets == null) {
            return Value.UNDEFINED;
        }

        val builder = ObjectValue.builder();

        if (extConfig instanceof ObjectValue obj) {
            builder.putAll(obj);
        }

        if (extSecrets instanceof ObjectValue obj) {
            builder.putAll(obj);
        }

        return builder.build();
    }

    /**
     * Reacts to prepare() method of the extension processor. Rejects an invalid
     * configuration instead of failing silent. A missing configuration is loading
     * an {@code InMemoryAttributeRepository}.
     */
    PreparationResult prepare(PDPConfiguration configuration) {
        val node = extractRepositoryConfig(configuration);
        if (!(node instanceof ObjectValue obj) || AttributeRepositoryFactory.validate(obj)) {
            return new PreparationResult.Accepted();
        }
        return new PreparationResult.Rejected(rejectReason(obj));
    }

    /*
     * Reacts to the commit() method of the extension processor. A known
     * configuration becomes the current one and a unknown configuration is built in
     * the background.
     */
    void buildOrRoute(String pdpId, PDPConfiguration configuration) {
        val configId = configuration.configurationId();
        val key      = new RouteKey(pdpId, configId);
        val repoNode = extractRepositoryConfig(configuration);

        rawConfig.put(key, repoNode);
        if (cache.containsKey(key)) {
            activateConfiguration(pdpId, configId);
        } else if (!pendingBuilds.containsKey(key)) {
            buildWithRetry(key, repoNode);
        }
    }

    /*
     * Reacts to the remove() method of the extension processor and removes the
     * configuration for the current pdp.
     */
    void removeForPdp(String pdpId) {
        val configId = pdpToConfig.remove(pdpId);
        if (configId == null) {
            return;
        }

        val key = new RouteKey(pdpId, configId);
        Optional.ofNullable(pendingBuilds.remove(key)).ifPresent(Disposable::dispose);
        Optional.ofNullable(cache.remove(key)).ifPresent(AttributeRepository::close);
        rawConfig.remove(key);
    }

    /**
     * The current raw configuration the router knows for the given pdp id. The last
     * configuration is set by {@link #buildOrRoute(String, PDPConfiguration)}.
     *
     * @param pdpId The pdp id to return the raw configuration for
     * @return The raw configuration
     */
    public Optional<Value> currentRawConfig(String pdpId) {
        return Optional.ofNullable(pdpToConfig.get(pdpId))
                .map(configId -> rawConfig.get(new RouteKey(pdpId, configId)));
    }

    @Override
    public void setBatchRunner(Consumer<Runnable> batchRunner) {
        this.batchRunner.set(batchRunner);
        cache.values().forEach(repository -> repository.setBatchRunner(batchRunner));
    }

    private String rejectReason(ObjectValue config) {
        if (!(config.get("type") instanceof TextValue typeField)) {
            return REASON_TYPE_MISSING;
        }

        val type      = typeField.value();
        val available = AttributeRepositoryFactory.availableTypes();
        if (!available.contains(type.toLowerCase(Locale.ROOT))) {
            return REASON_NO_PROVIDER.formatted(type, available);
        }
        return REASON_INVALID_FIELDS.formatted(type);
    }
}
