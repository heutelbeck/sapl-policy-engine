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
import org.hamcrest.Matcher;
import org.hamcrest.StringDescription;
import org.junit.jupiter.api.Test;

import static com.spotify.hamcrest.jackson.JsonMatchers.jsonText;
import static io.sapl.hamcrest.Matchers.hasAdviceContainingKeyValue;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HasAdviceContainingKeyValueTests {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void test() throws JsonProcessingException {
        final var advice   = MAPPER.readValue("[ {\"foo\" : \"bar\", \"key\" : \"value\"  }]", ArrayNode.class);
        final var decision = AuthorizationDecision.PERMIT.withAdvice(advice);
        assertThat(decision, hasAdviceContainingKeyValue("key", jsonText("value")));
    }

    @Test
    void test_neg() throws JsonProcessingException {
        final var advice   = MAPPER.readValue("[ {\"foo\" : \"bar\", \"key\" : \"value\"  }]", ArrayNode.class);
        final var decision = AuthorizationDecision.PERMIT.withAdvice(advice);
        assertThat(decision, not(hasAdviceContainingKeyValue("xxx", jsonText("yyy"))));
    }

    @Test
    void test_nullDecision() {
        assertThat(null, not(hasAdviceContainingKeyValue("key", jsonText("value"))));
    }

    @Test
    void test_nullKey() {
        final var text = jsonText("value");
        assertThrows(NullPointerException.class, () -> hasAdviceContainingKeyValue(null, text));
    }

    @Test
    void test_nullValue() {
        assertThrows(NullPointerException.class, () -> hasAdviceContainingKeyValue("key", (Matcher<JsonNode>) null));
    }

    @Test
    void test_emptyAdvice() {
        assertThat(AuthorizationDecision.PERMIT, not(hasAdviceContainingKeyValue("key", jsonText("value"))));
    }

    @Test
    void test_emptyMatcher() throws JsonProcessingException {
        final var advice   = MAPPER.readValue("[ {\"foo\" : \"bar\", \"key\" : \"value\"  }]", ArrayNode.class);
        final var decision = AuthorizationDecision.PERMIT.withAdvice(advice);
        assertThat(decision, hasAdviceContainingKeyValue("key"));
    }

    @Test
    void test_StringValueMatcher() throws JsonProcessingException {
        final var advice   = MAPPER.readValue("[ {\"foo\" : \"bar\", \"key\" : \"value\"  }]", ArrayNode.class);
        final var decision = AuthorizationDecision.PERMIT.withAdvice(advice);
        assertThat(decision, hasAdviceContainingKeyValue("key", "value"));
    }

    @Test
    void test_MatchingKey_NotMatchingValue() throws JsonProcessingException {
        final var advice   = MAPPER.readValue("[ {\"foo\" : \"bar\", \"key\" : \"value\"  }]", ArrayNode.class);
        final var decision = AuthorizationDecision.PERMIT.withAdvice(advice);
        assertThat(decision, not(hasAdviceContainingKeyValue("key", "xxx")));
    }

    @Test
    void testDescriptionForMatcherEmptyMatcher() {
        final var sut         = Matchers.hasAdviceContainingKeyValue("key");
        final var description = new StringDescription();
        sut.describeTo(description);
        assertThat(description.toString(), is("the decision has an advice containing key key with any value"));
    }

    @Test
    void testDescriptionForMatcher() {
        final var sut         = Matchers.hasAdviceContainingKeyValue("key", jsonText("value"));
        final var description = new StringDescription();
        sut.describeTo(description);
        assertThat(description.toString(),
                is("the decision has an advice containing key key with a text node with value that is \"value\""));
    }

}
