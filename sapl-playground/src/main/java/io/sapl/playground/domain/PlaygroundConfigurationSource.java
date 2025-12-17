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
package io.sapl.playground.domain;

import io.sapl.api.attributes.AttributeBroker;
import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.api.prp.PolicyRetrievalPoint;
import io.sapl.compiler.CompilationContext;
import io.sapl.compiler.CompiledPolicy;
import io.sapl.compiler.SaplCompiler;
import io.sapl.compiler.SaplCompilerException;
import io.sapl.parser.DefaultSAPLParser;
import io.sapl.parser.SAPLParser;
import io.sapl.pdp.CompiledPDPConfiguration;
import io.sapl.pdp.CompiledPDPConfigurationSource;
import io.sapl.prp.NaivePolicyRetrievalPoint;
import jakarta.annotation.PreDestroy;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * UI-scoped configuration source for the playground PDP. Combines shared
 * brokers with UI-scoped policies, variables,
 * and combining algorithm. Emits new configurations whenever any component
 * changes.
 */
public class PlaygroundConfigurationSource implements CompiledPDPConfigurationSource {

    private static final String     PDP_ID = "playground";
    private static final SAPLParser PARSER = new DefaultSAPLParser();

    private final FunctionBroker  functionBroker;
    private final AttributeBroker attributeBroker;

    private final Sinks.Many<Optional<CompiledPDPConfiguration>> configurationSink;
    private final Flux<Optional<CompiledPDPConfiguration>>       configurationFlux;

    private final AtomicReference<List<String>>                       currentPolicySources = new AtomicReference<>(
            List.of());
    private final AtomicReference<Map<String, Value>>                 currentVariables     = new AtomicReference<>(
            Map.of());
    private final AtomicReference<CombiningAlgorithm>                 currentAlgorithm     = new AtomicReference<>(
            CombiningAlgorithm.DENY_OVERRIDES);
    private final AtomicLong                                          configurationVersion = new AtomicLong(0);
    private final AtomicReference<Optional<CompiledPDPConfiguration>> currentConfiguration = new AtomicReference<>(
            Optional.empty());

    public PlaygroundConfigurationSource(FunctionBroker functionBroker, AttributeBroker attributeBroker) {
        this.functionBroker  = functionBroker;
        this.attributeBroker = attributeBroker;

        this.configurationSink = Sinks.many().replay().latest();
        this.configurationFlux = configurationSink.asFlux();

        emitConfiguration();
    }

    /**
     * Updates the policy documents and emits a new configuration.
     *
     * @param policySources
     * list of SAPL policy source strings
     */
    public void setPolicies(List<String> policySources) {
        currentPolicySources.set(List.copyOf(policySources));
        emitConfiguration();
    }

    /**
     * Updates the variables and emits a new configuration.
     *
     * @param variables
     * map of variable names to values
     */
    public void setVariables(Map<String, Value> variables) {
        currentVariables.set(Map.copyOf(variables));
        emitConfiguration();
    }

    /**
     * Updates the combining algorithm and emits a new configuration.
     *
     * @param algorithm
     * the combining algorithm
     */
    public void setCombiningAlgorithm(CombiningAlgorithm algorithm) {
        currentAlgorithm.set(algorithm);
        emitConfiguration();
    }

    private void emitConfiguration() {
        val configuration = buildConfiguration();
        currentConfiguration.set(configuration);
        configurationSink.tryEmitNext(configuration);
    }

    private Optional<CompiledPDPConfiguration> buildConfiguration() {
        val configId = String.valueOf(configurationVersion.incrementAndGet());
        val prp      = buildPolicyRetrievalPoint();

        return Optional.of(new CompiledPDPConfiguration(PDP_ID, configId, currentAlgorithm.get(),
                currentVariables.get(), functionBroker, attributeBroker, prp));
    }

    private PolicyRetrievalPoint buildPolicyRetrievalPoint() {
        val compilationContext = new CompilationContext(functionBroker, attributeBroker);
        val alwaysApplicable   = new ArrayList<CompiledPolicy>();
        val maybeApplicable    = new ArrayList<CompiledPolicy>();

        for (var source : currentPolicySources.get()) {
            try {
                compilationContext.resetForNextDocument();
                val compiled = SaplCompiler.compile(source, compilationContext);
                if (compiled.matchExpression().equals(Value.TRUE)) {
                    alwaysApplicable.add(compiled);
                } else {
                    maybeApplicable.add(compiled);
                }
            } catch (Exception exception) {
                // Skip invalid policies - they will show errors in the editor
            }
        }

        return new NaivePolicyRetrievalPoint(alwaysApplicable, maybeApplicable);
    }

    @Override
    public Flux<Optional<CompiledPDPConfiguration>> getPDPConfigurations(String pdpId) {
        return configurationFlux;
    }

    @Override
    public Optional<CompiledPDPConfiguration> getCurrentConfiguration(String pdpId) {
        return currentConfiguration.get();
    }

    /**
     * Attempts to compile a policy source and returns any compile errors.
     *
     * @param source
     * the SAPL policy source to compile
     *
     * @return optional containing the exception if compilation failed, empty if
     * successful
     */
    public Optional<SaplCompilerException> tryCompile(String source) {
        val compilationContext = new CompilationContext(functionBroker, attributeBroker);
        try {
            compilationContext.resetForNextDocument();
            SaplCompiler.compile(source, compilationContext);
            return Optional.empty();
        } catch (SaplCompilerException exception) {
            return Optional.of(exception);
        } catch (Exception exception) {
            return Optional.of(new SaplCompilerException(exception.getMessage(), exception));
        }
    }

    @PreDestroy
    public void destroy() {
        configurationSink.tryEmitComplete();
    }
}
