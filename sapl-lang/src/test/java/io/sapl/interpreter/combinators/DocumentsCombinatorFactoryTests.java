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

import static io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES;
import static io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm.DENY_UNLESS_PERMIT;
import static io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm.ONLY_ONE_APPLICABLE;
import static io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm.PERMIT_OVERRIDES;
import static io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm.PERMIT_UNLESS_DENY;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.jupiter.api.Test;

import io.sapl.interpreter.combinators.algorithms.DenyOverrides;
import io.sapl.interpreter.combinators.old.DenyUnlessPermitCombiningAlgorithmImplCustom;
import io.sapl.interpreter.combinators.old.OnlyOneApplicableCombiningAlgorithmImplCustom;
import io.sapl.interpreter.combinators.old.PermitOverridesCombiningAlgorithmImplCustom;
import io.sapl.interpreter.combinators.old.PermitUnlessDenyCombiningAlgorithmImplCustom;

class DocumentsCombinatorFactoryTests {

    @Test
    void permitUnlessDeny() {
        assertThat(CombiningAlgorithmFactory.getCombiningAlgorithm(PERMIT_UNLESS_DENY),
                instanceOf(PermitUnlessDenyCombiningAlgorithmImplCustom.class));
    }

    @Test
    void permitOverrides() {
        assertThat(CombiningAlgorithmFactory.getCombiningAlgorithm(PERMIT_OVERRIDES),
                instanceOf(PermitOverridesCombiningAlgorithmImplCustom.class));
    }

    @Test
    void denyOverrides() {
        assertThat(CombiningAlgorithmFactory.getCombiningAlgorithm(DENY_OVERRIDES),
                instanceOf(DenyOverrides.class));
    }

    @Test
    void oneApplicable() {
        assertThat(CombiningAlgorithmFactory.getCombiningAlgorithm(ONLY_ONE_APPLICABLE),
                instanceOf(OnlyOneApplicableCombiningAlgorithmImplCustom.class));
    }

    @Test
    void denyUnlessPermit() {
        assertThat(CombiningAlgorithmFactory.getCombiningAlgorithm(DENY_UNLESS_PERMIT),
                instanceOf(DenyUnlessPermitCombiningAlgorithmImplCustom.class));
    }

}
