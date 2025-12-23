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
package io.sapl.api.pdp;

import io.sapl.api.model.*;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthorizationDecisionTests {

    @Test
    void of_shouldCreatePermitDecision() {
        val decisionObj = ObjectValue.builder().put("decision", new TextValue("PERMIT", ValueMetadata.EMPTY))
                .put("obligations", new ArrayValue(List.of(Value.of("log")), ValueMetadata.EMPTY))
                .put("advice", new ArrayValue(List.of(Value.of("notify")), ValueMetadata.EMPTY))
                .put("resource", Value.of(42)).build();

        val decision = AuthorizationDecision.of(decisionObj);

        assertThat(decision.decision()).isEqualTo(Decision.PERMIT);
        assertThat(decision.obligations()).containsExactly(Value.of("log"));
        assertThat(decision.advice()).containsExactly(Value.of("notify"));
        assertThat(decision.resource()).isEqualTo(Value.of(42));
    }

    @Test
    void of_shouldCreateDenyDecision() {
        val decisionObj = ObjectValue.builder().put("decision", new TextValue("DENY", ValueMetadata.EMPTY))
                .put("obligations", new ArrayValue(List.of(), ValueMetadata.EMPTY))
                .put("advice", new ArrayValue(List.of(), ValueMetadata.EMPTY)).put("resource", Value.UNDEFINED).build();

        val decision = AuthorizationDecision.of(decisionObj);

        assertThat(decision.decision()).isEqualTo(Decision.DENY);
        assertThat(decision.obligations()).isEmpty();
        assertThat(decision.advice()).isEmpty();
        assertThat(decision.resource()).isEqualTo(Value.UNDEFINED);
    }

    @Test
    void of_shouldCreateIndeterminateDecision() {
        val decisionObj = ObjectValue.builder().put("decision", new TextValue("INDETERMINATE", ValueMetadata.EMPTY))
                .put("obligations", new ArrayValue(List.of(), ValueMetadata.EMPTY))
                .put("advice", new ArrayValue(List.of(), ValueMetadata.EMPTY)).put("resource", Value.UNDEFINED).build();

        val decision = AuthorizationDecision.of(decisionObj);

        assertThat(decision.decision()).isEqualTo(Decision.INDETERMINATE);
    }

    @Test
    void of_shouldCreateNotApplicableDecision() {
        val decisionObj = ObjectValue.builder().put("decision", new TextValue("NOT_APPLICABLE", ValueMetadata.EMPTY))
                .put("obligations", new ArrayValue(List.of(), ValueMetadata.EMPTY))
                .put("advice", new ArrayValue(List.of(), ValueMetadata.EMPTY)).put("resource", Value.UNDEFINED).build();

        val decision = AuthorizationDecision.of(decisionObj);

        assertThat(decision.decision()).isEqualTo(Decision.NOT_APPLICABLE);
    }

    @Test
    void of_shouldThrowWhenNotObjectValue() {
        val invalidValue = Value.of("not an object");

        assertThatThrownBy(() -> AuthorizationDecision.of(invalidValue)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be an ObjectValue");
    }

    @Test
    void of_shouldThrowWhenDecisionFieldNotTextValue() {
        val invalidDecisionObj = ObjectValue.builder().put("decision", Value.of(123))
                .put("obligations", new ArrayValue(List.of(), ValueMetadata.EMPTY))
                .put("advice", new ArrayValue(List.of(), ValueMetadata.EMPTY)).put("resource", Value.UNDEFINED).build();

        assertThatThrownBy(() -> AuthorizationDecision.of(invalidDecisionObj))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Decision field must be a TextValue");
    }

    @Test
    void of_shouldThrowWhenObligationsNotArrayValue() {
        val invalidDecisionObj = ObjectValue.builder().put("decision", new TextValue("PERMIT", ValueMetadata.EMPTY))
                .put("obligations", Value.of("not an array"))
                .put("advice", new ArrayValue(List.of(), ValueMetadata.EMPTY)).put("resource", Value.UNDEFINED).build();

        assertThatThrownBy(() -> AuthorizationDecision.of(invalidDecisionObj))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Obligations field must be an ArrayValue");
    }

    @Test
    void of_shouldThrowWhenAdviceNotArrayValue() {
        val invalidDecisionObj = ObjectValue.builder().put("decision", new TextValue("PERMIT", ValueMetadata.EMPTY))
                .put("obligations", new ArrayValue(List.of(), ValueMetadata.EMPTY))
                .put("advice", Value.of("not an array")).put("resource", Value.UNDEFINED).build();

        assertThatThrownBy(() -> AuthorizationDecision.of(invalidDecisionObj))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Advice field must be an ArrayValue");
    }

    @Test
    void of_shouldDefaultToUndefinedWhenResourceMissing() {
        val decisionObj = ObjectValue.builder().put("decision", new TextValue("PERMIT", ValueMetadata.EMPTY))
                .put("obligations", new ArrayValue(List.of(), ValueMetadata.EMPTY))
                .put("advice", new ArrayValue(List.of(), ValueMetadata.EMPTY)).build();

        val decision = AuthorizationDecision.of(decisionObj);

        assertThat(decision.resource()).isEqualTo(Value.UNDEFINED);
    }

    @Test
    void of_shouldDefaultToEmptyArrayWhenObligationsMissing() {
        val decisionObj = ObjectValue.builder().put("decision", new TextValue("PERMIT", ValueMetadata.EMPTY))
                .put("advice", new ArrayValue(List.of(), ValueMetadata.EMPTY)).put("resource", Value.UNDEFINED).build();

        val decision = AuthorizationDecision.of(decisionObj);

        assertThat(decision.obligations()).isEmpty();
    }

    @Test
    void of_shouldDefaultToEmptyArrayWhenAdviceMissing() {
        val decisionObj = ObjectValue.builder().put("decision", new TextValue("PERMIT", ValueMetadata.EMPTY))
                .put("obligations", new ArrayValue(List.of(), ValueMetadata.EMPTY)).put("resource", Value.UNDEFINED)
                .build();

        val decision = AuthorizationDecision.of(decisionObj);

        assertThat(decision.advice()).isEmpty();
    }

    @Test
    void of_shouldDefaultAllMissingFields() {
        val decisionObj = ObjectValue.builder().put("decision", new TextValue("DENY", ValueMetadata.EMPTY)).build();

        val decision = AuthorizationDecision.of(decisionObj);

        assertThat(decision.decision()).isEqualTo(Decision.DENY);
        assertThat(decision.obligations()).isEmpty();
        assertThat(decision.advice()).isEmpty();
        assertThat(decision.resource()).isEqualTo(Value.UNDEFINED);
    }

    @Test
    void of_shouldHandleMultipleObligationsAndAdvice() {
        val decisionObj = ObjectValue.builder().put("decision", new TextValue("PERMIT", ValueMetadata.EMPTY))
                .put("obligations",
                        new ArrayValue(List.of(Value.of("obl1"), Value.of("obl2"), Value.of("obl3")),
                                ValueMetadata.EMPTY))
                .put("advice", new ArrayValue(List.of(Value.of("adv1"), Value.of("adv2")), ValueMetadata.EMPTY))
                .put("resource", Value.of("transformed")).build();

        val decision = AuthorizationDecision.of(decisionObj);

        assertThat(decision.obligations()).hasSize(3);
        assertThat(decision.advice()).hasSize(2);
        assertThat(decision.resource()).isEqualTo(Value.of("transformed"));
    }

}
