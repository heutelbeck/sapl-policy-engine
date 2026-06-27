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

import static com.google.protobuf.WireFormat.WIRETYPE_LENGTH_DELIMITED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.protobuf.CodedOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.stream.Stream;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationSubscription;
import lombok.val;

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
            val bytes        = SaplProtobufCodec.writeValue(original);
            val deserialized = SaplProtobufCodec.readValue(bytes);
            assertThat(deserialized).isEqualTo(original);
        }

        @Test
        @DisplayName("decoding a value nested beyond the maximum depth fails closed with an IOException")
        void whenValueNestedBeyondMaxDepthThenReadFailsClosed() throws IOException {
            // Hand-built on the wire: a hostile payload would not pass through the encoder,
            // and each O(1) wrap avoids the encoder's own deep-nesting cost.
            byte[] bytes = SaplProtobufCodec.writeValue(Value.of("leaf"));
            for (int i = 0; i < 1100; i++) {
                bytes = wrapInArrayValue(bytes);
            }
            val nested = bytes;

            val decode = (ThrowingCallable) () -> SaplProtobufCodec.readValue(nested);

            assertThatThrownBy(decode).isInstanceOf(IOException.class).hasMessageContaining("maximum depth");
        }

        @Test
        @Timeout(10)
        @DisplayName("a deeply nested value within the limit encodes without exponential blow-up")
        void whenDeeplyNestedValueWithinLimitThenEncodesInLinearTime() throws IOException {
            Value value = Value.of("leaf");
            for (int i = 0; i < 90; i++) {
                value = ArrayValue.builder().add(value).build();
            }

            val bytes = SaplProtobufCodec.writeValue(value);

            assertThat(SaplProtobufCodec.readValue(bytes)).isEqualTo(value);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("malformedNumberCases")
        @DisplayName("decoding a malformed or unbounded number fails closed with an IOException")
        void whenNumberPayloadMalformedOrUnboundedThenReadFailsClosed(String description, String numberLiteral)
                throws IOException {
            val bytes = numberValuePayload(numberLiteral);

            val decode = (ThrowingCallable) () -> SaplProtobufCodec.readValue(bytes);

            assertThatThrownBy(decode).isInstanceOf(IOException.class);
        }

        static Stream<Arguments> malformedNumberCases() {
            return Stream.of(arguments("empty string", ""), arguments("whitespace", "   "),
                    arguments("non-numeric text", "abc"), arguments("multiple decimal points", "1.2.3"),
                    arguments("NaN literal", "NaN"), arguments("Infinity literal", "Infinity"),
                    arguments("enormous negative scale", "1E2147483647"),
                    arguments("enormous positive scale", "1E-2147483647"),
                    arguments("scale at Integer.MIN_VALUE boundary", "1E2147483648"),
                    arguments("scale beyond the strict 1000 bound", "1E5000"),
                    arguments("scale at the old loose 1000000 bound", "1E1000000"),
                    arguments("over-length all-digit literal", "1".repeat(1001)));
        }

        @Test
        @DisplayName("a number within the accepted magnitude round-trips")
        void whenNumberWithinAcceptedMagnitudeThenRoundTrips() throws IOException {
            val value = new NumberValue(new BigDecimal("123456789.123456789"));

            val bytes = SaplProtobufCodec.writeValue(value);

            assertThat(SaplProtobufCodec.readValue(bytes)).isEqualTo(value);
        }

        private static byte[] numberValuePayload(String numberLiteral) throws IOException {
            // Value fields: one VALUE_NUMBER (field 3) carrying the raw on-the-wire string.
            // Hand-built because the encoder would never emit a malformed or unbounded
            // number.
            val valueBuffer = new ByteArrayOutputStream();
            val valueOut    = CodedOutputStream.newInstance(valueBuffer);
            valueOut.writeString(3, numberLiteral);
            valueOut.flush();
            return valueBuffer.toByteArray();
        }

        private static byte[] wrapInArrayValue(byte[] innerValueBytes) throws IOException {
            // ArrayValue content: one ARRAY_ELEMENTS (field 1) holding the inner value.
            val elementBuffer = new ByteArrayOutputStream();
            val elementOut    = CodedOutputStream.newInstance(elementBuffer);
            elementOut.writeTag(1, WIRETYPE_LENGTH_DELIMITED);
            elementOut.writeUInt32NoTag(innerValueBytes.length);
            elementOut.writeRawBytes(innerValueBytes);
            elementOut.flush();
            val elementBytes = elementBuffer.toByteArray();

            // Value fields: one VALUE_ARRAY (field 5) holding the array content.
            val valueBuffer = new ByteArrayOutputStream();
            val valueOut    = CodedOutputStream.newInstance(valueBuffer);
            valueOut.writeTag(5, WIRETYPE_LENGTH_DELIMITED);
            valueOut.writeUInt32NoTag(elementBytes.length);
            valueOut.writeRawBytes(elementBytes);
            valueOut.flush();
            return valueBuffer.toByteArray();
        }

        static Stream<Arguments> valueTestCases() {
            return Stream.of(arguments("null value", Value.NULL), arguments("undefined value", Value.UNDEFINED),
                    arguments("boolean true", Value.of(true)), arguments("boolean false", Value.of(false)),
                    arguments("integer number", new NumberValue(new BigDecimal("42"))),
                    arguments("decimal number", new NumberValue(new BigDecimal("2.5"))),
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
    @DisplayName("ErrorValue wire contract")
    class ErrorValueWireContractTests {

        @Test
        @DisplayName("an error value round-trips carrying only its message")
        void whenErrorValueEncodedThenOnlyMessageIsCarried() throws IOException {
            val error = Value.error("policy evaluation failed");

            val bytes        = SaplProtobufCodec.writeValue(error);
            val deserialized = SaplProtobufCodec.readValue(bytes);

            assertThat(deserialized).isInstanceOfSatisfying(ErrorValue.class,
                    e -> assertThat(e.message()).isEqualTo("policy evaluation failed"));
        }

        @Test
        @DisplayName("an error payload carrying the reserved arguments field is decoded without corruption")
        void whenErrorPayloadCarriesReservedArgumentsFieldThenItIsIgnored() throws IOException {
            // Field 2 is reserved. The decoder must skip it and recover the message.
            val bytes = errorValuePayloadWithLegacyArguments("boom", "ignored-argument");

            val deserialized = SaplProtobufCodec.readValue(bytes);

            assertThat(deserialized).isInstanceOfSatisfying(ErrorValue.class,
                    e -> assertThat(e.message()).isEqualTo("boom"));
        }

        private static byte[] errorValuePayloadWithLegacyArguments(String message, String legacyArgument)
                throws IOException {
            // Hand-built ErrorValue: message (field 1) plus reserved arguments (field 2).
            val errorBuffer = new ByteArrayOutputStream();
            val errorOut    = CodedOutputStream.newInstance(errorBuffer);
            errorOut.writeString(1, message);
            errorOut.writeString(2, legacyArgument);
            errorOut.flush();
            val errorBytes = errorBuffer.toByteArray();

            // VALUE_ERROR (field 8) holding the error content.
            val valueBuffer = new ByteArrayOutputStream();
            val valueOut    = CodedOutputStream.newInstance(valueBuffer);
            valueOut.writeTag(8, WIRETYPE_LENGTH_DELIMITED);
            valueOut.writeUInt32NoTag(errorBytes.length);
            valueOut.writeRawBytes(errorBytes);
            valueOut.flush();
            return valueBuffer.toByteArray();
        }
    }

    @Nested
    @DisplayName("AuthorizationSubscription serialization")
    class AuthorizationSubscriptionSerializationTests {

        @Test
        @DisplayName("should round-trip subscription with all fields")
        void shouldRoundTripSubscriptionWithAllFields() throws IOException {
            val subscription = new AuthorizationSubscription(Value.of("user123"), Value.of("read"),
                    Value.of("document/456"), ObjectValue.builder().put("time", Value.of("morning")).build(),
                    ObjectValue.builder().put("apiKey", Value.of("secret123")).build());

            val bytes        = SaplProtobufCodec.writeAuthorizationSubscription(subscription);
            val deserialized = SaplProtobufCodec.readAuthorizationSubscription(bytes);

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
            val subscription = new AuthorizationSubscription(Value.of("user"), Value.of("action"), Value.UNDEFINED,
                    Value.UNDEFINED, Value.EMPTY_OBJECT);

            val bytes        = SaplProtobufCodec.writeAuthorizationSubscription(subscription);
            val deserialized = SaplProtobufCodec.readAuthorizationSubscription(bytes);

            assertThat(deserialized).satisfies(d -> {
                assertThat(d.resource()).isEqualTo(Value.UNDEFINED);
                assertThat(d.environment()).isEqualTo(Value.UNDEFINED);
                assertThat(d.secrets()).isEqualTo(Value.EMPTY_OBJECT);
            });
        }

        @Test
        @DisplayName("secrets is always decoded as an object even when the wire carries a non-object value")
        void whenSecretsPayloadIsNotAnObjectThenSecretsDecodeAsEmptyObject() throws IOException {
            // A non-object value in field 5 must decode to an empty object, not leak
            // through.
            val bytes = subscriptionWithNonObjectSecrets("not-an-object");

            val deserialized = SaplProtobufCodec.readAuthorizationSubscription(bytes);

            assertThat(deserialized.secrets()).isEqualTo(Value.EMPTY_OBJECT);
        }

        private static byte[] subscriptionWithNonObjectSecrets(String secretText) throws IOException {
            // secrets as VALUE_TEXT (field 4) rather than an object.
            val secretValueBuffer = new ByteArrayOutputStream();
            val secretValueOut    = CodedOutputStream.newInstance(secretValueBuffer);
            secretValueOut.writeString(4, secretText);
            secretValueOut.flush();
            val secretValueBytes = secretValueBuffer.toByteArray();

            // SUBSCRIPTION_SECRETS (field 5).
            val subscriptionBuffer = new ByteArrayOutputStream();
            val subscriptionOut    = CodedOutputStream.newInstance(subscriptionBuffer);
            subscriptionOut.writeTag(5, WIRETYPE_LENGTH_DELIMITED);
            subscriptionOut.writeUInt32NoTag(secretValueBytes.length);
            subscriptionOut.writeRawBytes(secretValueBytes);
            subscriptionOut.flush();
            return subscriptionBuffer.toByteArray();
        }
    }

    @Nested
    @DisplayName("AuthorizationDecision serialization")
    class AuthorizationDecisionSerializationTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource("decisionTestCases")
        @DisplayName("should round-trip decisions correctly")
        void shouldRoundTripDecisions(String description, Decision decision) throws IOException {
            val authDecision = new AuthorizationDecision(decision, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY,
                    Value.UNDEFINED);

            val bytes        = SaplProtobufCodec.writeAuthorizationDecision(authDecision);
            val deserialized = SaplProtobufCodec.readAuthorizationDecision(bytes);

            assertThat(deserialized.decision()).isEqualTo(decision);
        }

        static Stream<Arguments> decisionTestCases() {
            return Stream.of(arguments("PERMIT", Decision.PERMIT), arguments("DENY", Decision.DENY),
                    arguments("SUSPEND", Decision.SUSPEND), arguments("INDETERMINATE", Decision.INDETERMINATE),
                    arguments("NOT_APPLICABLE", Decision.NOT_APPLICABLE));
        }

        @Test
        @DisplayName("should round-trip decision with obligations and advice")
        void shouldRoundTripDecisionWithObligationsAndAdvice() throws IOException {
            val obligations = ArrayValue.builder().add(ObjectValue.builder().put("type", Value.of("log")).build())
                    .build();
            val advice      = ArrayValue.builder().add(ObjectValue.builder().put("type", Value.of("notify")).build())
                    .build();
            val resource    = ObjectValue.builder().put("filtered", Value.of(true)).build();

            val authDecision = new AuthorizationDecision(Decision.PERMIT, obligations, advice, resource);

            val bytes        = SaplProtobufCodec.writeAuthorizationDecision(authDecision);
            val deserialized = SaplProtobufCodec.readAuthorizationDecision(bytes);

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
            val multi = new MultiAuthorizationSubscription();

            val bytes        = SaplProtobufCodec.writeMultiAuthorizationSubscription(multi);
            val deserialized = SaplProtobufCodec.readMultiAuthorizationSubscription(bytes);

            assertThat(deserialized.hasSubscriptions()).isFalse();
        }

        @Test
        @DisplayName("should round-trip multi-subscription with multiple entries")
        void shouldRoundTripMultiSubscriptionWithMultipleEntries() throws IOException {
            val multi = new MultiAuthorizationSubscription();
            multi.addSubscription("sub1", new AuthorizationSubscription(Value.of("user1"), Value.of("read"),
                    Value.of("doc1"), Value.UNDEFINED, Value.EMPTY_OBJECT));
            multi.addSubscription("sub2", new AuthorizationSubscription(Value.of("user2"), Value.of("write"),
                    Value.of("doc2"), Value.UNDEFINED, Value.EMPTY_OBJECT));

            val bytes        = SaplProtobufCodec.writeMultiAuthorizationSubscription(multi);
            val deserialized = SaplProtobufCodec.readMultiAuthorizationSubscription(bytes);

            assertThat(deserialized).isInstanceOfSatisfying(MultiAuthorizationSubscription.class, d -> {
                assertThat(d.size()).isEqualTo(2);
                assertThat(d.getSubscription("sub1").subject()).isEqualTo(Value.of("user1"));
                assertThat(d.getSubscription("sub2").action()).isEqualTo(Value.of("write"));
            });
        }

        @Test
        @DisplayName("a payload carrying two entries with the same id fails closed as IOException, not an unchecked exception")
        void whenDuplicateSubscriptionIdThenIOException() throws IOException {
            val single = new MultiAuthorizationSubscription();
            single.addSubscription("dup", new AuthorizationSubscription(Value.of("user"), Value.of("read"),
                    Value.of("doc"), Value.UNDEFINED, Value.EMPTY_OBJECT));
            val oneEntry  = SaplProtobufCodec.writeMultiAuthorizationSubscription(single);
            val twoSameId = new byte[oneEntry.length * 2];
            System.arraycopy(oneEntry, 0, twoSameId, 0, oneEntry.length);
            System.arraycopy(oneEntry, 0, twoSameId, oneEntry.length, oneEntry.length);

            val decode = (ThrowingCallable) () -> SaplProtobufCodec.readMultiAuthorizationSubscription(twoSameId);

            assertThatThrownBy(decode).isInstanceOf(IOException.class);
        }
    }

    @Nested
    @DisplayName("MultiAuthorizationDecision serialization")
    class MultiAuthorizationDecisionSerializationTests {

        @Test
        @DisplayName("should round-trip empty multi-decision")
        void shouldRoundTripEmptyMultiDecision() throws IOException {
            val multi = new MultiAuthorizationDecision();

            val bytes        = SaplProtobufCodec.writeMultiAuthorizationDecision(multi);
            val deserialized = SaplProtobufCodec.readMultiAuthorizationDecision(bytes);

            assertThat(deserialized.size()).isZero();
        }

        @Test
        @DisplayName("should round-trip multi-decision with multiple entries")
        void shouldRoundTripMultiDecisionWithMultipleEntries() throws IOException {
            val multi = new MultiAuthorizationDecision();
            multi.setDecision("sub1",
                    new AuthorizationDecision(Decision.PERMIT, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, Value.UNDEFINED));
            multi.setDecision("sub2",
                    new AuthorizationDecision(Decision.DENY, Value.EMPTY_ARRAY, Value.EMPTY_ARRAY, Value.UNDEFINED));

            val bytes        = SaplProtobufCodec.writeMultiAuthorizationDecision(multi);
            val deserialized = SaplProtobufCodec.readMultiAuthorizationDecision(bytes);

            assertThat(deserialized).isInstanceOfSatisfying(MultiAuthorizationDecision.class, d -> {
                assertThat(d.size()).isEqualTo(2);
                assertThat(d.getDecision("sub1").decision()).isEqualTo(Decision.PERMIT);
                assertThat(d.getDecision("sub2").decision()).isEqualTo(Decision.DENY);
            });
        }

        @Test
        @DisplayName("a multi-decision payload with duplicate subscription IDs is rejected")
        void whenMultiDecisionPayloadContainsDuplicateSubscriptionIdThenDecoderRejectsIt() throws IOException {
            val bytes = multiDecisionPayloadWithDuplicateId();

            val decode = (ThrowingCallable) () -> SaplProtobufCodec.readMultiAuthorizationDecision(bytes);

            assertThatThrownBy(decode).isInstanceOf(IOException.class).hasMessageContaining("subscription id");
        }
    }

    @Nested
    @DisplayName("IdentifiableAuthorizationDecision serialization")
    class IdentifiableAuthorizationDecisionSerializationTests {

        @Test
        @DisplayName("should round-trip identifiable decision")
        void shouldRoundTripIdentifiableDecision() throws IOException {
            val idDec = new IdentifiableAuthorizationDecision("subscription-123",
                    new AuthorizationDecision(Decision.PERMIT,
                            ArrayValue.builder().add(ObjectValue.builder().put("log", Value.of(true)).build()).build(),
                            Value.EMPTY_ARRAY, Value.UNDEFINED));

            val bytes        = SaplProtobufCodec.writeIdentifiableAuthorizationDecision(idDec);
            val deserialized = SaplProtobufCodec.readIdentifiableAuthorizationDecision(bytes);

            assertThat(deserialized).satisfies(d -> {
                assertThat(d.subscriptionId()).isEqualTo("subscription-123");
                assertThat(d.decision().decision()).isEqualTo(Decision.PERMIT);
                assertThat(d.decision().obligations()).hasSize(1);
            });
        }
    }

    private static byte[] multiDecisionPayloadWithDuplicateId() throws IOException {
        val first  = multiDecisionEntry("guarded-action", AuthorizationDecision.DENY);
        val second = multiDecisionEntry("guarded-action", AuthorizationDecision.PERMIT);
        val out    = new ByteArrayOutputStream(first.length + second.length);
        out.writeBytes(first);
        out.writeBytes(second);
        return out.toByteArray();
    }

    private static byte[] multiDecisionEntry(String subscriptionId, AuthorizationDecision decision) throws IOException {
        val content = SaplProtobufCodec.writeIdentifiableAuthorizationDecision(
                new IdentifiableAuthorizationDecision(subscriptionId, decision));
        val out     = new ByteArrayOutputStream();
        val output  = CodedOutputStream.newInstance(out);
        output.writeTag(1, WIRETYPE_LENGTH_DELIMITED);
        output.writeUInt32NoTag(content.length);
        output.writeRawBytes(content);
        output.flush();
        return out.toByteArray();
    }
}
