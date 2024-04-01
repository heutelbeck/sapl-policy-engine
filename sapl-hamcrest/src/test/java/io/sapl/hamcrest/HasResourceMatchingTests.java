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
package io.sapl.hamcrest;

import static io.sapl.hamcrest.Matchers.hasResourceMatching;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import java.util.function.Predicate;

import org.hamcrest.StringDescription;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;

class HasResourceMatchingTests {

    @Test
    void test() {
        Predicate<JsonNode> pred = (JsonNode jsonNode) -> jsonNode.has("foo");
        var                 sut  = hasResourceMatching(pred);

        ObjectMapper mapper   = new ObjectMapper();
        ObjectNode   resource = mapper.createObjectNode();
        resource.put("foo", "bar");
        AuthorizationDecision dec = new AuthorizationDecision(Decision.PERMIT, Optional.of(resource), Optional.empty(),
                Optional.empty());

        assertThat(dec, is(sut));
    }

    @Test
    void test_neg() {
        Predicate<JsonNode> pred = (JsonNode jsonNode) -> jsonNode.has("xxx");
        var                 sut  = hasResourceMatching(pred);

        ObjectMapper mapper   = new ObjectMapper();
        ObjectNode   resource = mapper.createObjectNode();
        resource.put("foo", "bar");
        AuthorizationDecision dec = new AuthorizationDecision(Decision.PERMIT, Optional.of(resource), Optional.empty(),
                Optional.empty());

        assertThat(dec, not(is(sut)));
    }

    @Test
    void test_nullDecision() {
        Predicate<JsonNode> pred = (JsonNode jsonNode) -> jsonNode.has("foo");
        var                 sut  = hasResourceMatching(pred);
        assertThat(null, not(is(sut)));
    }

    @Test
    void test_resourceEmpty() {
        Predicate<JsonNode>   pred = (JsonNode jsonNode) -> jsonNode.has("foo");
        var                   sut  = hasResourceMatching(pred);
        AuthorizationDecision dec  = new AuthorizationDecision(Decision.PERMIT, Optional.empty(), Optional.empty(),
                Optional.empty());
        assertThat(dec, not(is(sut)));
    }

    @Test
    void test_nullPredicate() {
        assertThrows(NullPointerException.class, () -> hasResourceMatching(null));
    }

    @Test
    void testDescriptionForMatcher() {
        Predicate<JsonNode>     pred        = (JsonNode jsonNode) -> jsonNode.has("foo");
        var                     sut         = hasResourceMatching(pred);
        final StringDescription description = new StringDescription();
        sut.describeTo(description);
        assertThat(description.toString(), is("the decision has a resource matching the predicate"));
    }

}
