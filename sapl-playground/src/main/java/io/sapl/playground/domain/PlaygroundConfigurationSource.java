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
package io.sapl.playground.domain;

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import static io.sapl.api.pdp.CombiningAlgorithm.DefaultDecision.ABSTAIN;
import static io.sapl.api.pdp.CombiningAlgorithm.ErrorHandling.PROPAGATE;
import static io.sapl.api.pdp.CombiningAlgorithm.VotingMode.PRIORITY_DENY;

import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.api.pdp.PDPConfiguration;
import io.sapl.api.pdp.PdpData;
import io.sapl.compiler.document.DocumentCompiler;
import io.sapl.compiler.expressions.CompilationContext;
import io.sapl.compiler.expressions.SaplCompilerException;
import io.sapl.pdp.DynamicPolicyDecisionPoint;
import io.sapl.pdp.configuration.PdpVoterSource;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

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
@Slf4j
public class PlaygroundConfigurationSource {

    private static final String PDP_ID = DynamicPolicyDecisionPoint.DEFAULT_PDP_ID;

    @Getter
    private final PdpVoterSource pdpVoterSource;

    private final AtomicReference<List<String>>       currentPolicySources = new AtomicReference<>(List.of());
    private final AtomicReference<Map<String, Value>> currentVariables     = new AtomicReference<>(Map.of());
    private final AtomicReference<CombiningAlgorithm> currentAlgorithm     = new AtomicReference<>(
            new CombiningAlgorithm(PRIORITY_DENY, ABSTAIN, PROPAGATE));
    private final AtomicLong                          configurationVersion = new AtomicLong(0);

    public PlaygroundConfigurationSource(PdpVoterSource pdpVoterSource) {
        this.pdpVoterSource = pdpVoterSource;
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
        val configId      = String.valueOf(configurationVersion.incrementAndGet());
        val configuration = new PDPConfiguration(PDP_ID, configId, currentAlgorithm.get(), currentPolicySources.get(),
                buildPdpData());
        try {
            pdpVoterSource.loadConfiguration(configuration, true);
        } catch (IllegalArgumentException e) {
            log.debug("Failed to compile PDP configuration: {}", e.getMessage());
        }
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
        val compilationContext = new CompilationContext(new PdpData(varablesAsObjectValue(), Value.EMPTY_OBJECT),
                pdpVoterSource.getFunctionBroker(), pdpVoterSource.getAttributeBroker());
        try {
            compilationContext.resetForNextDocument();
            DocumentCompiler.compileDocument(source, compilationContext);
            return Optional.empty();
        } catch (SaplCompilerException exception) {
            return Optional.of(exception);
        } catch (Exception exception) {
            return Optional.of(new SaplCompilerException(exception.getMessage(), exception));
        }
    }

    private PdpData buildPdpData() {
        return new PdpData(varablesAsObjectValue(), Value.EMPTY_OBJECT);
    }

    private ObjectValue varablesAsObjectValue() {
        val variables = ObjectValue.builder();
        for (val entry : currentVariables.get().entrySet()) {
            variables.put(entry.getKey(), entry.getValue());
        }
        return variables.build();
    }

}
