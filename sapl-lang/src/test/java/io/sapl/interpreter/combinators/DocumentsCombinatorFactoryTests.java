/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static io.sapl.interpreter.combinators.DenyOverrides.DENY_OVERRIDES;
import static io.sapl.interpreter.combinators.DenyUnlessPermit.DENY_UNLESS_PERMIT;
import static io.sapl.interpreter.combinators.FirstApplicable.FIRST_APPLICABLE;
import static io.sapl.interpreter.combinators.OnlyOneApplicable.ONLY_ONE_APPLICABLE;
import static io.sapl.interpreter.combinators.PermitOverrides.PERMIT_OVERRIDES;
import static io.sapl.interpreter.combinators.PermitUnlessDeny.PERMIT_UNLESS_DENY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.PolicyEvaluationException;

class DocumentsCombinatorFactoryTests {

    @Test
    void permitUnlessDeny() {
        assertThat(CombiningAlgorithmFactory.documentsCombiningAlgorithm(PERMIT_UNLESS_DENY))
                .isInstanceOf(DocumentsCombiningAlgorithm.class);
        assertThat(CombiningAlgorithmFactory.policySetCombiningAlgorithm(PERMIT_UNLESS_DENY))
                .isInstanceOf(PolicySetCombiningAlgorithm.class);
    }

    @Test
    void permitOverrides() {
        assertThat(CombiningAlgorithmFactory.documentsCombiningAlgorithm(PERMIT_OVERRIDES))
                .isInstanceOf(DocumentsCombiningAlgorithm.class);
        assertThat(CombiningAlgorithmFactory.policySetCombiningAlgorithm(PERMIT_OVERRIDES))
                .isInstanceOf(PolicySetCombiningAlgorithm.class);
    }

    @Test
    void denyOverrides() {
        assertThat(CombiningAlgorithmFactory.documentsCombiningAlgorithm(DENY_OVERRIDES))
                .isInstanceOf(DocumentsCombiningAlgorithm.class);
        assertThat(CombiningAlgorithmFactory.policySetCombiningAlgorithm(DENY_OVERRIDES))
                .isInstanceOf(PolicySetCombiningAlgorithm.class);
    }

    @Test
    void oneApplicable() {
        assertThat(CombiningAlgorithmFactory.documentsCombiningAlgorithm(ONLY_ONE_APPLICABLE))
                .isInstanceOf(DocumentsCombiningAlgorithm.class);
        assertThat(CombiningAlgorithmFactory.policySetCombiningAlgorithm(ONLY_ONE_APPLICABLE))
                .isInstanceOf(PolicySetCombiningAlgorithm.class);
    }

    @Test
    void denyUnlessPermit() {
        assertThat(CombiningAlgorithmFactory.documentsCombiningAlgorithm(DENY_UNLESS_PERMIT))
                .isInstanceOf(DocumentsCombiningAlgorithm.class);
        assertThat(CombiningAlgorithmFactory.policySetCombiningAlgorithm(DENY_UNLESS_PERMIT))
                .isInstanceOf(PolicySetCombiningAlgorithm.class);
    }

    @Test
    void firstApplicable() {
        assertThat(CombiningAlgorithmFactory.policySetCombiningAlgorithm(FIRST_APPLICABLE))
                .isInstanceOf(PolicySetCombiningAlgorithm.class);
    }

    @Test
    void unknown() {
        assertThatThrownBy(() -> CombiningAlgorithmFactory.documentsCombiningAlgorithm(FIRST_APPLICABLE))
                .isInstanceOf(PolicyEvaluationException.class);
        assertThatThrownBy(() -> CombiningAlgorithmFactory.policySetCombiningAlgorithm("unknown"))
                .isInstanceOf(PolicyEvaluationException.class);
    }
}
