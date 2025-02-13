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
import static io.sapl.hamcrest.Matchers.hasResource;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;

import org.hamcrest.StringDescription;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;

class HasResourceTests {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void test() {
        final var resource = MAPPER.createObjectNode();
        resource.put("foo", "bar");
        final var decision         = new AuthorizationDecision(Decision.PERMIT, Optional.of(resource), Optional.empty(),
                Optional.empty());
        final var matcherUnderTest = hasResource(resource);
        assertThat(decision, is(matcherUnderTest));
    }

    @Test
    void testEmptyMatcher() {
        final var resource = MAPPER.createObjectNode();
        resource.put("foo", "bar");
        final var decision         = new AuthorizationDecision(Decision.PERMIT, Optional.of(resource), Optional.empty(),
                Optional.empty());
        final var matcherUnderTest = hasResource();
        assertThat(decision, is(matcherUnderTest));
    }

    @Test
    void test_neg() {
        final var expectedResource = MAPPER.createObjectNode();
        expectedResource.put("foo", "bar");
        final var actualResource = MAPPER.createObjectNode();
        actualResource.put("xxx", "yyy");
        final var decision         = new AuthorizationDecision(Decision.PERMIT, Optional.of(actualResource),
                Optional.empty(), Optional.empty());
        final var matcherUnderTest = hasResource(expectedResource);
        assertThat(decision, not(is(matcherUnderTest)));
    }

    @Test
    void test_nullDecision() {
        final var resource = MAPPER.createObjectNode();
        resource.put("foo", "bar");
        final var matcherUnderTest = hasResource(resource);
        assertThat(null, not(is(matcherUnderTest)));
    }

    @Test
    void test_emptyResource() {
        final var resource = MAPPER.createObjectNode();
        resource.put("foo", "bar");
        final var decision         = new AuthorizationDecision(Decision.PERMIT, Optional.empty(), Optional.empty(),
                Optional.empty());
        final var matcherUnderTest = hasResource(resource);
        assertThat(decision, not(is(matcherUnderTest)));
    }

    @Test
    void test_nullJsonNode() {
        assertThrows(NullPointerException.class, () -> hasResource((ObjectNode) null));
    }

    @Test
    void testDescriptionEmptyMatcher() {
        final var matcherUnderTest = hasResource();
        final var description      = new StringDescription();
        matcherUnderTest.describeTo(description);
        assertThat(description.toString(), is("a resource with any JsonNode"));
    }

    @Test
    void testDescriptionMatcher() {
        final var matcherUnderTest = hasResource(jsonText("value"));
        final var description      = new StringDescription();
        matcherUnderTest.describeTo(description);
        assertThat(description.toString(), is("a resource with a text node with value that is \"value\""));
    }

}
