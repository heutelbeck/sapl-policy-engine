/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
        var advice = JSON.objectNode();
        advice.put("foo", "bar");
        var adviceArray = JSON.arrayNode();
        adviceArray.add(advice);
        var decision = new AuthorizationDecision(Decision.PERMIT, Optional.empty(), Optional.empty(),
                Optional.of(adviceArray));

        assertThat(decision, hasAdvice(advice));
    }

    @Test
    void testConvenienceMatcherAdviceString() {
        var advice = JSON.objectNode();
        advice.put("foo", "bar");
        var adviceArray = JSON.arrayNode();
        adviceArray.add(advice);
        adviceArray.add(JSON.textNode("food"));
        var decision = new AuthorizationDecision(Decision.PERMIT, Optional.empty(), Optional.empty(),
                Optional.of(adviceArray));

        assertThat(decision, hasAdvice("food"));
    }

    @Test
    void test_neg() {
        var advice = JSON.objectNode();
        advice.put("foo", "bar");
        var adviceArray = JSON.arrayNode();
        adviceArray.add(advice);

        var expectedAdvice = JSON.objectNode();
        expectedAdvice.put("xxx", "xxx");
        var decision = new AuthorizationDecision(Decision.PERMIT, Optional.empty(), Optional.empty(),
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
        var advice = JSON.objectNode();
        advice.put("foo", "bar");
        var decision = new AuthorizationDecision(Decision.PERMIT, Optional.empty(), Optional.empty(), Optional.empty());
        assertThat(decision, not(hasAdvice(advice)));
    }

    @Test
    void test_numberEqualTest() {
        var expectedAdvice = JSON.objectNode();
        expectedAdvice.put("foo", 1);
        var actualAdvice = JSON.objectNode();
        actualAdvice.put("foo", 1f);
        var actualAdviceArray = JSON.arrayNode();
        actualAdviceArray.add(actualAdvice);
        var decision = new AuthorizationDecision(Decision.PERMIT, Optional.empty(), Optional.empty(),
                Optional.of(actualAdviceArray));

        assertThat(decision, hasAdvice(expectedAdvice));
    }

    @Test
    void test_emptyMatcher() {
        var actualAdvice = JSON.objectNode();
        actualAdvice.put("foo", 1);
        var actualAdviceArray = JSON.arrayNode();
        actualAdviceArray.add(actualAdvice);
        var decision = new AuthorizationDecision(Decision.PERMIT, Optional.empty(), Optional.empty(),
                Optional.of(actualAdviceArray));

        assertThat(decision, new HasAdvice());
    }

    @Test
    void test_nullJsonNode() {
        assertThrows(NullPointerException.class, () -> hasAdvice((ObjectNode) null));
    }

    @Test
    void testDescriptionForMatcherEmptyMatcher() {
        var sut         = new HasAdvice();
        var description = new StringDescription();
        sut.describeTo(description);
        assertThat(description.toString(), is("the decision has an advice equals any advice"));
    }

    @Test
    void testDescriptionForMatcher() {
        var sut         = hasAdvice(jsonText("value"));
        var description = new StringDescription();
        sut.describeTo(description);
        assertThat(description.toString(),
                is("the decision has an advice equals a text node with value that is \"value\""));
    }

}
