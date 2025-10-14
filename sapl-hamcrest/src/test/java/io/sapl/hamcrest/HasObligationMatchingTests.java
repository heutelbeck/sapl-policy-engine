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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.sapl.api.pdp.AuthorizationDecision;
import org.hamcrest.StringDescription;
import org.junit.jupiter.api.Test;

import java.util.function.Predicate;

import static com.spotify.hamcrest.jackson.JsonMatchers.jsonObject;
import static com.spotify.hamcrest.jackson.JsonMatchers.jsonText;
import static io.sapl.hamcrest.Matchers.hasObligation;
import static io.sapl.hamcrest.Matchers.hasObligationMatching;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HasObligationMatchingTests {

    private static final ObjectMapper        MAPPER               = new ObjectMapper();
    private static final Predicate<JsonNode> FOO_IS_BAR_PREDICATE = x -> is(
            jsonObject().where("foo", is(jsonText("bar")))).matches(x);

    @Test
    void test() throws JsonProcessingException {
        final var obligations = MAPPER.readValue("[ {\"foo\" : \"bar\" }]", ArrayNode.class);
        final var decision    = AuthorizationDecision.PERMIT.withObligations(obligations);
        assertThat(decision, hasObligationMatching(FOO_IS_BAR_PREDICATE));
    }

    @Test
    void testConvenienceMatcherObligationString() throws JsonProcessingException {
        final var obligations = MAPPER.readValue("[ {\"foo\" : \"bar\" }, \"food\"]", ArrayNode.class);
        final var decision    = AuthorizationDecision.PERMIT.withObligations(obligations);
        assertThat(decision, hasObligation("food"));
    }

    @Test
    void test_neg() throws JsonProcessingException {
        final var obligations = MAPPER.readValue("[ {\"xxx\" : \"bar\" }]", ArrayNode.class);
        final var decision    = AuthorizationDecision.PERMIT.withObligations(obligations);
        assertThat(decision, not(hasObligationMatching(FOO_IS_BAR_PREDICATE)));
    }

    @Test
    void test_nullDecision() {
        assertThat(null, not(hasObligationMatching(FOO_IS_BAR_PREDICATE)));
    }

    @Test
    void test_nullPredicate() {
        assertThrows(NullPointerException.class, () -> Matchers.hasObligationMatching(null));
    }

    @Test
    void test_emptyObligation() {
        assertThat(AuthorizationDecision.PERMIT, not(hasObligationMatching(FOO_IS_BAR_PREDICATE)));
    }

    @Test
    void testDescriptionForMatcher() {
        final var sut         = hasObligationMatching(FOO_IS_BAR_PREDICATE);
        final var description = new StringDescription();
        sut.describeTo(description);
        assertThat(description.toString(), is("the decision has an obligation matching the predicate"));
    }

}
