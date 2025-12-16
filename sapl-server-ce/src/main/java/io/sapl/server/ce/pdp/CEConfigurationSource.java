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
package io.sapl.server.ce.pdp;

import io.sapl.api.attributes.AttributeBroker;
import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.UndefinedValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.compiler.CompilationContext;
import io.sapl.compiler.CompiledPolicy;
import io.sapl.compiler.SaplCompiler;
import io.sapl.pdp.CompiledPDPConfiguration;
import io.sapl.pdp.CompiledPDPConfigurationSource;
import io.sapl.prp.NaivePolicyRetrievalPoint;
import io.sapl.server.ce.model.pdpconfiguration.Variable;
import io.sapl.server.ce.model.sapldocument.PolicyChangeListener;
import io.sapl.server.ce.model.sapldocument.PublishedSaplDocument;
import io.sapl.server.ce.model.sapldocument.PublishedSaplDocumentRepository;
import io.sapl.server.ce.model.setup.condition.SetupFinishedCondition;
import jakarta.annotation.PostConstruct;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Configuration source for the Community Edition Server PDP.
 * Combines database-backed policies, variables, and combining algorithm
 * into CompiledPDPConfiguration objects for the PDP.
 */
@Slf4j
@Component
@Conditional(SetupFinishedCondition.class)
public class CEConfigurationSource
        implements CompiledPDPConfigurationSource, PDPConfigurationPublisher, PolicyChangeListener {

    private static final String PDP_ID = "default";

    private final PublishedSaplDocumentRepository publishedSaplDocumentRepository;
    private final FunctionBroker                  functionBroker;
    private final AttributeBroker                 attributeBroker;

    private final Sinks.Many<Optional<CompiledPDPConfiguration>> configurationSink;
    private final Flux<Optional<CompiledPDPConfiguration>>       configurationFlux;

    private final AtomicReference<Map<String, Value>>                 currentVariables     = new AtomicReference<>(
            Map.of());
    private final AtomicReference<CombiningAlgorithm>                 currentAlgorithm     = new AtomicReference<>(
            CombiningAlgorithm.DENY_UNLESS_PERMIT);
    private final AtomicLong                                          configurationVersion = new AtomicLong(0);
    private final AtomicReference<Optional<CompiledPDPConfiguration>> currentConfiguration = new AtomicReference<>(
            Optional.empty());

    public CEConfigurationSource(PublishedSaplDocumentRepository publishedSaplDocumentRepository,
            FunctionBroker functionBroker,
            AttributeBroker attributeBroker) {
        this.publishedSaplDocumentRepository = publishedSaplDocumentRepository;
        this.functionBroker                  = functionBroker;
        this.attributeBroker                 = attributeBroker;

        this.configurationSink = Sinks.many().replay().latest();
        this.configurationFlux = configurationSink.asFlux();
    }

    @PostConstruct
    public void init() {
        emitConfiguration();
    }

    @Override
    public void publishCombiningAlgorithm(@NonNull CombiningAlgorithm algorithm) {
        currentAlgorithm.set(algorithm);
        emitConfiguration();
    }

    @Override
    public void publishVariables(@NonNull Collection<Variable> variables) {
        currentVariables.set(variablesCollectionToMap(variables));
        emitConfiguration();
    }

    @Override
    public void onPoliciesChanged() {
        emitConfiguration();
    }

    @Override
    public Flux<Optional<CompiledPDPConfiguration>> getPDPConfigurations(String pdpId) {
        return configurationFlux;
    }

    @Override
    public Optional<CompiledPDPConfiguration> getCurrentConfiguration(String pdpId) {
        return currentConfiguration.get();
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

    private NaivePolicyRetrievalPoint buildPolicyRetrievalPoint() {
        val compilationContext = new CompilationContext(functionBroker, attributeBroker);
        val alwaysApplicable   = new ArrayList<CompiledPolicy>();
        val maybeApplicable    = new ArrayList<CompiledPolicy>();

        var publishedDocuments = publishedSaplDocumentRepository.findAll();
        for (var publishedDocument : publishedDocuments) {
            try {
                compilationContext.resetForNextDocument();
                val compiled = SaplCompiler.compile(publishedDocument.getDocument(), compilationContext);
                if (compiled.matchExpression().equals(Value.TRUE)) {
                    alwaysApplicable.add(compiled);
                } else {
                    maybeApplicable.add(compiled);
                }
            } catch (Exception exception) {
                log.error("Failed to compile policy '{}': {}", publishedDocument.getDocumentName(),
                        exception.getMessage());
            }
        }

        return new NaivePolicyRetrievalPoint(alwaysApplicable, maybeApplicable);
    }

    private static Map<String, Value> variablesCollectionToMap(@NonNull Collection<Variable> variables) {
        var variablesAsMap = new HashMap<String, Value>(variables.size());
        for (val variable : variables) {
            val value = ValueJsonMarshaller.json(variable.getJsonValue());
            if (value instanceof ErrorValue || value instanceof UndefinedValue) {
                log.error("Ignoring variable {} - not valid JSON.", variable.getName());
            } else {
                variablesAsMap.put(variable.getName(), value);
            }
        }
        return variablesAsMap;
    }

}
