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
package io.sapl.api.model.jackson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import io.sapl.api.pdp.IdentifiableAuthorizationSubscription;
import io.sapl.api.pdp.MultiAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationSubscription;
import lombok.val;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class SaplJacksonModuleTests {

    private static ObjectMapper mapper;

    @BeforeAll
    static void setup() {
        mapper = new ObjectMapper();
        mapper.registerModule(new SaplJacksonModule());
    }

    @ParameterizedTest
    @MethodSource("valueSerializationCases")
    void whenSerializingValue_thenProducesExpectedJson(Value value, String expectedJson)
            throws JsonProcessingException {
        val json = mapper.writeValueAsString(value);
        assertThat(json).isEqualTo(expectedJson);
    }

    static Stream<Arguments> valueSerializationCases() {
        return Stream.of(arguments(Value.NULL, "null"), arguments(Value.TRUE, "true"), arguments(Value.FALSE, "false"),
                arguments(Value.of(42), "42"), arguments(Value.of(3.14), "3.14"),
                arguments(Value.of("eldritch"), "\"eldritch\""), arguments(Value.EMPTY_ARRAY, "[]"),
                arguments(Value.EMPTY_OBJECT, "{}"),
                arguments(Value.ofArray(Value.of(1), Value.of(2), Value.of(3)), "[1,2,3]"),
                arguments(Value.ofObject(Map.of("cultist", Value.of("Wilbur"))), "{\"cultist\":\"Wilbur\"}"));
    }

    @ParameterizedTest
    @MethodSource("valueDeserializationCases")
    void whenDeserializingJson_thenProducesExpectedValue(String json, Value expectedValue)
            throws JsonProcessingException {
        val value = mapper.readValue(json, Value.class);
        assertThat(value).isEqualTo(expectedValue);
    }

    static Stream<Arguments> valueDeserializationCases() {
        return Stream.of(arguments("null", Value.NULL), arguments("true", Value.TRUE), arguments("false", Value.FALSE),
                arguments("42", Value.of(42)), arguments("3.14", Value.of(3.14)),
                arguments("\"necronomicon\"", Value.of("necronomicon")), arguments("[]", Value.EMPTY_ARRAY),
                arguments("{}", Value.EMPTY_OBJECT),
                arguments("[1,2,3]", Value.ofArray(Value.of(1), Value.of(2), Value.of(3))),
                arguments("{\"tome\":\"forbidden\"}", Value.ofObject(Map.of("tome", Value.of("forbidden")))));
    }

    @Test
    void whenSerializingUndefinedValue_thenThrowsException() {
        val undefined = Value.UNDEFINED;
        assertThatThrownBy(() -> mapper.writeValueAsString(undefined))
                .hasCauseInstanceOf(IllegalArgumentException.class).hasMessageContaining("UndefinedValue");
    }

    @Test
    void whenSerializingErrorValue_thenThrowsException() {
        val error = Value.error("The stars are not right.");
        assertThatThrownBy(() -> mapper.writeValueAsString(error)).hasCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ErrorValue");
    }

    @Test
    void whenSerializingArrayWithUndefined_thenUndefinedIsSkipped() throws JsonProcessingException {
        val array = Value.ofArray(Value.of("visible"), Value.UNDEFINED, Value.of("also visible"));
        val json  = mapper.writeValueAsString(array);
        assertThat(json).isEqualTo("[\"visible\",\"also visible\"]");
    }

    @Test
    void whenSerializingObjectWithUndefined_thenUndefinedIsSkipped() throws JsonProcessingException {
        val object = Value.ofObject(
                Map.of("name", Value.of("Cthulhu"), "location", Value.UNDEFINED, "status", Value.of("dreaming")));
        val json   = mapper.writeValueAsString(object);
        assertThat(json).doesNotContain("location").contains("name").contains("status");
    }

    @Test
    void whenRoundTrippingComplexValue_thenValueIsPreserved() throws JsonProcessingException {
        val original = Value.ofObject(Map.of("investigator", Value.of("Herbert West"), "experiments",
                Value.ofArray(Value.of("reanimation"), Value.of("serum")), "success", Value.FALSE, "attempts",
                Value.of(17)));
        val json     = mapper.writeValueAsString(original);
        val restored = mapper.readValue(json, Value.class);
        assertThat(restored).isEqualTo(original);
    }

    @Test
    void whenSerializingSimplePermitDecision_thenOnlyDecisionFieldIncluded() throws JsonProcessingException {
        val json = mapper.writeValueAsString(AuthorizationDecision.PERMIT);
        assertThat(json).isEqualTo("{\"decision\":\"PERMIT\"}");
    }

    @Test
    void whenSerializingSimpleDenyDecision_thenOnlyDecisionFieldIncluded() throws JsonProcessingException {
        val json = mapper.writeValueAsString(AuthorizationDecision.DENY);
        assertThat(json).isEqualTo("{\"decision\":\"DENY\"}");
    }

    @Test
    void whenSerializingDecisionWithObligations_thenObligationsIncluded() throws JsonProcessingException {
        val obligation = Value.ofObject(Map.of("type", Value.of("log"), "message", Value.of("Access granted")));
        val decision   = new AuthorizationDecision(Decision.PERMIT, List.of(obligation), List.of(), Value.UNDEFINED);
        val json       = mapper.writeValueAsString(decision);

        assertThat(json).contains("\"decision\":\"PERMIT\"").contains("\"obligations\"").contains("\"type\":\"log\"")
                .doesNotContain("\"advice\"").doesNotContain("\"resource\"");
    }

    @Test
    void whenSerializingDecisionWithAdvice_thenAdviceIncluded() throws JsonProcessingException {
        val advice   = Value.ofObject(Map.of("recommendation", Value.of("Consider two-factor authentication")));
        val decision = new AuthorizationDecision(Decision.PERMIT, List.of(), List.of(advice), Value.UNDEFINED);
        val json     = mapper.writeValueAsString(decision);

        assertThat(json).contains("\"decision\":\"PERMIT\"").contains("\"advice\"").contains("\"recommendation\"")
                .doesNotContain("\"obligations\"").doesNotContain("\"resource\"");
    }

    @Test
    void whenSerializingDecisionWithResource_thenResourceIncluded() throws JsonProcessingException {
        val resource = Value.ofObject(Map.of("filtered", Value.TRUE, "originalSize", Value.of(100)));
        val decision = new AuthorizationDecision(Decision.PERMIT, List.of(), List.of(), resource);
        val json     = mapper.writeValueAsString(decision);

        assertThat(json).contains("\"decision\":\"PERMIT\"").contains("\"resource\"").contains("\"filtered\":true")
                .doesNotContain("\"obligations\"").doesNotContain("\"advice\"");
    }

    @Test
    void whenSerializingFullDecision_thenAllFieldsIncluded() throws JsonProcessingException {
        val obligation = Value.ofObject(Map.of("action", Value.of("audit")));
        val advice     = Value.ofObject(Map.of("hint", Value.of("Check permissions")));
        val resource   = Value.ofObject(Map.of("redacted", Value.TRUE));
        val decision   = new AuthorizationDecision(Decision.PERMIT, List.of(obligation), List.of(advice), resource);
        val json       = mapper.writeValueAsString(decision);

        assertThat(json).contains("\"decision\":\"PERMIT\"").contains("\"obligations\"").contains("\"advice\"")
                .contains("\"resource\"");
    }

    @Test
    void whenSerializingAuthorizationSubscription_thenAllFieldsSerialized() throws JsonProcessingException {
        val subscription = new AuthorizationSubscription(Value.of("investigator"), Value.of("read"),
                Value.of("necronomicon"), Value.ofObject(Map.of("location", Value.of("Miskatonic University"))));
        val json         = mapper.writeValueAsString(subscription);

        assertThat(json).contains("\"subject\":\"investigator\"").contains("\"action\":\"read\"")
                .contains("\"resource\":\"necronomicon\"").contains("\"environment\"");
    }

    @Test
    void whenDeserializingAuthorizationSubscription_thenAllFieldsRestored() throws JsonProcessingException {
        val json         = """
                {"subject":"cultist","action":"summon","resource":"shoggoth","environment":{"ritual":"complete"}}""";
        val subscription = mapper.readValue(json, AuthorizationSubscription.class);

        assertThat(subscription.subject()).isEqualTo(Value.of("cultist"));
        assertThat(subscription.action()).isEqualTo(Value.of("summon"));
        assertThat(subscription.resource()).isEqualTo(Value.of("shoggoth"));
        assertThat(subscription.environment()).isEqualTo(Value.ofObject(Map.of("ritual", Value.of("complete"))));
    }

    @Test
    void whenRoundTrippingAuthorizationSubscription_thenSubscriptionPreserved() throws JsonProcessingException {
        val original = new AuthorizationSubscription(
                Value.ofObject(Map.of("name", Value.of("Randolph Carter"), "role", Value.of("dreamer"))),
                Value.of("enter"), Value.of("dreamlands"), Value.UNDEFINED);
        val json     = mapper.writeValueAsString(original);
        val restored = mapper.readValue(json, AuthorizationSubscription.class);

        assertThat(restored.subject()).isEqualTo(original.subject());
        assertThat(restored.action()).isEqualTo(original.action());
        assertThat(restored.resource()).isEqualTo(original.resource());
    }

    @Test
    void whenRoundTrippingAuthorizationDecision_thenDecisionPreserved() throws JsonProcessingException {
        val obligation = Value.ofObject(Map.of("type", Value.of("notify"), "target", Value.of("security")));
        val advice     = Value.ofObject(Map.of("suggestion", Value.of("Review access logs")));
        val resource   = Value.ofObject(Map.of("sanitized", Value.TRUE));
        val original   = new AuthorizationDecision(Decision.PERMIT, List.of(obligation), List.of(advice), resource);

        val json     = mapper.writeValueAsString(original);
        val restored = mapper.readValue(json, AuthorizationDecision.class);

        assertThat(restored).isEqualTo(original);
    }

    @Test
    void whenSerializingIdentifiableSubscription_thenContainsIdAndSubscription() throws JsonProcessingException {
        val subscription = new AuthorizationSubscription(Value.of("keeper"), Value.of("access"), Value.of("silver_key"),
                Value.UNDEFINED);
        val identifiable = new IdentifiableAuthorizationSubscription("open-gate", subscription);
        val json         = mapper.writeValueAsString(identifiable);

        assertThat(json).contains("\"subscriptionId\":\"open-gate\"").contains("\"subscription\"")
                .contains("\"subject\":\"keeper\"");
    }

    @Test
    void whenRoundTrippingIdentifiableSubscription_thenPreserved() throws JsonProcessingException {
        val subscription = new AuthorizationSubscription(Value.of("deep_one"), Value.of("emerge"),
                Value.of("innsmouth_harbor"), Value.UNDEFINED);
        val original     = new IdentifiableAuthorizationSubscription("emergence-request", subscription);

        val json     = mapper.writeValueAsString(original);
        val restored = mapper.readValue(json, IdentifiableAuthorizationSubscription.class);

        assertThat(restored.subscriptionId()).isEqualTo(original.subscriptionId());
        assertThat(restored.subscription().subject()).isEqualTo(original.subscription().subject());
        assertThat(restored.subscription().action()).isEqualTo(original.subscription().action());
        assertThat(restored.subscription().resource()).isEqualTo(original.subscription().resource());
    }

    @Test
    void whenSerializingIdentifiableDecision_thenContainsIdAndDecision() throws JsonProcessingException {
        val identifiable = new IdentifiableAuthorizationDecision("read-tome", AuthorizationDecision.PERMIT);
        val json         = mapper.writeValueAsString(identifiable);

        assertThat(json).contains("\"subscriptionId\":\"read-tome\"").contains("\"decision\"")
                .contains("\"decision\":\"PERMIT\"");
    }

    @Test
    void whenRoundTrippingIdentifiableDecision_thenPreserved() throws JsonProcessingException {
        val decision = new AuthorizationDecision(Decision.DENY, List.of(), List.of(), Value.UNDEFINED);
        val original = new IdentifiableAuthorizationDecision("forbidden-ritual", decision);

        val json     = mapper.writeValueAsString(original);
        val restored = mapper.readValue(json, IdentifiableAuthorizationDecision.class);

        assertThat(restored.subscriptionId()).isEqualTo(original.subscriptionId());
        assertThat(restored.decision()).isEqualTo(original.decision());
    }

    @Test
    void whenSerializingMultiSubscription_thenSerializesAsMap() throws JsonProcessingException {
        val multiSubscription = new MultiAuthorizationSubscription()
                .addSubscription("read-necronomicon",
                        new AuthorizationSubscription(Value.of("scholar"), Value.of("read"), Value.of("necronomicon"),
                                Value.UNDEFINED))
                .addSubscription("write-journal", new AuthorizationSubscription(Value.of("scholar"), Value.of("write"),
                        Value.of("research_journal"), Value.UNDEFINED));

        val json = mapper.writeValueAsString(multiSubscription);

        assertThat(json).contains("\"read-necronomicon\"").contains("\"write-journal\"")
                .contains("\"subject\":\"scholar\"");
    }

    @Test
    void whenRoundTrippingMultiSubscription_thenPreserved() throws JsonProcessingException {
        val original = new MultiAuthorizationSubscription()
                .addSubscription("enter-dunwich",
                        new AuthorizationSubscription(Value.of("traveler"), Value.of("enter"), Value.of("dunwich"),
                                Value.UNDEFINED))
                .addSubscription("enter-arkham", new AuthorizationSubscription(Value.of("traveler"), Value.of("enter"),
                        Value.of("arkham"), Value.UNDEFINED));

        val json     = mapper.writeValueAsString(original);
        val restored = mapper.readValue(json, MultiAuthorizationSubscription.class);

        assertThat(restored.size()).isEqualTo(2);
        assertThat(restored.getSubscription("enter-dunwich").resource()).isEqualTo(Value.of("dunwich"));
        assertThat(restored.getSubscription("enter-arkham").resource()).isEqualTo(Value.of("arkham"));
    }

    @Test
    void whenSerializingMultiDecision_thenSerializesAsMap() throws JsonProcessingException {
        val multiDecision = new MultiAuthorizationDecision();
        multiDecision.setDecision("read-tome", AuthorizationDecision.PERMIT);
        multiDecision.setDecision("burn-tome", AuthorizationDecision.DENY);

        val json = mapper.writeValueAsString(multiDecision);

        assertThat(json).contains("\"read-tome\"").contains("\"burn-tome\"").contains("\"decision\":\"PERMIT\"")
                .contains("\"decision\":\"DENY\"");
    }

    @Test
    void whenRoundTrippingMultiDecision_thenPreserved() throws JsonProcessingException {
        val original = new MultiAuthorizationDecision();
        original.setDecision("summon-byakhee", AuthorizationDecision.DENY);
        original.setDecision("dismiss-byakhee", AuthorizationDecision.PERMIT);

        val json     = mapper.writeValueAsString(original);
        val restored = mapper.readValue(json, MultiAuthorizationDecision.class);

        assertThat(restored.size()).isEqualTo(2);
        assertThat(restored.getDecision("summon-byakhee")).isEqualTo(AuthorizationDecision.DENY);
        assertThat(restored.getDecision("dismiss-byakhee")).isEqualTo(AuthorizationDecision.PERMIT);
    }

    @Test
    void whenRoundTrippingMultiDecisionWithObligations_thenObligationsPreserved() throws JsonProcessingException {
        val obligation = Value.ofObject(Map.of("type", Value.of("log_access")));
        val decision   = new AuthorizationDecision(Decision.PERMIT, List.of(obligation), List.of(), Value.UNDEFINED);

        val original = new MultiAuthorizationDecision();
        original.setDecision("guarded-action", decision);

        val json     = mapper.writeValueAsString(original);
        val restored = mapper.readValue(json, MultiAuthorizationDecision.class);

        assertThat(restored.getDecision("guarded-action").obligations()).hasSize(1);
        assertThat(restored.getDecision("guarded-action").obligations().getFirst()).isEqualTo(obligation);
    }
}
