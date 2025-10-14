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
package io.sapl.interpreter;

import io.sapl.api.interpreter.Trace;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.grammar.sapl.CombiningAlgorithm;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CombinedDecisionTests {

    @Test
    void error() {
        final var decision = CombinedDecision.error(CombiningAlgorithm.DENY_OVERRIDES, "error message");
        assertThat(decision.getAuthorizationDecision().getDecision()).isEqualTo(Decision.INDETERMINATE);
        assertThat(decision.getTrace().get(Trace.COMBINING_ALGORITHM).textValue())
                .isEqualTo(CombiningAlgorithm.DENY_OVERRIDES.toString());
        assertThat(decision.getTrace().get(Trace.ERROR_MESSAGE).textValue()).isEqualTo("error message");
    }

    @Test
    void ofOneDecision() {
        final var decision = CombinedDecision.of(AuthorizationDecision.PERMIT, CombiningAlgorithm.DENY_OVERRIDES);
        assertThat(decision.getAuthorizationDecision().getDecision()).isEqualTo(Decision.PERMIT);
        assertThat(decision.getTrace().get(Trace.COMBINING_ALGORITHM).textValue())
                .isEqualTo(CombiningAlgorithm.DENY_OVERRIDES.toString());
    }

    @Test
    void actualCombination() {
        final var decision = CombinedDecision.of(AuthorizationDecision.DENY, CombiningAlgorithm.DENY_OVERRIDES,
                List.of(mock(DocumentEvaluationResult.class)));
        assertThat(decision.getAuthorizationDecision().getDecision()).isEqualTo(Decision.DENY);
        assertThat(decision.getTrace().get(Trace.COMBINING_ALGORITHM).textValue())
                .isEqualTo(CombiningAlgorithm.DENY_OVERRIDES.toString());
        assertThat(decision.getTrace().get(Trace.EVALUATED_POLICIES).isArray()).isTrue();
    }

}
