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

import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Trace;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.Decision;
import io.sapl.grammar.sapl.impl.util.ErrorFactory;

class PolicyDecisionTests {

    @Test
    void fromWhereResult() {
        final var decision = PolicyDecision.fromWhereResult("doc", Decision.INDETERMINATE,
                ErrorFactory.error("error message"));
        assertThat(decision.getEntitlement()).isEqualTo(Decision.INDETERMINATE);
        assertThat(decision.getTrace().get(Trace.ENTITLEMENT).textValue()).isEqualTo("INDETERMINATE");
        assertThat(decision.getTrace().get(Trace.WHERE).get(Trace.VALUE).textValue())
                .isEqualTo("|ERROR| error message");
    }

    @Test
    void withAdditionalData() {
        final var decision = PolicyDecision.fromWhereResult("doc", Decision.PERMIT, Val.TRUE)
                .withAdvice(Val.of("advice")).withObligation(Val.of("obligation")).withResource(Val.of("resource"));
        assertThat(decision.getEntitlement()).isEqualTo(Decision.PERMIT);
        assertThat(decision.getTrace().get(Trace.ENTITLEMENT).textValue()).isEqualTo("PERMIT");
        assertThat(decision.getTrace().get(Trace.WHERE).get(Trace.VALUE).asBoolean()).isTrue();
        assertThat(decision.getTrace().get(Trace.OBLIGATIONS).get(0).get(Trace.VALUE).textValue())
                .isEqualTo("obligation");
        assertThat(decision.getTrace().get(Trace.ADVICE).get(0).get(Trace.VALUE).textValue()).isEqualTo("advice");
        assertThat(decision.getTrace().get(Trace.RESOURCE).get(Trace.VALUE).textValue()).isEqualTo("resource");
    }

    @Test
    void withNullEntitlement() {
        final var decision = PolicyDecision.fromWhereResult("doc", null, Val.TRUE);
        assertThat(decision.getTrace().has(Trace.ENTITLEMENT)).isFalse();
    }

    @Test
    void withErrorMessage() {
        final var decision = PolicyDecision.ofImportError("policy", Decision.INDETERMINATE, "error message");
        assertThat(decision.getEntitlement()).isEqualTo(Decision.INDETERMINATE);
        assertThat(decision.getTrace().get(Trace.ERROR_MESSAGE).textValue()).isEqualTo("error message");
    }

    @Test
    void withTargetResult() {
        final var decision = PolicyDecision.ofTargetExpressionEvaluation("policy", Val.TRUE, Decision.NOT_APPLICABLE);
        assertThat(decision.getEntitlement()).isEqualTo(Decision.NOT_APPLICABLE);
        assertThat(decision.getTrace().get(Trace.TARGET).get(Trace.VALUE).asBoolean()).isTrue();
    }
}
