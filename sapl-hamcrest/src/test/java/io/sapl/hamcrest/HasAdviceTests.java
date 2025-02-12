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
package io.sapl.hamcrest;

import static com.spotify.hamcrest.jackson.JsonMatchers.jsonText;
import static io.sapl.hamcrest.Matchers.hasAdvice;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;

import org.hamcrest.StringDescription;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;

class HasAdviceTests {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    @Test
    void test() {
        final var advice = JSON.objectNode();
        advice.put("foo", "bar");
        final var adviceArray = JSON.arrayNode();
        adviceArray.add(advice);
        final var decision = new AuthorizationDecision(Decision.PERMIT, Optional.empty(), Optional.empty(),
                Optional.of(adviceArray));

        assertThat(decision, hasAdvice(advice));
    }

    @Test
    void testConvenienceMatcherAdviceString() {
        final var advice = JSON.objectNode();
        advice.put("foo", "bar");
        final var adviceArray = JSON.arrayNode();
        adviceArray.add(advice);
        adviceArray.add(JSON.textNode("food"));
        final var decision = new AuthorizationDecision(Decision.PERMIT, Optional.empty(), Optional.empty(),
                Optional.of(adviceArray));

        assertThat(decision, hasAdvice("food"));
    }

    @Test
    void test_neg() {
        final var advice = JSON.objectNode();
        advice.put("foo", "bar");
        final var adviceArray = JSON.arrayNode();
        adviceArray.add(advice);

        final var expectedAdvice = JSON.objectNode();
        expectedAdvice.put("xxx", "xxx");
        final var decision = new AuthorizationDecision(Decision.PERMIT, Optional.empty(), Optional.empty(),
                Optional.of(adviceArray));

        assertThat(decision, not(is(hasAdvice(expectedAdvice))));
    }

    @Test
    void test_nullDecision() {
        ObjectNode advice = JSON.objectNode();
        advice.put("foo", "bar");
        assertThat(null, not(hasAdvice(advice)));
    }

    @Test
    void test_emptyAdvice() {
        final var advice = JSON.objectNode();
        advice.put("foo", "bar");
        final var decision = new AuthorizationDecision(Decision.PERMIT, Optional.empty(), Optional.empty(),
                Optional.empty());
        assertThat(decision, not(hasAdvice(advice)));
    }

    @Test
    void test_numberEqualTest() {
        final var expectedAdvice = JSON.objectNode();
        expectedAdvice.put("foo", 1);
        final var actualAdvice = JSON.objectNode();
        actualAdvice.put("foo", 1f);
        final var actualAdviceArray = JSON.arrayNode();
        actualAdviceArray.add(actualAdvice);
        final var decision = new AuthorizationDecision(Decision.PERMIT, Optional.empty(), Optional.empty(),
                Optional.of(actualAdviceArray));

        assertThat(decision, hasAdvice(expectedAdvice));
    }

    @Test
    void test_emptyMatcher() {
        final var actualAdvice = JSON.objectNode();
        actualAdvice.put("foo", 1);
        final var actualAdviceArray = JSON.arrayNode();
        actualAdviceArray.add(actualAdvice);
        final var decision = new AuthorizationDecision(Decision.PERMIT, Optional.empty(), Optional.empty(),
                Optional.of(actualAdviceArray));

        assertThat(decision, new HasAdvice());
    }

    @Test
    void test_nullJsonNode() {
        assertThrows(NullPointerException.class, () -> hasAdvice((ObjectNode) null));
    }

    @Test
    void testDescriptionForMatcherEmptyMatcher() {
        final var sut         = new HasAdvice();
        final var description = new StringDescription();
        sut.describeTo(description);
        assertThat(description.toString(), is("the decision has an advice equals any advice"));
    }

    @Test
    void testDescriptionForMatcher() {
        final var sut         = hasAdvice(jsonText("value"));
        final var description = new StringDescription();
        sut.describeTo(description);
        assertThat(description.toString(),
                is("the decision has an advice equals a text node with value that is \"value\""));
    }

}
