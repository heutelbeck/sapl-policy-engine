/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

class AuthorizationDecisionTests {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    @Test
    void defaultConstructorResultsInNoEntriesAndIndeterminate() {
        var sa       = new SoftAssertions();
        var decision = new AuthorizationDecision();
        sa.assertThat(decision.getDecision()).isEqualTo(Decision.INDETERMINATE);
        sa.assertThat(decision.getAdvice()).isEmpty();
        sa.assertThat(decision.getObligations()).isEmpty();
        sa.assertThat(decision.getResource()).isEmpty();
        sa.assertAll();
    }

    @Test
    void decisionConstructorResultsInNoEntries() {
        var sa       = new SoftAssertions();
        var decision = new AuthorizationDecision(Decision.DENY);
        sa.assertThat(decision.getDecision()).isEqualTo(Decision.DENY);
        sa.assertThat(decision.getAdvice()).isEmpty();
        sa.assertThat(decision.getObligations()).isEmpty();
        sa.assertThat(decision.getResource()).isEmpty();
        sa.assertAll();
    }

    @Test
    void decisionConstructorNull() {
        assertThatThrownBy(() -> new AuthorizationDecision(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void withAdviceNull() {
        var decision = new AuthorizationDecision();
        assertThatThrownBy(() -> decision.withAdvice(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void withDecisionNull() {
        var decision = new AuthorizationDecision();
        assertThatThrownBy(() -> decision.withDecision(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void withAdviceEmpty() {
        var advice   = JSON.arrayNode();
        var decision = new AuthorizationDecision().withAdvice(advice);
        assertThat(decision.getAdvice()).isEmpty();
    }

    @Test
    void withAdvicePresent() {
        var advice = JSON.arrayNode();
        advice.add(JSON.numberNode(0));
        var decision = new AuthorizationDecision().withAdvice(advice);
        assertThatJson(decision.getAdvice().get()).isArray().containsAnyOf(0);
    }

    @Test
    void withObligationsEmpty() {
        var obligations = JSON.arrayNode();
        var decision    = new AuthorizationDecision().withObligations(obligations);
        assertThat(decision.getObligations()).isEmpty();
    }

    @Test
    void withObligationsNull() {
        var decision = new AuthorizationDecision();
        assertThatThrownBy(() -> decision.withObligations(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void withObligationsPresent() {
        var obligations = JSON.arrayNode();
        obligations.add(JSON.numberNode(0));
        var decision = new AuthorizationDecision().withObligations(obligations);
        assertThatJson(decision.getObligations().get()).isArray().containsAnyOf(0);
    }

    @Test
    void withResourceNull() {
        var decision = new AuthorizationDecision();
        assertThatThrownBy(() -> decision.withResource(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void withResource() {
        var decision = new AuthorizationDecision().withResource(JSON.numberNode(0));
        assertThatJson(decision.getResource().get()).isPresent().isEqualTo(0);
    }

    @Test
    void withDecision() {
        var decision = AuthorizationDecision.DENY.withDecision(Decision.PERMIT);
        assertThat(decision.getDecision()).isEqualTo(Decision.PERMIT);
    }

}
