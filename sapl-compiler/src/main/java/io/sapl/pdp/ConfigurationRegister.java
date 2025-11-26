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

@RequiredArgsConstructor
public class ConfigurationRegister implements CompiledPDPConfigurationSource {

    private final FunctionBroker  functionBroker;
    private final AttributeBroker attributeBroker;

    private final Map<String, CompiledPDPConfiguration>                       configurations = new ConcurrentHashMap<>();
    private final Map<String, Sinks.Many<Optional<CompiledPDPConfiguration>>> sinks          = new ConcurrentHashMap<>();

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
        configurations.put(pdpConfiguration.pdpId(), newConfiguration);
        getSink(pdpConfiguration.pdpId()).tryEmitNext(Optional.of(newConfiguration));
    }

    public void removeConfigurationForPdp(String pdpId) {
        configurations.remove(pdpId);
        getSink(pdpId).tryEmitNext(Optional.empty());
    }

    @Override
    public Flux<Optional<CompiledPDPConfiguration>> getPDPConfigurations(String pdpId) {
        return getSink(pdpId).asFlux();
    }

    private Sinks.Many<Optional<CompiledPDPConfiguration>> getSink(String pdpId) {
        return sinks.computeIfAbsent(pdpId, id -> {
            Sinks.Many<Optional<CompiledPDPConfiguration>> sink = Sinks.many().replay().latest();
            sink.tryEmitNext(Optional.empty());
            return sink;
        });
    }
}
