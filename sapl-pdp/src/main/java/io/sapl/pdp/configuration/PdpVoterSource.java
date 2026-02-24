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

import io.sapl.api.attributes.AttributeBroker;
import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.pdp.PDPConfiguration;
import io.sapl.compiler.expressions.CompilationContext;
import io.sapl.compiler.expressions.SaplCompilerException;
import io.sapl.compiler.pdp.CompiledPdpVoter;
import io.sapl.compiler.pdp.PdpCompiler;
import io.sapl.compiler.util.CompilationErrorFormatter;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Optimized configuration register using the HybridMono pattern for
 * high-throughput reactive access. This
 * implementation uses AtomicReference for lock-free reads combined with
 * Mono.fromSupplier() for minimal reactive
 * overhead.
 * <p>
 * <b>Performance characteristics:</b>
 * </p>
 * <ul>
 * <li>Single-threaded: ~35M ops/sec</li>
 * <li>Multi-threaded (16 threads): ~30M ops/sec (scales well)</li>
 * <li>Improvement over ReplayLatestConfigurationRegister: ~45x at high
 * concurrency</li>
 * </ul>
 * <p>
 * <b>Design rationale:</b>
 * </p>
 * <p>
 * The traditional approach of using {@code Sinks.many().replay().latest()}
 * incurs significant subscription overhead
 * when callers use {@code flux.next().block()}. This implementation optimizes
 * for the common case where configuration
 * reads are frequent but configuration changes are rare.
 * </p>
 * <p>
 * The HybridMono pattern provides:
 * </p>
 * <ul>
 * <li>Fast first-element delivery via Mono.fromSupplier() wrapping
 * AtomicReference</li>
 * <li>Reactive updates for streaming subscribers via directBestEffort sink</li>
 * <li>Full reactive semantics - configuration updates propagate correctly</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
public class PdpVoterSource {

    @Getter
    private final FunctionBroker  functionBroker;
    @Getter
    private final AttributeBroker attributeBroker;
    private final Clock           clock;

    /**
     * Per-PDP configuration cache using AtomicReference for lock-free volatile
     * reads. This provides better performance
     * than ConcurrentHashMap for single-value access patterns where the key is
     * known.
     */
    private final Map<String, AtomicReference<Optional<CompiledPdpVoter>>> configCache = new ConcurrentHashMap<>();

    /**
     * DirectBestEffort sinks for notifying streaming subscribers of configuration
     * changes. These are only used for true
     * streaming use cases, not for one-shot reads.
     */
    private final Map<String, Sinks.Many<Optional<CompiledPdpVoter>>> updateSinks = new ConcurrentHashMap<>();

    /**
     * Per-PDP status tracking for health reporting.
     */
    private final Map<String, AtomicReference<PdpStatus>> statusCache = new ConcurrentHashMap<>();

    /**
     * Loads a new configuration for a PDP, compiling all SAPL documents and making
     * the configuration immediately available to both synchronous and reactive
     * readers.
     * <p>
     * This method never throws. On compilation failure, the PDP transitions to
     * either STALE (if {@code keepOldConfigOnError} is true and a valid config
     * exists) or ERROR (storing an error voter that returns INDETERMINATE).
     * </p>
     *
     * @param pdpConfiguration the configuration to load
     * @param keepOldConfigOnError if true, retains existing configuration on
     * compilation errors
     */
    public void loadConfiguration(PDPConfiguration pdpConfiguration, boolean keepOldConfigOnError) {
        val              compilationContext = new CompilationContext(pdpConfiguration.pdpId(),
                pdpConfiguration.configurationId(), pdpConfiguration.data(), functionBroker, attributeBroker);
        CompiledPdpVoter newConfiguration;
        try {
            newConfiguration = PdpCompiler.compilePDPConfiguration(pdpConfiguration, compilationContext);
        } catch (SaplCompilerException compilerException) {
            val formattedError = CompilationErrorFormatter.format(compilerException);
            val now            = clock.instant();
            val currentState   = getStatusRef(pdpConfiguration.pdpId()).get().state();
            if (keepOldConfigOnError && (currentState == PdpState.LOADED || currentState == PdpState.STALE)) {
                getStatusRef(pdpConfiguration.pdpId()).updateAndGet(current -> new PdpStatus(PdpState.STALE,
                        current.configurationId(), current.combiningAlgorithm(), current.documentCount(),
                        current.lastSuccessfulLoad(), now, formattedError));
            } else {
                val errorVoter     = PdpCompiler.createErrorVoter(pdpConfiguration, compilationContext,
                        compilerException);
                val optionalConfig = Optional.of(errorVoter);
                getConfigRef(pdpConfiguration.pdpId()).set(optionalConfig);
                getUpdateSink(pdpConfiguration.pdpId()).tryEmitNext(optionalConfig);
                getStatusRef(pdpConfiguration.pdpId()).updateAndGet(current -> new PdpStatus(PdpState.ERROR, null, null,
                        0, current.lastSuccessfulLoad(), now, formattedError));
            }
            log.error("Configuration rejected:\n{}", formattedError);
            return;
        }
        val optionalConfig = Optional.of(newConfiguration);

        getConfigRef(pdpConfiguration.pdpId()).set(optionalConfig);
        getUpdateSink(pdpConfiguration.pdpId()).tryEmitNext(optionalConfig);

        val now = clock.instant();
        getStatusRef(pdpConfiguration.pdpId()).set(new PdpStatus(PdpState.LOADED, pdpConfiguration.configurationId(),
                pdpConfiguration.combiningAlgorithm().toCanonicalString(), pdpConfiguration.saplDocuments().size(), now,
                null, null));
    }

    /**
     * Removes the configuration for a PDP, notifying all subscribers and removing
     * its status entry.
     *
     * @param pdpId the PDP identifier
     */
    public void removeConfigurationForPdp(String pdpId) {
        val emptyConfig = Optional.<CompiledPdpVoter>empty();
        getConfigRef(pdpId).set(emptyConfig);
        getUpdateSink(pdpId).tryEmitNext(emptyConfig);
        statusCache.remove(pdpId);
    }

    /**
     * Returns a reactive stream of configuration updates for the specified PDP.
     * <p>
     * This implementation uses the HybridMono pattern: the first element is
     * delivered immediately via
     * {@link Mono#fromSupplier} reading from the AtomicReference cache, avoiding
     * Reactor's subscription overhead.
     * Subsequent elements come from the directBestEffort sink for streaming
     * subscribers.
     * </p>
     * <p>
     * For one-shot reads using {@code flux.next().block()}, this approach is ~45x
     * faster than the traditional
     * replay().latest() pattern at high concurrency.
     * </p>
     *
     * @param pdpId
     * the PDP identifier
     *
     * @return a Flux emitting the current configuration immediately, then any
     * updates
     */
    public Flux<Optional<CompiledPdpVoter>> getPDPConfigurations(String pdpId) {
        // HybridMono pattern: fast first element from cache, then stream updates
        // Mono.fromSupplier() has minimal overhead compared to Sinks subscription
        val initialValue = Mono.fromSupplier(() -> getConfigRef(pdpId).get());
        val updates      = getUpdateSink(pdpId).asFlux();

        // Concat initial value with updates
        // Note: here we cannot dedupe, because equals of CompiledPdpVoter is not
        // meaningfully defined.
        return Flux.concat(initialValue, updates);
    }

    /**
     * Returns the current configuration synchronously. This is the fastest path for
     * one-shot reads, using a simple
     * volatile read from AtomicReference.
     *
     * @param pdpId
     * the PDP identifier
     *
     * @return the current configuration, or empty if none is loaded
     */
    public Optional<CompiledPdpVoter> getCurrentConfiguration(String pdpId) {
        return getConfigRef(pdpId).get();
    }

    /**
     * Returns the current status of all known PDPs.
     *
     * @return an unmodifiable map of PDP ID to status
     */
    public Map<String, PdpStatus> getAllPdpStatuses() {
        val result = new ConcurrentHashMap<String, PdpStatus>();
        for (val entry : statusCache.entrySet()) {
            result.put(entry.getKey(), entry.getValue().get());
        }
        return Map.copyOf(result);
    }

    /**
     * Returns the current status of a specific PDP.
     *
     * @param pdpId the PDP identifier
     *
     * @return the current status, or empty if the PDP is unknown
     */
    public Optional<PdpStatus> getPdpStatus(String pdpId) {
        val ref = statusCache.get(pdpId);
        return ref != null ? Optional.of(ref.get()) : Optional.empty();
    }

    /**
     * Gets or creates the AtomicReference cache entry for a PDP.
     */
    private AtomicReference<Optional<CompiledPdpVoter>> getConfigRef(String pdpId) {
        return configCache.computeIfAbsent(pdpId, id -> new AtomicReference<>(Optional.empty()));
    }

    /**
     * Gets or creates the update sink for a PDP. Uses directBestEffort for minimal
     * overhead - updates are delivered
     * best-effort without backpressure.
     */
    private Sinks.Many<Optional<CompiledPdpVoter>> getUpdateSink(String pdpId) {
        return updateSinks.computeIfAbsent(pdpId, id -> Sinks.many().multicast().directBestEffort());
    }

    /**
     * Gets or creates the status reference for a PDP.
     */
    private AtomicReference<PdpStatus> getStatusRef(String pdpId) {
        return statusCache.computeIfAbsent(pdpId, id -> new AtomicReference<>(PdpStatus.initial()));
    }

}
