/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;

import org.hamcrest.Matcher;
import org.hamcrest.StringDescription;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;

class HasObligationContainingKeyValueTests {

    @Test
    void test() {
        ObjectMapper mapper     = new ObjectMapper();
        ObjectNode   obligation = mapper.createObjectNode();
        obligation.put("foo", "bar");
        obligation.put("key", "value");
        ArrayNode obligations = mapper.createArrayNode();
        obligations.add(obligation);

        AuthorizationDecision dec = new AuthorizationDecision(Decision.PERMIT, Optional.empty(),
                Optional.of(obligations), Optional.empty());

        var sut = Matchers.hasObligationContainingKeyValue("key", jsonText("value"));

        assertThat(dec, is(sut));
    }

    @Test
    void test_neg() {
        ObjectMapper mapper     = new ObjectMapper();
        ObjectNode   obligation = mapper.createObjectNode();
        obligation.put("foo", "bar");
        obligation.put("key", "value");
        ArrayNode obligations = mapper.createArrayNode();
        obligations.add(obligation);

        AuthorizationDecision dec = new AuthorizationDecision(Decision.PERMIT, Optional.empty(),
                Optional.of(obligations), Optional.empty());

        var sut = Matchers.hasObligationContainingKeyValue("xxx", jsonText("yyy"));

        assertThat(dec, not(is(sut)));
    }

    @Test
    void test_nullDecision() {
        var sut = Matchers.hasObligationContainingKeyValue("key", jsonText("value"));

        assertThat(null, not(is(sut)));
    }

    @Test
    void test_nullKey() {
        var text = jsonText("value");
        assertThrows(NullPointerException.class, () -> Matchers.hasObligationContainingKeyValue(null, text));
    }

    @Test
    void test_nullValue() {
        assertThrows(NullPointerException.class,
                () -> Matchers.hasObligationContainingKeyValue("key", (Matcher<JsonNode>) null));
    }

    @Test
    void test_emptyObligation() {
        AuthorizationDecision dec = new AuthorizationDecision(Decision.PERMIT, Optional.empty(), Optional.empty(),
                Optional.empty());

        var sut = Matchers.hasObligationContainingKeyValue("key", jsonText("value"));

        assertThat(dec, not(is(sut)));
    }

    @Test
    void test_emptyMatcher() {
        ObjectMapper mapper           = new ObjectMapper();
        ObjectNode   actualObligation = mapper.createObjectNode();
        actualObligation.put("foo", "bar");
        actualObligation.put("key", "value");
        ArrayNode actualObligations = mapper.createArrayNode();
        actualObligations.add(actualObligation);
        AuthorizationDecision dec = new AuthorizationDecision(Decision.PERMIT, null, Optional.of(actualObligations),
                null);

        var sut = Matchers.hasObligationContainingKeyValue("key");

        assertThat(dec, is(sut));
    }

    @Test
    void test_StringValueMatcher() {
        ObjectMapper mapper           = new ObjectMapper();
        ObjectNode   actualObligation = mapper.createObjectNode();
        actualObligation.put("foo", "bar");
        actualObligation.put("key", "value");
        ArrayNode actualObligations = mapper.createArrayNode();
        actualObligations.add(actualObligation);
        AuthorizationDecision dec = new AuthorizationDecision(Decision.PERMIT, null, Optional.of(actualObligations),
                null);

        var sut = Matchers.hasObligationContainingKeyValue("key", "value");

        assertThat(dec, is(sut));
    }

    @Test
    void test_MatchingKey_NotMatchingValue() {
        ObjectMapper mapper           = new ObjectMapper();
        ObjectNode   actualObligation = mapper.createObjectNode();
        actualObligation.put("foo", "bar");
        actualObligation.put("key", "value");
        ArrayNode actualObligations = mapper.createArrayNode();
        actualObligations.add(actualObligation);
        AuthorizationDecision dec = new AuthorizationDecision(Decision.PERMIT, null, Optional.of(actualObligations),
                null);

        var sut = Matchers.hasObligationContainingKeyValue("key", "xxx");

        assertThat(dec, not(is(sut)));
    }

    @Test
    void testDescriptionForMatcherEmptyMatcher() {
        var                     sut         = Matchers.hasObligationContainingKeyValue("key");
        final StringDescription description = new StringDescription();
        sut.describeTo(description);
        assertThat(description.toString(), is("the decision has an obligation containing key key with any value"));
    }

    @Test
    void testDescriptionForMatcher() {
        var                     sut         = Matchers.hasObligationContainingKeyValue("key", jsonText("value"));
        final StringDescription description = new StringDescription();
        sut.describeTo(description);
        assertThat(description.toString(),
                is("the decision has an obligation containing key key with a text node with value that is \"value\""));
    }

}
