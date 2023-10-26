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
package io.sapl.hamcrest;

import static com.spotify.hamcrest.jackson.JsonMatchers.jsonText;
import static io.sapl.hamcrest.Matchers.hasObligation;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;

import org.hamcrest.StringDescription;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;

class HasObligationTests {

    @Test
    void test() {
        ObjectMapper mapper     = new ObjectMapper();
        ObjectNode   obligation = mapper.createObjectNode();
        obligation.put("foo", "bar");
        ArrayNode obligations = mapper.createArrayNode();
        obligations.add(obligation);
        AuthorizationDecision dec = new AuthorizationDecision(Decision.PERMIT, Optional.empty(),
                Optional.of(obligations), Optional.empty());

        var sut = hasObligation(obligation);

        assertThat(dec, is(sut));
    }

    @Test
    void test_neg() {
        ObjectMapper mapper     = new ObjectMapper();
        ObjectNode   obligation = mapper.createObjectNode();
        obligation.put("foo", "bar");
        ArrayNode obligations = mapper.createArrayNode();
        obligations.add(obligation);

        ObjectNode expectedObligation = mapper.createObjectNode();
        expectedObligation.put("xxx", "xxx");
        AuthorizationDecision dec = new AuthorizationDecision(Decision.PERMIT, Optional.empty(),
                Optional.of(obligations), Optional.empty());

        var sut = hasObligation(expectedObligation);

        assertThat(dec, not(is(sut)));
    }

    @Test
    void test_nullDecision() {
        ObjectMapper mapper     = new ObjectMapper();
        ObjectNode   obligation = mapper.createObjectNode();
        obligation.put("foo", "bar");
        var sut = hasObligation(obligation);
        assertThat(null, not(is(sut)));
    }

    @Test
    void test_emptyObligation() {
        ObjectMapper mapper     = new ObjectMapper();
        ObjectNode   obligation = mapper.createObjectNode();
        obligation.put("foo", "bar");
        var                   sut = hasObligation(obligation);
        AuthorizationDecision dec = new AuthorizationDecision(Decision.PERMIT, Optional.empty(), Optional.empty(),
                Optional.empty());
        assertThat(dec, not(is(sut)));
    }

    @Test
    void test_numberEqualTest() {
        ObjectMapper mapper             = new ObjectMapper();
        ObjectNode   expectedObligation = mapper.createObjectNode();
        expectedObligation.put("foo", 1);
        ObjectNode actualObligation = mapper.createObjectNode();
        actualObligation.put("foo", 1f);
        ArrayNode actualObligations = mapper.createArrayNode();
        actualObligations.add(actualObligation);
        AuthorizationDecision dec = new AuthorizationDecision(Decision.PERMIT, Optional.empty(),
                Optional.of(actualObligations), Optional.empty());

        var sut = hasObligation(expectedObligation);

        assertThat(dec, is(sut));
    }

    @Test
    void test_emptyMatcher() {
        ObjectMapper mapper           = new ObjectMapper();
        ObjectNode   actualObligation = mapper.createObjectNode();
        actualObligation.put("foo", 1);
        ArrayNode actualObligations = mapper.createArrayNode();
        actualObligations.add(actualObligation);
        AuthorizationDecision dec = new AuthorizationDecision(Decision.PERMIT, null, Optional.of(actualObligations),
                null);

        var sut = new HasObligation();

        assertThat(dec, is(sut));
    }

    @Test
    void test_nullJsonNode() {
        assertThrows(NullPointerException.class, () -> hasObligation((ObjectNode) null));
    }

    @Test
    void testDescriptionForEmptyMatcher() {
        var                     sut         = new HasObligation();
        final StringDescription description = new StringDescription();
        sut.describeTo(description);
        assertThat(description.toString(), is("the decision has an obligation equals any obligation"));
    }

    @Test
    void testDescriptionForMatcher() {
        var                     sut         = hasObligation(jsonText("value"));
        final StringDescription description = new StringDescription();
        sut.describeTo(description);
        assertThat(description.toString(),
                is("the decision has an obligation equals a text node with value that is \"value\""));
    }

}
