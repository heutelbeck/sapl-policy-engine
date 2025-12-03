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
package io.sapl.pdp;

import io.sapl.api.attributes.AttributeBroker;
import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.PDPConfiguration;
import io.sapl.compiler.CompilationContext;
import io.sapl.compiler.CompiledPolicy;
import io.sapl.compiler.SaplCompiler;
import io.sapl.compiler.SaplCompilerException;
import io.sapl.prp.NaivePolicyRetrievalPoint;
import lombok.RequiredArgsConstructor;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Configuration register using Reactor's replay().latest() sink pattern.
 * This implementation creates a new subscription for each reactive read,
 * which incurs Reactor's subscription overhead.
 *
 * <p>
 * This class is retained for benchmarking comparison with the optimized
 * {@link ConfigurationRegister} implementation.
 * </p>
 *
 * <p>
 * <b>Performance characteristics:</b>
 * </p>
 * <ul>
 * <li>Single-threaded: ~4.5M ops/sec</li>
 * <li>Multi-threaded: Plateaus at ~675K ops/sec due to subscription
 * contention</li>
 * </ul>
 *
 * @see ConfigurationRegister for the optimized implementation
 */
@RequiredArgsConstructor
public class ReplayLatestConfigurationRegister implements CompiledPDPConfigurationSource {

    private final FunctionBroker  functionBroker;
    private final AttributeBroker attributeBroker;

    private final Map<String, Sinks.Many<Optional<CompiledPDPConfiguration>>> sinks = new ConcurrentHashMap<>();

    /**
     * Lock-free cache for synchronous configuration access. ConcurrentHashMap.get()
     * is a volatile read with no locking, making it ideal for read-heavy workloads.
     */
    private final Map<String, Optional<CompiledPDPConfiguration>> currentConfigs = new ConcurrentHashMap<>();

    public void loadConfiguration(PDPConfiguration pdpConfiguration, boolean keepOldConfigOnError) {
        val namesInUse                = new HashSet<String>();
        val alwaysApplicableDocuments = new ArrayList<CompiledPolicy>();
        val maybeApplicableDocuments  = new ArrayList<CompiledPolicy>();

        val compilationContext = new CompilationContext(functionBroker, attributeBroker);
        for (String saplDocument : pdpConfiguration.saplDocuments()) {
            CompiledPolicy compiledDocument;
            try {
                compiledDocument = SaplCompiler.compile(saplDocument, compilationContext);
            } catch (SaplCompilerException exception) {
                if (!keepOldConfigOnError) {
                    removeConfigurationForPdp(pdpConfiguration.pdpId());
                }
                throw new IllegalArgumentException("Configuration rejected. Error compiling document.", exception);
            }
            if (namesInUse.contains(compiledDocument.name())) {
                if (!keepOldConfigOnError) {
                    removeConfigurationForPdp(pdpConfiguration.pdpId());
                }
                throw new IllegalArgumentException(
                        "Configuration rejected. Document name collision: %s".formatted(compiledDocument.name()));
            }
            namesInUse.add(compiledDocument.name());
            if (Value.TRUE.equals(compiledDocument.matchExpression())) {
                alwaysApplicableDocuments.add(compiledDocument);
            } else if (!Value.FALSE.equals(compiledDocument.matchExpression())) {
                maybeApplicableDocuments.add(compiledDocument);
            }
        }
        val policyRetrievalPoint = new NaivePolicyRetrievalPoint(alwaysApplicableDocuments, maybeApplicableDocuments);
        val newConfiguration     = new CompiledPDPConfiguration(pdpConfiguration.pdpId(),
                pdpConfiguration.configurationId(), pdpConfiguration.combiningAlgorithm(), pdpConfiguration.variables(),
                functionBroker, attributeBroker, policyRetrievalPoint);
        val optionalConfig       = Optional.of(newConfiguration);
        // Update cache first (lock-free write), then notify reactive subscribers
        currentConfigs.put(pdpConfiguration.pdpId(), optionalConfig);
        getSink(pdpConfiguration.pdpId()).tryEmitNext(optionalConfig);
    }

    public void removeConfigurationForPdp(String pdpId) {
        // Update cache first (lock-free write), then notify reactive subscribers
        currentConfigs.put(pdpId, Optional.empty());
        getSink(pdpId).tryEmitNext(Optional.empty());
    }

    @Override
    public Flux<Optional<CompiledPDPConfiguration>> getPDPConfigurations(String pdpId) {
        return getSink(pdpId).asFlux();
    }

    @Override
    public Optional<CompiledPDPConfiguration> getCurrentConfiguration(String pdpId) {
        return currentConfigs.getOrDefault(pdpId, Optional.empty());
    }

    private Sinks.Many<Optional<CompiledPDPConfiguration>> getSink(String pdpId) {
        return sinks.computeIfAbsent(pdpId, id -> {
            Sinks.Many<Optional<CompiledPDPConfiguration>> sink = Sinks.many().replay().latest();
            sink.tryEmitNext(Optional.empty());
            return sink;
        });
    }
}
