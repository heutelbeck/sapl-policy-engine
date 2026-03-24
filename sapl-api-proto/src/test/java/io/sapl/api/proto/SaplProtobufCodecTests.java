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
package io.sapl.api.proto;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationSubscription;

import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("SaplProtobufCodec")
class SaplProtobufCodecTests {

    @Nested
    @DisplayName("Value serialization")
    class ValueSerializationTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource("valueTestCases")
        @DisplayName("should round-trip Value types correctly")
        void shouldRoundTripValueTypes(String description, Value original) throws IOException {
            var bytes        = SaplProtobufCodec.writeValue(original);
            var deserialized = SaplProtobufCodec.readValue(bytes);
            assertThat(deserialized).isEqualTo(original);
        }

        static Stream<Arguments> valueTestCases() {
            return Stream.of(arguments("null value", Value.NULL), arguments("undefined value", Value.UNDEFINED),
                    arguments("boolean true", Value.of(true)), arguments("boolean false", Value.of(false)),
                    arguments("integer number", new NumberValue(new BigDecimal("42"))),
                    arguments("decimal number", new NumberValue(new BigDecimal("3.14159"))),
                    arguments("negative number", new NumberValue(new BigDecimal("-999"))),
                    arguments("large number", new NumberValue(new BigDecimal("12345678901234567890"))),
                    arguments("empty string", Value.of("")), arguments("simple string", Value.of("hello")),
                    arguments("string with unicode", Value.of("hello world")),
                    arguments("empty array", Value.EMPTY_ARRAY),
                    arguments("array with elements",
                            ArrayValue.builder().add(Value.of(1)).add(Value.of("two")).add(Value.of(true)).build()),
                    arguments("nested array",
                            ArrayValue.builder().add(ArrayValue.builder().add(Value.of(1)).build()).build()),
                    arguments("empty object", Value.EMPTY_OBJECT),
                    arguments("simple object", ObjectValue.builder().put("key", Value.of("value")).build()),
                    arguments("nested object",
                            ObjectValue.builder().put("outer", ObjectValue.builder().put("inner", Value.of(42)).build())
                                    .build()),
                    arguments("complex structure",
                            ObjectValue.builder().put("name", Value.of("test"))
                                    .put("values", ArrayValue.builder().add(Value.of(1)).add(Value.of(2)).build())
                                    .put("nested", ObjectValue.builder().put("flag", Value.of(true)).build()).build()),
                    arguments("error value", Value.error("test error message")),
                    arguments("deeply nested mixed types", createDeeplyNestedMixedTypes()),
                    arguments("array of arrays of objects", createArrayOfArraysOfObjects()),
                    arguments("object with all value types", createObjectWithAllValueTypes()));
        }

        private static Value createDeeplyNestedMixedTypes() {
            return ObjectValue.builder()
                    .put("level1",
                            ObjectValue.builder().put("level2", ObjectValue.builder().put("level3", ArrayValue.builder()
                                    .add(ObjectValue.builder()
                                            .put("level4",
                                                    ArrayValue.builder().add(Value.of("deep string"))
                                                            .add(new NumberValue(new BigDecimal("999.999")))
                                                            .add(Value.NULL).add(Value.of(true)).build())
                                            .build())
                                    .add(Value.error("nested error")).build()).build()).build())
                    .build();
        }

        private static Value createArrayOfArraysOfObjects() {
            return ArrayValue.builder()
                    .add(ArrayValue.builder().add(ObjectValue.builder().put("a", Value.of(1)).build())
                            .add(ObjectValue.builder().put("b", Value.of(2)).build()).build())
                    .add(ArrayValue.builder().add(ObjectValue.builder()
                            .put("nested", ArrayValue.builder().add(Value.of("x")).add(Value.of("y")).build()).build())
                            .build())
                    .add(ArrayValue.builder().add(Value.NULL).add(Value.UNDEFINED).add(Value.error("array error"))
                            .build())
                    .build();
        }

        private static Value createObjectWithAllValueTypes() {
            return ObjectValue.builder().put("nullField", Value.NULL).put("undefinedField", Value.UNDEFINED)
                    .put("boolTrue", Value.of(true)).put("boolFalse", Value.of(false))
                    .put("integer", new NumberValue(new BigDecimal("42")))
                    .put("decimal", new NumberValue(new BigDecimal("2.718281828")))
                    .put("negativeNumber", new NumberValue(new BigDecimal("-12345"))).put("emptyString", Value.of(""))
                    .put("textValue", Value.of("hello world")).put("emptyArray", Value.EMPTY_ARRAY)
                    .put("emptyObject", Value.EMPTY_OBJECT).put("errorValue", Value.error("field error"))
                    .put("nestedArray",
                            ArrayValue.builder().add(Value.of(1))
                                    .add(ObjectValue.builder().put("inner", Value.of(true)).build()).build())
                    .put("nestedObject", ObjectValue.builder()
                            .put("deep", ObjectValue.builder().put("deeper", Value.of("bottom")).build()).build())
                    .build();
        }
    }

    @Nested
    @DisplayName("AuthorizationSubscription serialization")
    class AuthorizationSubscriptionSerializationTests {

        @Test
        @DisplayName("should round-trip subscription with all fields")
        void shouldRoundTripSubscriptionWithAllFields() throws IOException {
            var subscription = new AuthorizationSubscription(Value.of("user123"), Value.of("read"),
                    Value.of("document/456"), ObjectValue.builder().put("time", Value.of("morning")).build(),
                    ObjectValue.builder().put("apiKey", Value.of("secret123")).build());

            var bytes        = SaplProtobufCodec.writeAuthorizationSubscription(subscription);
            var deserialized = SaplProtobufCodec.readAuthorizationSubscription(bytes);

            assertThat(deserialized).satisfies(s -> {
                assertThat(s.subject()).isEqualTo(subscription.subject());
                assertThat(s.action()).isEqualTo(subscription.action());
                assertThat(s.resource()).isEqualTo(subscription.resource());
                assertThat(s.environment()).isEqualTo(subscription.environment());
                assertThat(s.secrets()).isEqualTo(subscription.secrets());
            });
        }

        @Test
        @DisplayName("should round-trip subscription with undefined fields")
        void shouldRoundTripSubscriptionWithUndefinedFields() throws IOException {
            var subscription = new AuthorizationSubscription(Value.of("user"), Value.of("action"), Value.UNDEFINED,
                    Value.UNDEFINED, Value.EMPTY_OBJECT);

            var bytes        = SaplProtobufCodec.writeAuthorizationSubscription(subscription);
            var deserialized = SaplProtobufCodec.readAuthorizationSubscription(bytes);

            assertThat(deserialized).satisfies(d -> {
                assertThat(d.resource()).isEqualTo(Value.UNDEFINED);
                assertThat(d.environment()).isEqualTo(Value.UNDEFINED);
                assertThat(d.secrets()).isEqualTo(Value.EMPTY_OBJECT);
            });
        }
    }

    @Nested
    @DisplayName("AuthorizationDecision serialization")
    class AuthorizationDecisionSerializationTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource("decisionTestCases")
        @DisplayName("should round-trip decisions correctly")
        void shouldRoundTripDecisions(String description, Decision decision) throws IOException {
            var authDecision = new AuthorizationDecision(decision, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY,
                    Value.UNDEFINED);

            var bytes        = SaplProtobufCodec.writeAuthorizationDecision(authDecision);
            var deserialized = SaplProtobufCodec.readAuthorizationDecision(bytes);

            assertThat(deserialized.decision()).isEqualTo(decision);
        }

        static Stream<Arguments> decisionTestCases() {
            return Stream.of(arguments("PERMIT", Decision.PERMIT), arguments("DENY", Decision.DENY),
                    arguments("INDETERMINATE", Decision.INDETERMINATE),
                    arguments("NOT_APPLICABLE", Decision.NOT_APPLICABLE));
        }

        @Test
        @DisplayName("should round-trip decision with obligations and advice")
        void shouldRoundTripDecisionWithObligationsAndAdvice() throws IOException {
            var obligations = ArrayValue.builder().add(ObjectValue.builder().put("type", Value.of("log")).build())
                    .build();
            var advice      = ArrayValue.builder().add(ObjectValue.builder().put("type", Value.of("notify")).build())
                    .build();
            var resource    = ObjectValue.builder().put("filtered", Value.of(true)).build();

            var authDecision = new AuthorizationDecision(Decision.PERMIT, obligations, advice, resource);

            var bytes        = SaplProtobufCodec.writeAuthorizationDecision(authDecision);
            var deserialized = SaplProtobufCodec.readAuthorizationDecision(bytes);

            assertThat(deserialized).satisfies(d -> {
                assertThat(d.decision()).isEqualTo(Decision.PERMIT);
                assertThat(d.obligations()).isEqualTo(obligations);
                assertThat(d.advice()).isEqualTo(advice);
                assertThat(d.resource()).isEqualTo(resource);
            });
        }
    }

    @Nested
    @DisplayName("MultiAuthorizationSubscription serialization")
    class MultiAuthorizationSubscriptionSerializationTests {

        @Test
        @DisplayName("should round-trip empty multi-subscription")
        void shouldRoundTripEmptyMultiSubscription() throws IOException {
            var multi = new MultiAuthorizationSubscription();

            var bytes        = SaplProtobufCodec.writeMultiAuthorizationSubscription(multi);
            var deserialized = SaplProtobufCodec.readMultiAuthorizationSubscription(bytes);

            assertThat(deserialized.hasSubscriptions()).isFalse();
        }

        @Test
        @DisplayName("should round-trip multi-subscription with multiple entries")
        void shouldRoundTripMultiSubscriptionWithMultipleEntries() throws IOException {
            var multi = new MultiAuthorizationSubscription();
            multi.addSubscription("sub1", new AuthorizationSubscription(Value.of("user1"), Value.of("read"),
                    Value.of("doc1"), Value.UNDEFINED, Value.EMPTY_OBJECT));
            multi.addSubscription("sub2", new AuthorizationSubscription(Value.of("user2"), Value.of("write"),
                    Value.of("doc2"), Value.UNDEFINED, Value.EMPTY_OBJECT));

            var bytes        = SaplProtobufCodec.writeMultiAuthorizationSubscription(multi);
            var deserialized = SaplProtobufCodec.readMultiAuthorizationSubscription(bytes);

            assertThat(deserialized).isInstanceOfSatisfying(MultiAuthorizationSubscription.class, d -> {
                assertThat(d.size()).isEqualTo(2);
                assertThat(d.getSubscription("sub1").subject()).isEqualTo(Value.of("user1"));
                assertThat(d.getSubscription("sub2").action()).isEqualTo(Value.of("write"));
            });
        }
    }

    @Nested
    @DisplayName("MultiAuthorizationDecision serialization")
    class MultiAuthorizationDecisionSerializationTests {

        @Test
        @DisplayName("should round-trip empty multi-decision")
        void shouldRoundTripEmptyMultiDecision() throws IOException {
            var multi = new MultiAuthorizationDecision();

            var bytes        = SaplProtobufCodec.writeMultiAuthorizationDecision(multi);
            var deserialized = SaplProtobufCodec.readMultiAuthorizationDecision(bytes);

            assertThat(deserialized.size()).isZero();
        }

        @Test
        @DisplayName("should round-trip multi-decision with multiple entries")
        void shouldRoundTripMultiDecisionWithMultipleEntries() throws IOException {
            var multi = new MultiAuthorizationDecision();
            multi.setDecision("sub1",
                    new AuthorizationDecision(Decision.PERMIT, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, Value.UNDEFINED));
            multi.setDecision("sub2",
                    new AuthorizationDecision(Decision.DENY, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, Value.UNDEFINED));

            var bytes        = SaplProtobufCodec.writeMultiAuthorizationDecision(multi);
            var deserialized = SaplProtobufCodec.readMultiAuthorizationDecision(bytes);

            assertThat(deserialized).isInstanceOfSatisfying(MultiAuthorizationDecision.class, d -> {
                assertThat(d.size()).isEqualTo(2);
                assertThat(d.getDecision("sub1").decision()).isEqualTo(Decision.PERMIT);
                assertThat(d.getDecision("sub2").decision()).isEqualTo(Decision.DENY);
            });
        }
    }

    @Nested
    @DisplayName("IdentifiableAuthorizationDecision serialization")
    class IdentifiableAuthorizationDecisionSerializationTests {

        @Test
        @DisplayName("should round-trip identifiable decision")
        void shouldRoundTripIdentifiableDecision() throws IOException {
            var idDec = new IdentifiableAuthorizationDecision("subscription-123",
                    new AuthorizationDecision(Decision.PERMIT,
                            ArrayValue.builder().add(ObjectValue.builder().put("log", Value.of(true)).build()).build(),
                            Value.EMPTY_ARRAY, Value.UNDEFINED));

            var bytes        = SaplProtobufCodec.writeIdentifiableAuthorizationDecision(idDec);
            var deserialized = SaplProtobufCodec.readIdentifiableAuthorizationDecision(bytes);

            assertThat(deserialized).satisfies(d -> {
                assertThat(d.subscriptionId()).isEqualTo("subscription-123");
                assertThat(d.decision().decision()).isEqualTo(Decision.PERMIT);
                assertThat(d.decision().obligations()).hasSize(1);
            });
        }
    }
}
