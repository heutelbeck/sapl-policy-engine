/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import io.sapl.api.pdp.*;
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
    void when_serializingValue_then_producesExpectedJson(Value value, String expectedJson)
            throws JsonProcessingException {
        val json = mapper.writeValueAsString(value);
        assertThat(json).isEqualTo(expectedJson);
    }

    static Stream<Arguments> valueSerializationCases() {
        return Stream.of(arguments(Value.NULL, "null"), arguments(Value.TRUE, "true"), arguments(Value.FALSE, "false"),
                arguments(Value.of(42), "42"), arguments(Value.of(4.13), "4.13"),
                arguments(Value.of("eldritch"), "\"eldritch\""), arguments(Value.EMPTY_ARRAY, "[]"),
                arguments(Value.EMPTY_OBJECT, "{}"),
                arguments(Value.ofArray(Value.of(1), Value.of(2), Value.of(3)), "[1,2,3]"),
                arguments(Value.ofObject(Map.of("cultist", Value.of("Wilbur"))), "{\"cultist\":\"Wilbur\"}"));
    }

    @ParameterizedTest
    @MethodSource("valueDeserializationCases")
    void when_deserializingJson_then_producesExpectedValue(String json, Value expectedValue)
            throws JsonProcessingException {
        val value = mapper.readValue(json, Value.class);
        assertThat(value).isEqualTo(expectedValue);
    }

    static Stream<Arguments> valueDeserializationCases() {
        return Stream.of(arguments("null", Value.NULL), arguments("true", Value.TRUE), arguments("false", Value.FALSE),
                arguments("42", Value.of(42)), arguments("4.13", Value.of(4.13)),
                arguments("\"necronomicon\"", Value.of("necronomicon")), arguments("[]", Value.EMPTY_ARRAY),
                arguments("{}", Value.EMPTY_OBJECT),
                arguments("[1,2,3]", Value.ofArray(Value.of(1), Value.of(2), Value.of(3))),
                arguments("{\"tome\":\"forbidden\"}", Value.ofObject(Map.of("tome", Value.of("forbidden")))));
    }

    @ParameterizedTest
    @MethodSource("nonSerializableValueCases")
    void when_serializingNonSerializableValue_then_throwsException(Value value, String expectedMessage) {
        assertThatThrownBy(() -> mapper.writeValueAsString(value)).hasCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(expectedMessage);
    }

    static Stream<Arguments> nonSerializableValueCases() {
        return Stream.of(arguments(Value.UNDEFINED, "UndefinedValue"),
                arguments(Value.error("The stars are not right."), "ErrorValue"));
    }

    @Test
    void when_serializingArrayWithUndefined_then_undefinedIsSkipped() throws JsonProcessingException {
        val array = Value.ofArray(Value.of("visible"), Value.UNDEFINED, Value.of("also visible"));
        val json  = mapper.writeValueAsString(array);
        assertThat(json).isEqualTo("[\"visible\",\"also visible\"]");
    }

    @Test
    void when_serializingObjectWithUndefined_then_undefinedIsSkipped() throws JsonProcessingException {
        val object = Value.ofObject(
                Map.of("name", Value.of("Cthulhu"), "location", Value.UNDEFINED, "status", Value.of("dreaming")));
        val json   = mapper.writeValueAsString(object);
        assertThat(json).doesNotContain("location").contains("name").contains("status");
    }

    @Test
    void when_roundTrippingComplexValue_then_valueIsPreserved() throws JsonProcessingException {
        val original = Value.ofObject(Map.of("investigator", Value.of("Herbert West"), "experiments",
                Value.ofArray(Value.of("reanimation"), Value.of("serum")), "success", Value.FALSE, "attempts",
                Value.of(17)));
        val json     = mapper.writeValueAsString(original);
        val restored = mapper.readValue(json, Value.class);
        assertThat(restored).isEqualTo(original);
    }

    @ParameterizedTest
    @MethodSource("simpleDecisionSerializationCases")
    void when_serializingSimpleDecision_then_onlyDecisionFieldIncluded(AuthorizationDecision decision,
            String expectedJson) throws JsonProcessingException {
        val json = mapper.writeValueAsString(decision);
        assertThat(json).isEqualTo(expectedJson);
    }

    static Stream<Arguments> simpleDecisionSerializationCases() {
        return Stream.of(arguments(AuthorizationDecision.PERMIT, "{\"decision\":\"PERMIT\"}"),
                arguments(AuthorizationDecision.DENY, "{\"decision\":\"DENY\"}"),
                arguments(AuthorizationDecision.INDETERMINATE, "{\"decision\":\"INDETERMINATE\"}"),
                arguments(AuthorizationDecision.NOT_APPLICABLE, "{\"decision\":\"NOT_APPLICABLE\"}"));
    }

    @ParameterizedTest
    @MethodSource("decisionWithOptionalFieldsCases")
    void when_serializingDecisionWithOptionalFields_then_onlyPresentFieldsIncluded(AuthorizationDecision decision,
            List<String> expectedPresent, List<String> expectedAbsent) throws JsonProcessingException {
        val json = mapper.writeValueAsString(decision);

        assertThat(json).contains("\"decision\":\"PERMIT\"");
        expectedPresent.forEach(field -> assertThat(json).contains(field));
        expectedAbsent.forEach(field -> assertThat(json).doesNotContain(field));
    }

    static Stream<Arguments> decisionWithOptionalFieldsCases() {
        val obligation = Value.ofObject(Map.of("type", Value.of("log")));
        val advice     = Value.ofObject(Map.of("recommendation", Value.of("enable_2fa")));
        val resource   = Value.ofObject(Map.of("filtered", Value.TRUE));

        return Stream.of(
                arguments(new AuthorizationDecision(Decision.PERMIT, List.of(obligation), List.of(), Value.UNDEFINED),
                        List.of("\"obligations\"", "\"type\":\"log\""), List.of("\"advice\"", "\"resource\"")),
                arguments(new AuthorizationDecision(Decision.PERMIT, List.of(), List.of(advice), Value.UNDEFINED),
                        List.of("\"advice\"", "\"recommendation\""), List.of("\"obligations\"", "\"resource\"")),
                arguments(new AuthorizationDecision(Decision.PERMIT, List.of(), List.of(), resource),
                        List.of("\"resource\"", "\"filtered\":true"), List.of("\"obligations\"", "\"advice\"")),
                arguments(new AuthorizationDecision(Decision.PERMIT, List.of(obligation), List.of(advice), resource),
                        List.of("\"obligations\"", "\"advice\"", "\"resource\""), List.of()));
    }

    @Test
    void when_serializingAuthorizationSubscription_then_allFieldsSerialized() throws JsonProcessingException {
        val subscription = new AuthorizationSubscription(Value.of("investigator"), Value.of("read"),
                Value.of("necronomicon"), Value.ofObject(Map.of("location", Value.of("Miskatonic University"))));
        val json         = mapper.writeValueAsString(subscription);

        assertThat(json).contains("\"subject\":\"investigator\"").contains("\"action\":\"read\"")
                .contains("\"resource\":\"necronomicon\"").contains("\"environment\"");
    }

    @Test
    void when_deserializingAuthorizationSubscription_then_allFieldsRestored() throws JsonProcessingException {
        val json         = """
                {"subject":"cultist","action":"summon","resource":"shoggoth","environment":{"ritual":"complete"}}""";
        val subscription = mapper.readValue(json, AuthorizationSubscription.class);

        assertThat(subscription.subject()).isEqualTo(Value.of("cultist"));
        assertThat(subscription.action()).isEqualTo(Value.of("summon"));
        assertThat(subscription.resource()).isEqualTo(Value.of("shoggoth"));
        assertThat(subscription.environment()).isEqualTo(Value.ofObject(Map.of("ritual", Value.of("complete"))));
    }

    @Test
    void when_roundTrippingAuthorizationSubscription_then_subscriptionPreserved() throws JsonProcessingException {
        val original = new AuthorizationSubscription(
                Value.ofObject(Map.of("name", Value.of("Randolph Carter"), "role", Value.of("dreamer"))),
                Value.of("enter"), Value.of("dreamlands"), Value.UNDEFINED);
        val json     = mapper.writeValueAsString(original);
        val restored = mapper.readValue(json, AuthorizationSubscription.class);

        // Environment becomes UNDEFINED after round-trip (not serialized), so compare
        // relevant fields
        assertThat(restored.subject()).isEqualTo(original.subject());
        assertThat(restored.action()).isEqualTo(original.action());
        assertThat(restored.resource()).isEqualTo(original.resource());
    }

    @Test
    void when_roundTrippingAuthorizationDecision_then_decisionPreserved() throws JsonProcessingException {
        val obligation = Value.ofObject(Map.of("type", Value.of("notify"), "target", Value.of("security")));
        val advice     = Value.ofObject(Map.of("suggestion", Value.of("Review access logs")));
        val resource   = Value.ofObject(Map.of("sanitized", Value.TRUE));
        val original   = new AuthorizationDecision(Decision.PERMIT, List.of(obligation), List.of(advice), resource);

        val json     = mapper.writeValueAsString(original);
        val restored = mapper.readValue(json, AuthorizationDecision.class);

        assertThat(restored).isEqualTo(original);
    }

    @Test
    void when_serializingIdentifiableSubscription_then_containsIdAndSubscription() throws JsonProcessingException {
        val subscription = new AuthorizationSubscription(Value.of("keeper"), Value.of("access"), Value.of("silver_key"),
                Value.UNDEFINED);
        val identifiable = new IdentifiableAuthorizationSubscription("open-gate", subscription);
        val json         = mapper.writeValueAsString(identifiable);

        assertThat(json).contains("\"subscriptionId\":\"open-gate\"").contains("\"subscription\"")
                .contains("\"subject\":\"keeper\"");
    }

    @Test
    void when_roundTrippingIdentifiableSubscription_then_preserved() throws JsonProcessingException {
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
    void when_serializingIdentifiableDecision_then_containsIdAndDecision() throws JsonProcessingException {
        val identifiable = new IdentifiableAuthorizationDecision("read-tome", AuthorizationDecision.PERMIT);
        val json         = mapper.writeValueAsString(identifiable);

        assertThat(json).contains("\"subscriptionId\":\"read-tome\"").contains("\"decision\"")
                .contains("\"decision\":\"PERMIT\"");
    }

    @Test
    void when_roundTrippingIdentifiableDecision_then_preserved() throws JsonProcessingException {
        val decision = new AuthorizationDecision(Decision.DENY, List.of(), List.of(), Value.UNDEFINED);
        val original = new IdentifiableAuthorizationDecision("forbidden-ritual", decision);

        val json     = mapper.writeValueAsString(original);
        val restored = mapper.readValue(json, IdentifiableAuthorizationDecision.class);

        assertThat(restored.subscriptionId()).isEqualTo(original.subscriptionId());
        assertThat(restored.decision()).isEqualTo(original.decision());
    }

    @Test
    void when_serializingMultiSubscription_then_serializesAsMap() throws JsonProcessingException {
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
    void when_roundTrippingMultiSubscription_then_preserved() throws JsonProcessingException {
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
    void when_serializingMultiDecision_then_serializesAsMap() throws JsonProcessingException {
        val multiDecision = new MultiAuthorizationDecision();
        multiDecision.setDecision("read-tome", AuthorizationDecision.PERMIT);
        multiDecision.setDecision("burn-tome", AuthorizationDecision.DENY);

        val json = mapper.writeValueAsString(multiDecision);

        assertThat(json).contains("\"read-tome\"").contains("\"burn-tome\"").contains("\"decision\":\"PERMIT\"")
                .contains("\"decision\":\"DENY\"");
    }

    @Test
    void when_roundTrippingMultiDecision_then_preserved() throws JsonProcessingException {
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
    void when_roundTrippingMultiDecisionWithObligations_then_obligationsPreserved() throws JsonProcessingException {
        val obligation = Value.ofObject(Map.of("type", Value.of("log_access")));
        val decision   = new AuthorizationDecision(Decision.PERMIT, List.of(obligation), List.of(), Value.UNDEFINED);

        val original = new MultiAuthorizationDecision();
        original.setDecision("guarded-action", decision);

        val json     = mapper.writeValueAsString(original);
        val restored = mapper.readValue(json, MultiAuthorizationDecision.class);

        assertThat(restored.getDecision("guarded-action").obligations()).hasSize(1);
        assertThat(restored.getDecision("guarded-action").obligations().getFirst()).isEqualTo(obligation);
    }

    @ParameterizedTest
    @MethodSource("combiningAlgorithmSerializationCases")
    void when_serializingCombiningAlgorithm_then_producesEnumName(CombiningAlgorithm algorithm, String expectedJson)
            throws JsonProcessingException {
        val json = mapper.writeValueAsString(algorithm);
        assertThat(json).isEqualTo(expectedJson);
    }

    static Stream<Arguments> combiningAlgorithmSerializationCases() {
        return Stream.of(arguments(CombiningAlgorithm.DENY_OVERRIDES, "\"DENY_OVERRIDES\""),
                arguments(CombiningAlgorithm.PERMIT_OVERRIDES, "\"PERMIT_OVERRIDES\""),
                arguments(CombiningAlgorithm.DENY_UNLESS_PERMIT, "\"DENY_UNLESS_PERMIT\""),
                arguments(CombiningAlgorithm.PERMIT_UNLESS_DENY, "\"PERMIT_UNLESS_DENY\""),
                arguments(CombiningAlgorithm.ONLY_ONE_APPLICABLE, "\"ONLY_ONE_APPLICABLE\""));
    }

    @ParameterizedTest
    @MethodSource("combiningAlgorithmDeserializationCases")
    void when_deserializingCombiningAlgorithm_then_supportsCaseInsensitive(String json, CombiningAlgorithm expected)
            throws JsonProcessingException {
        val algorithm = mapper.readValue(json, CombiningAlgorithm.class);
        assertThat(algorithm).isEqualTo(expected);
    }

    static Stream<Arguments> combiningAlgorithmDeserializationCases() {
        return Stream.of(arguments("\"DENY_OVERRIDES\"", CombiningAlgorithm.DENY_OVERRIDES),
                arguments("\"deny_overrides\"", CombiningAlgorithm.DENY_OVERRIDES),
                arguments("\"Deny_Overrides\"", CombiningAlgorithm.DENY_OVERRIDES),
                arguments("\"deny-overrides\"", CombiningAlgorithm.DENY_OVERRIDES),
                arguments("\"DENY-OVERRIDES\"", CombiningAlgorithm.DENY_OVERRIDES),
                arguments("\"PERMIT_OVERRIDES\"", CombiningAlgorithm.PERMIT_OVERRIDES),
                arguments("\"permit_overrides\"", CombiningAlgorithm.PERMIT_OVERRIDES),
                arguments("\"permit-overrides\"", CombiningAlgorithm.PERMIT_OVERRIDES),
                arguments("\"DENY_UNLESS_PERMIT\"", CombiningAlgorithm.DENY_UNLESS_PERMIT),
                arguments("\"deny-unless-permit\"", CombiningAlgorithm.DENY_UNLESS_PERMIT),
                arguments("\"PERMIT_UNLESS_DENY\"", CombiningAlgorithm.PERMIT_UNLESS_DENY),
                arguments("\"permit-unless-deny\"", CombiningAlgorithm.PERMIT_UNLESS_DENY),
                arguments("\"ONLY_ONE_APPLICABLE\"", CombiningAlgorithm.ONLY_ONE_APPLICABLE),
                arguments("\"only-one-applicable\"", CombiningAlgorithm.ONLY_ONE_APPLICABLE));
    }

    @Test
    void when_deserializingInvalidCombiningAlgorithm_then_throwsException() {
        assertThatThrownBy(() -> mapper.readValue("\"INVALID_ALGORITHM\"", CombiningAlgorithm.class))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void when_serializingPDPConfiguration_then_allFieldsSerialized() throws JsonProcessingException {
        val configuration = new PDPConfiguration("arkham-pdp", "v1.0", CombiningAlgorithm.DENY_OVERRIDES,
                TraceLevel.STANDARD, List.of("policy access-control permit", "policy audit-log deny"),
                Map.of("serverUrl", Value.of("https://miskatonic.edu"), "maxRetries", Value.of(3)));
        val json          = mapper.writeValueAsString(configuration);

        assertThat(json).contains("\"pdpId\":\"arkham-pdp\"").contains("\"configurationId\":\"v1.0\"")
                .contains("\"combiningAlgorithm\":\"DENY_OVERRIDES\"")
                .contains("\"saplDocuments\":[\"policy access-control permit\",\"policy audit-log deny\"]")
                .contains("\"variables\"").contains("\"serverUrl\":\"https://miskatonic.edu\"")
                .contains("\"maxRetries\":3");
    }

    @Test
    void when_deserializingPDPConfiguration_then_allFieldsRestored() throws JsonProcessingException {
        val json          = """
                {
                    "pdpId": "innsmouth-pdp",
                    "configurationId": "ritual-security",
                    "combiningAlgorithm": "PERMIT_OVERRIDES",
                    "saplDocuments": ["policy deep-ones permit"],
                    "variables": {"depth": 100, "location": "reef"}
                }""";
        val configuration = mapper.readValue(json, PDPConfiguration.class);

        assertThat(configuration.pdpId()).isEqualTo("innsmouth-pdp");
        assertThat(configuration.configurationId()).isEqualTo("ritual-security");
        assertThat(configuration.combiningAlgorithm()).isEqualTo(CombiningAlgorithm.PERMIT_OVERRIDES);
        assertThat(configuration.saplDocuments()).containsExactly("policy deep-ones permit");
        assertThat(configuration.variables()).containsEntry("depth", Value.of(100)).containsEntry("location",
                Value.of("reef"));
    }

    @Test
    void when_roundTrippingPDPConfiguration_then_configurationPreserved() throws JsonProcessingException {
        val original = new PDPConfiguration("dunwich-pdp", "elder-security", CombiningAlgorithm.ONLY_ONE_APPLICABLE,
                TraceLevel.STANDARD,
                List.of("policy whateley-access permit where action == \"read\"",
                        "policy stone-circles deny where subject.sanity < 50"),
                Map.of("threshold", Value.of(42), "location", Value.of("standing stones"), "active", Value.TRUE));

        val json     = mapper.writeValueAsString(original);
        val restored = mapper.readValue(json, PDPConfiguration.class);

        assertThat(restored).isEqualTo(original);
    }

    @Test
    void when_deserializingPDPConfigurationWithKebabCaseAlgorithm_then_parsesCorrectly()
            throws JsonProcessingException {
        val json          = """
                {
                    "pdpId": "test-pdp",
                    "configurationId": "test-security",
                    "combiningAlgorithm": "deny-unless-permit",
                    "saplDocuments": [],
                    "variables": {}
                }""";
        val configuration = mapper.readValue(json, PDPConfiguration.class);

        assertThat(configuration.combiningAlgorithm()).isEqualTo(CombiningAlgorithm.DENY_UNLESS_PERMIT);
    }

    @Test
    void when_deserializingPDPConfigurationWithComplexVariables_then_variablesPreserved()
            throws JsonProcessingException {
        val json          = """
                {
                    "pdpId": "complex-pdp",
                    "configurationId": "complex-security",
                    "combiningAlgorithm": "PERMIT_UNLESS_DENY",
                    "saplDocuments": [],
                    "variables": {
                        "nested": {"inner": "value", "count": 5},
                        "array": [1, 2, 3],
                        "bool": true,
                        "nullable": null
                    }
                }""";
        val configuration = mapper.readValue(json, PDPConfiguration.class);

        assertThat(configuration.variables()).hasSize(4);
        assertThat(configuration.variables().get("nested")).isInstanceOf(io.sapl.api.model.ObjectValue.class);
        assertThat(configuration.variables().get("array")).isInstanceOf(io.sapl.api.model.ArrayValue.class);
        assertThat(configuration.variables().get("bool")).isEqualTo(Value.TRUE);
        assertThat(configuration.variables().get("nullable")).isEqualTo(Value.NULL);
    }

    @Test
    void when_deserializingPDPConfigurationWithoutPdpId_then_throwsException() {
        val json = """
                {
                    "configurationId": "test-security",
                    "combiningAlgorithm": "DENY_OVERRIDES",
                    "saplDocuments": [],
                    "variables": {}
                }""";
        assertThatThrownBy(() -> mapper.readValue(json, PDPConfiguration.class)).hasMessageContaining("pdpId");
    }

    @Test
    void when_deserializingPDPConfigurationWithoutConfigurationId_then_throwsException() {
        val json = """
                {
                    "pdpId": "test-pdp",
                    "combiningAlgorithm": "DENY_OVERRIDES",
                    "saplDocuments": [],
                    "variables": {}
                }""";
        assertThatThrownBy(() -> mapper.readValue(json, PDPConfiguration.class))
                .hasMessageContaining("configurationId");
    }

    @Test
    void when_deserializingPDPConfigurationWithoutAlgorithm_then_throwsException() {
        val json = """
                {
                    "pdpId": "test-pdp",
                    "configurationId": "test-security",
                    "saplDocuments": [],
                    "variables": {}
                }""";
        assertThatThrownBy(() -> mapper.readValue(json, PDPConfiguration.class))
                .hasMessageContaining("combiningAlgorithm");
    }

    @Test
    void when_deserializingPDPConfigurationWithEmptyDocumentsAndVariables_then_defaultsToEmptyCollections()
            throws JsonProcessingException {
        val json          = """
                {
                    "pdpId": "empty-pdp",
                    "configurationId": "empty-security",
                    "combiningAlgorithm": "DENY_OVERRIDES"
                }""";
        val configuration = mapper.readValue(json, PDPConfiguration.class);

        assertThat(configuration.saplDocuments()).isEmpty();
        assertThat(configuration.variables()).isEmpty();
    }

    @Test
    void when_roundTrippingPDPConfigurationWithMultilineDocuments_then_newlinesPreserved()
            throws JsonProcessingException {
        val multilinePolicy = """
                policy "elder-sign-access"
                permit
                where
                    subject.role == "investigator";
                    action == "read";
                    resource.classification != "restricted";
                """;
        val original        = new PDPConfiguration("arkham-pdp", "v1.0", CombiningAlgorithm.DENY_OVERRIDES,
                TraceLevel.STANDARD, List.of(multilinePolicy), Map.of());

        val json     = mapper.writeValueAsString(original);
        val restored = mapper.readValue(json, PDPConfiguration.class);

        assertThat(restored.saplDocuments()).hasSize(1);
        assertThat(restored.saplDocuments().getFirst()).isEqualTo(multilinePolicy);
        assertThat(restored.saplDocuments().getFirst()).contains("\n");
    }
}
