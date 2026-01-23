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
package io.sapl.server.ce.model.pdpconfiguration;

import static io.sapl.api.pdp.CombiningAlgorithm.DefaultDecision.DENY;
import static io.sapl.api.pdp.CombiningAlgorithm.ErrorHandling.ABSTAIN;
import static io.sapl.api.pdp.CombiningAlgorithm.VotingMode.PRIORITY_PERMIT;

import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.api.pdp.CombiningAlgorithm.DefaultDecision;
import io.sapl.api.pdp.CombiningAlgorithm.ErrorHandling;
import io.sapl.api.pdp.CombiningAlgorithm.VotingMode;
import io.sapl.server.ce.model.setup.condition.SetupFinishedCondition;
import io.sapl.server.ce.pdp.PDPConfigurationPublisher;
import jakarta.annotation.PostConstruct;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

/**
 * Service for managing the combining algorithm components.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Conditional(SetupFinishedCondition.class)
public class CombiningAlgorithmService {

    /**
     * Default combining algorithm: priority permit or deny errors abstain.
     */
    public static final CombiningAlgorithm DEFAULT = new CombiningAlgorithm(PRIORITY_PERMIT, DENY, ABSTAIN);

    private final SelectedCombiningAlgorithmRepository selectedCombiningAlgorithmRepository;
    private final PDPConfigurationPublisher            pdpConfigurationPublisher;

    @PostConstruct
    public void init() {
        pdpConfigurationPublisher.publishCombiningAlgorithm(getSelected());
    }

    /**
     * Gets the selected combining algorithm.
     *
     * @return the selected combining algorithm
     */
    public CombiningAlgorithm getSelected() {
        var entities = selectedCombiningAlgorithmRepository.findAll();
        if (entities.isEmpty()) {
            var defaultEntity = new SelectedCombiningAlgorithm(DEFAULT);
            selectedCombiningAlgorithmRepository.save(defaultEntity);
            return DEFAULT;
        }
        return entities.iterator().next().toCombiningAlgorithm();
    }

    /**
     * Gets the current voting mode.
     */
    public VotingMode getVotingMode() {
        return getSelected().votingMode();
    }

    /**
     * Gets the current default decision.
     */
    public DefaultDecision getDefaultDecision() {
        return getSelected().defaultDecision();
    }

    /**
     * Gets the current error handling strategy.
     */
    public ErrorHandling getErrorHandling() {
        return getSelected().errorHandling();
    }

    /**
     * Sets the voting mode component.
     */
    public void setVotingMode(@NonNull VotingMode votingMode) {
        var current = getSelected();
        var updated = new CombiningAlgorithm(votingMode, current.defaultDecision(), current.errorHandling());
        setSelected(updated);
    }

    /**
     * Sets the default decision component.
     */
    public void setDefaultDecision(@NonNull DefaultDecision defaultDecision) {
        var current = getSelected();
        var updated = new CombiningAlgorithm(current.votingMode(), defaultDecision, current.errorHandling());
        setSelected(updated);
    }

    /**
     * Sets the error handling component.
     */
    public void setErrorHandling(@NonNull ErrorHandling errorHandling) {
        var current = getSelected();
        var updated = new CombiningAlgorithm(current.votingMode(), current.defaultDecision(), errorHandling);
        setSelected(updated);
    }

    /**
     * Sets the complete combining algorithm.
     *
     * @param combiningAlgorithm the combining algorithm to set
     */
    public void setSelected(@NonNull CombiningAlgorithm combiningAlgorithm) {
        selectedCombiningAlgorithmRepository.deleteAll();
        selectedCombiningAlgorithmRepository.save(new SelectedCombiningAlgorithm(combiningAlgorithm));
        pdpConfigurationPublisher.publishCombiningAlgorithm(combiningAlgorithm);
        log.info("set combining algorithm: {}", combiningAlgorithm.toCanonicalString());
    }
}
