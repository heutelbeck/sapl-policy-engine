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
package io.sapl.interpreter.combinators;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.sapl.grammar.sapl.CombiningAlgorithm;

class DocumentsCombinatorFactoryTests {

    @Test
    void permitUnlessDeny() {
        assertThat(CombiningAlgorithmFactory
                .documentsCombiningAlgorithm(PolicyDocumentCombiningAlgorithm.PERMIT_UNLESS_DENY))
                .isInstanceOf(DocumentsCombiningAlgorithm.class);
        assertThat(CombiningAlgorithmFactory.policySetCombiningAlgorithm(CombiningAlgorithm.PERMIT_UNLESS_DENY))
                .isInstanceOf(PolicySetCombiningAlgorithm.class);
    }

    @Test
    void permitOverrides() {
        assertThat(CombiningAlgorithmFactory
                .documentsCombiningAlgorithm(PolicyDocumentCombiningAlgorithm.PERMIT_OVERRIDES))
                .isInstanceOf(DocumentsCombiningAlgorithm.class);
        assertThat(CombiningAlgorithmFactory.policySetCombiningAlgorithm(CombiningAlgorithm.PERMIT_OVERRIDES))
                .isInstanceOf(PolicySetCombiningAlgorithm.class);
    }

    @Test
    void denyOverrides() {
        assertThat(
                CombiningAlgorithmFactory.documentsCombiningAlgorithm(PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES))
                .isInstanceOf(DocumentsCombiningAlgorithm.class);
        assertThat(CombiningAlgorithmFactory.policySetCombiningAlgorithm(CombiningAlgorithm.DENY_OVERRIDES))
                .isInstanceOf(PolicySetCombiningAlgorithm.class);
    }

    @Test
    void oneApplicable() {
        assertThat(CombiningAlgorithmFactory
                .documentsCombiningAlgorithm(PolicyDocumentCombiningAlgorithm.ONLY_ONE_APPLICABLE))
                .isInstanceOf(DocumentsCombiningAlgorithm.class);
        assertThat(CombiningAlgorithmFactory.policySetCombiningAlgorithm(CombiningAlgorithm.ONLY_ONE_APPLICABLE))
                .isInstanceOf(PolicySetCombiningAlgorithm.class);
    }

    @Test
    void denyUnlessPermit() {
        assertThat(CombiningAlgorithmFactory
                .documentsCombiningAlgorithm(PolicyDocumentCombiningAlgorithm.DENY_UNLESS_PERMIT))
                .isInstanceOf(DocumentsCombiningAlgorithm.class);
        assertThat(CombiningAlgorithmFactory.policySetCombiningAlgorithm(CombiningAlgorithm.DENY_UNLESS_PERMIT))
                .isInstanceOf(PolicySetCombiningAlgorithm.class);
    }

    @Test
    void firstApplicable() {
        assertThat(CombiningAlgorithmFactory.policySetCombiningAlgorithm(CombiningAlgorithm.FIRST_APPLICABLE))
                .isInstanceOf(PolicySetCombiningAlgorithm.class);
    }

}
