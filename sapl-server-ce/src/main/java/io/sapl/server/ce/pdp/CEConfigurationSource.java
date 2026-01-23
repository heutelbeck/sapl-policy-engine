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
package io.sapl.server.ce.pdp;

import io.sapl.api.attributes.AttributeBroker;
import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.UndefinedValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import static io.sapl.api.pdp.CombiningAlgorithm.DefaultDecision.DENY;
import static io.sapl.api.pdp.CombiningAlgorithm.ErrorHandling.ABSTAIN;
import static io.sapl.api.pdp.CombiningAlgorithm.VotingMode.PRIORITY_PERMIT;

import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.api.pdp.PDPConfiguration;
import io.sapl.compiler.expressions.CompilationContext;
import io.sapl.compiler.expressions.SaplCompilerException;
import io.sapl.compiler.pdp.CompiledPdpVoter;
import io.sapl.compiler.pdp.PdpCompiler;
import io.sapl.pdp.CompiledPDPConfigurationSource;
import io.sapl.server.ce.model.pdpconfiguration.Variable;
import io.sapl.server.ce.model.sapldocument.PolicyChangeListener;
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

    private final Sinks.Many<Optional<CompiledPdpVoter>> configurationSink;
    private final Flux<Optional<CompiledPdpVoter>>       configurationFlux;

    private final AtomicReference<Map<String, Value>>         currentVariables     = new AtomicReference<>(Map.of());
    private final AtomicReference<CombiningAlgorithm>         currentAlgorithm     = new AtomicReference<>(
            new CombiningAlgorithm(PRIORITY_PERMIT, DENY, ABSTAIN));
    private final AtomicLong                                  configurationVersion = new AtomicLong(0);
    private final AtomicReference<Optional<CompiledPdpVoter>> currentConfiguration = new AtomicReference<>(
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
    public Flux<Optional<CompiledPdpVoter>> getPDPConfigurations(String pdpId) {
        return configurationFlux;
    }

    @Override
    public Optional<CompiledPdpVoter> getCurrentConfiguration(String pdpId) {
        return currentConfiguration.get();
    }

    private void emitConfiguration() {
        val configuration = buildConfiguration();
        currentConfiguration.set(configuration);
        configurationSink.tryEmitNext(configuration);
    }

    private Optional<CompiledPdpVoter> buildConfiguration() {
        val configId           = String.valueOf(configurationVersion.incrementAndGet());
        val publishedDocuments = publishedSaplDocumentRepository.findAll();
        val documentStrings    = new ArrayList<String>();

        for (var publishedDocument : publishedDocuments) {
            documentStrings.add(publishedDocument.getDocument());
        }

        val pdpConfiguration   = new PDPConfiguration(PDP_ID, configId, currentAlgorithm.get(), documentStrings,
                currentVariables.get());
        val compilationContext = new CompilationContext(PDP_ID, configId, functionBroker, attributeBroker);

        try {
            return Optional.of(PdpCompiler.compilePDPConfiguration(pdpConfiguration, compilationContext));
        } catch (SaplCompilerException e) {
            log.error("Failed to compile PDP configuration: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static Map<String, Value> variablesCollectionToMap(@NonNull Collection<Variable> variables) {
        var variablesAsMap = HashMap.<String, Value>newHashMap(variables.size());
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
