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
package io.sapl.interpreter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Trace;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;

class CombinedDecisionTests {

    @Test
    void error() {
        var decision = CombinedDecision.error("algorithm", "error message");
        assertThat(decision.getAuthorizationDecision().getDecision()).isEqualTo(Decision.INDETERMINATE);
        assertThat(decision.getTrace().get(Trace.COMBINING_ALGORITHM).textValue()).isEqualTo("algorithm");
        assertThat(decision.getTrace().get(Trace.ERROR_MESSAGE).textValue()).isEqualTo("error message");
    }

    @Test
    void ofOneDecision() {
        var decision = CombinedDecision.of(AuthorizationDecision.PERMIT, "test");
        assertThat(decision.getAuthorizationDecision().getDecision()).isEqualTo(Decision.PERMIT);
        assertThat(decision.getTrace().get(Trace.COMBINING_ALGORITHM).textValue()).isEqualTo("test");
    }

    @Test
    void actualCombination() {
        var decision = CombinedDecision.of(AuthorizationDecision.DENY, "test",
                List.of(mock(DocumentEvaluationResult.class)));
        assertThat(decision.getAuthorizationDecision().getDecision()).isEqualTo(Decision.DENY);
        assertThat(decision.getTrace().get(Trace.COMBINING_ALGORITHM).textValue()).isEqualTo("test");
        assertThat(decision.getTrace().get(Trace.EVALUATED_POLICIES).isArray()).isTrue();
    }

}
