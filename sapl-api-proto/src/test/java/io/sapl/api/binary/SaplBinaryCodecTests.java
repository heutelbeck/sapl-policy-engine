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
package io.sapl.api.binary;

import static io.sapl.api.binary.SaplBinaryFormatException.Reason.DUPLICATE_SUBSCRIPTION_ID;
import static io.sapl.api.binary.SaplBinaryFormatException.Reason.INVALID_NUMBER;
import static io.sapl.api.binary.SaplBinaryFormatException.Reason.MALFORMED;
import static io.sapl.api.binary.SaplBinaryFormatException.Reason.TOO_DEEP;
import static io.sapl.api.binary.SaplBinaryFormatException.Reason.TOO_MANY_SUBSCRIPTIONS;
import static io.sapl.api.binary.SaplBinaryFormatException.Reason.UNPAIRED_SURROGATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.api.binary.SaplBinaryFormatException.Reason;
import io.sapl.api.binary.WireCases.Case;
import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.IdentifiableAuthorizationDecision;
import io.sapl.api.pdp.IdentifiableAuthorizationSubscription;
import io.sapl.api.pdp.MultiAuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationSubscription;
import lombok.val;

@DisplayName("SaplBinaryCodec")
class SaplBinaryCodecTests {

    /** The most subscriptions the vectors allow in a multi-subscription. */
    private static final int MAX_SUBSCRIPTIONS = 256;

    /** The deepest nesting of arrays and objects the format allows. */
    private static final int DEPTH_LIMIT = 500;

    /** The message of the error value that the word {@code error} stands for in a decision vector. */
    private static final String ERROR_MESSAGE = "Boom.";

    private static final HexFormat HEX = HexFormat.ofDelimiter(" ");

    /** A computation that reads or writes a payload. */
    @FunctionalInterface
    interface Coding<T> {
        T run() throws SaplBinaryFormatException;
    }

    /** The result of a computation, or the reason it was refused for. */
    private static Object outcome(Coding<?> coding) {
        try {
            return coding.run();
        } catch (SaplBinaryFormatException e) {
            return e.getReason();
        }
    }

    @Nested
    @DisplayName("Test vectors of the binary format")
    class VectorTests {

        static Stream<Case> subscriptionCases() {
            return WireCases.casesIn("subscriptions-binary.cases").stream();
        }

        static Stream<Case> multiSubscriptionCases() {
            return WireCases.casesIn("multi-subscriptions-binary.cases").stream();
        }

        static Stream<Case> decisionCases() {
            return WireCases.casesIn("decisions.cases").stream();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("subscriptionCases")
        @DisplayName("a subscription vector reads as its parts or is refused for its reason")
        void whenSubscriptionVectorIsReadThenItGivesItsPartsOrItsRefusal(Case vector) {
            val payload = WireCases.bytesOfHex(vector.document());
            val read    = outcome(() -> asStated(SaplBinaryCodec.readAuthorizationSubscription(payload)));
            assertThat(read).isEqualTo(expectedSubscription(vector));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("multiSubscriptionCases")
        @DisplayName("a multi-subscription vector reads as its ids or is refused for its reason")
        void whenMultiSubscriptionVectorIsReadThenItGivesItsIdsOrItsRefusal(Case vector) {
            val payload = WireCases.bytesOfHex(vector.document());
            val read    = outcome(
                    () -> ids(SaplBinaryCodec.readMultiAuthorizationSubscription(payload, MAX_SUBSCRIPTIONS)));
            assertThat(read).isEqualTo(expectedIds(vector));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("decisionCases")
        @DisplayName("a decision vector is written byte for byte and reads back unchanged")
        void whenDecisionVectorIsWrittenThenItGivesItsBinaryFormAndReadsBack(Case vector) throws Exception {
            val decision = decisionOf(vector);
            val expected = WireCases.form(vector.expectation(), "binary").orElseThrow();
            val written  = SaplBinaryCodec.writeAuthorizationDecision(decision);
            val readBack = SaplBinaryCodec.readAuthorizationDecision(written);
            assertThat(List.of(HEX.formatHex(written), readBack)).containsExactly(HEX.formatHex(expected), decision);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("subscriptionCases")
        @DisplayName("an accepted subscription vector is written again and reads back unchanged")
        void whenAcceptedSubscriptionVectorIsWrittenAgainThenItReadsBackUnchanged(Case vector) {
            val payload = WireCases.bytesOfHex(vector.document());
            val read    = outcome(() -> SaplBinaryCodec.readAuthorizationSubscription(payload));
            if (read instanceof AuthorizationSubscription subscription) {
                val readBack = outcome(() -> SaplBinaryCodec
                        .readAuthorizationSubscription(SaplBinaryCodec.writeAuthorizationSubscription(subscription)));
                assertThat(readBack).isEqualTo(subscription);
            }
        }

        private static Object expectedSubscription(Case vector) {
            val refusal = refusal(vector);
            if (refusal != null) {
                return refusal;
            }
            final Function<String, Value> part = name -> WireCases
                    .value(WireCases.field(vector.expectation(), name).orElseThrow());
            return new AuthorizationSubscription(part.apply("subject"), part.apply("action"), part.apply("resource"),
                    part.apply("environment"), (ObjectValue) part.apply("secrets"));
        }

        private static Object expectedIds(Case vector) {
            val refusal = refusal(vector);
            if (refusal != null) {
                return refusal;
            }
            val ids = (ArrayValue) WireCases.value(WireCases.field(vector.expectation(), "ids").orElseThrow());
            return ids.stream().map(id -> ((TextValue) id).value()).collect(Collectors.toSet());
        }

        private static Reason refusal(Case vector) {
            return WireCases.field(vector.expectation(), "refused").map(reason -> switch (reason) {
            case "malformed"              -> MALFORMED;
            case "invalid number"         -> INVALID_NUMBER;
            case "too deep"               -> TOO_DEEP;
            case "secrets not an object"  -> Reason.SECRETS_NOT_AN_OBJECT;
            case "too many subscriptions" -> TOO_MANY_SUBSCRIPTIONS;
            case "blank id"               -> Reason.BLANK_SUBSCRIPTION_ID;
            case "duplicate id"           -> DUPLICATE_SUBSCRIPTION_ID;
            default                       ->
                throw new IllegalArgumentException(vector.origin() + " names the unknown reason " + reason + ".");
            }).orElse(null);
        }

        private static Set<String> ids(MultiAuthorizationSubscription subscriptions) {
            return StreamSupport.stream(subscriptions.spliterator(), false)
                    .map(IdentifiableAuthorizationSubscription::subscriptionId).collect(Collectors.toSet());
        }

        private static AuthorizationDecision decisionOf(Case vector) {
            val document    = vector.document();
            val verdict     = Decision.valueOf(WireCases.field(document, "decision").orElseThrow());
            val obligations = WireCases.field(document, "obligations").map(WireCases::value).map(ArrayValue.class::cast)
                    .orElse(Value.EMPTY_ARRAY);
            val advice      = WireCases.field(document, "advice").map(WireCases::value).map(ArrayValue.class::cast)
                    .orElse(Value.EMPTY_ARRAY);
            val resource    = WireCases.field(document, "resource")
                    .map(notation -> "error".equals(notation) ? Value.error(ERROR_MESSAGE) : WireCases.value(notation))
                    .orElse(Value.UNDEFINED);
            return new AuthorizationDecision(verdict, obligations, advice, resource);
        }
    }

    @Nested
    @DisplayName("Decisions")
    class DecisionTests {

        static Stream<Arguments> malformedDecisions() {
            return Stream.of(arguments("the empty payload", "", MALFORMED),
                    arguments("only the lead byte", "00", MALFORMED),
                    arguments("cut off after the verdict", "00 00 01", MALFORMED),
                    arguments("cut off before the resource", "00 00 01 00 00", MALFORMED),
                    arguments("a byte after the resource", "00 00 01 00 00 00 00", MALFORMED),
                    arguments("a permit without the lead byte", "00 01 00 00 00", MALFORMED),
                    arguments("the lead byte of another version", "01 00 01 00 00 00", MALFORMED),
                    arguments("a permit in protobuf", "08 01", MALFORMED),
                    arguments("more obligations than the payload has bytes", "00 00 01 ff 7f 00", MALFORMED),
                    arguments("an obligation with the scale 1001", "00 00 01 01 05 02 d2 0f 00 00", INVALID_NUMBER));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("malformedDecisions")
        @DisplayName("a payload that is no decision is refused for its reason")
        void whenPayloadIsNoDecisionThenItIsRefused(String description, String hex, Reason expected) {
            val payload = WireCases.bytesOfHex(hex);
            assertThat(outcome(() -> SaplBinaryCodec.readAuthorizationDecision(payload))).isEqualTo(expected);
        }

        @Test
        @DisplayName("a verdict the format does not know is indeterminate")
        void whenVerdictIsUnknownThenDecisionIsIndeterminate() {
            val payload = WireCases.bytesOfHex("00 00 09 00 00 00");
            assertThat(outcome(() -> SaplBinaryCodec.readAuthorizationDecision(payload)))
                    .isEqualTo(AuthorizationDecision.INDETERMINATE);
        }

        @Test
        @DisplayName("decisions under ids are written and read back unchanged")
        void whenIdentifiableAndMultiDecisionsAreWrittenThenTheyReadBackUnchanged() throws Exception {
            val identifiable = new IdentifiableAuthorizationDecision("a", denyWithEverything());
            val multi        = new MultiAuthorizationDecision();
            multi.setDecision("a", denyWithEverything());
            multi.setDecision("b", AuthorizationDecision.PERMIT);
            val readIdentifiable = SaplBinaryCodec.readIdentifiableAuthorizationDecision(
                    SaplBinaryCodec.writeIdentifiableAuthorizationDecision(identifiable));
            val readMulti        = SaplBinaryCodec
                    .readMultiAuthorizationDecision(SaplBinaryCodec.writeMultiAuthorizationDecision(multi));
            assertThat(List.of(readIdentifiable, readMulti)).containsExactly(identifiable, multi);
        }

        @Test
        @DisplayName("a decision under an id that is cut off anywhere is refused")
        void whenIdentifiableDecisionIsCutOffAnywhereThenItIsRefused() throws Exception {
            val payload  = SaplBinaryCodec.writeIdentifiableAuthorizationDecision(
                    new IdentifiableAuthorizationDecision("a", denyWithEverything()));
            val accepted = IntStream.range(0, payload.length)
                    .filter(length -> !(outcome(() -> SaplBinaryCodec
                            .readIdentifiableAuthorizationDecision(Arrays.copyOf(payload, length))) instanceof Reason))
                    .boxed().toList();
            assertThat(accepted).isEmpty();
        }

        @Test
        @DisplayName("a multi-decision with an id given twice is refused")
        void whenMultiDecisionHasAnIdTwiceThenItIsRefused() {
            val payload = WireCases.bytesOfHex("00 02 61 61 02 01 01 00 00 00 01 01 00 00 00");
            assertThat(outcome(() -> SaplBinaryCodec.readMultiAuthorizationDecision(payload)))
                    .isEqualTo(DUPLICATE_SUBSCRIPTION_ID);
        }

        private static AuthorizationDecision denyWithEverything() {
            return new AuthorizationDecision(Decision.DENY, (ArrayValue) Value.ofJson("[{\"type\":\"log\"}]"),
                    (ArrayValue) Value.ofJson("[\"hint\",2.5]"), Value.ofJson("{\"name\":\"Wilbur\",\"age\":7}"));
        }
    }

    @Nested
    @DisplayName("Subscriptions")
    class SubscriptionTests {

        @Test
        @DisplayName("a multi-subscription is written and reads back unchanged")
        void whenMultiSubscriptionIsWrittenThenItReadsBackUnchanged() throws Exception {
            val subscriptions = new MultiAuthorizationSubscription()
                    .addSubscription("a", subscriptionWithSubject(Value.of("alice"))).addSubscription("b",
                            subscriptionWithSubject(Value.ofJson("{\"name\":\"bob\",\"roles\":[1,2.50]}")));
            val readBack      = SaplBinaryCodec.readMultiAuthorizationSubscription(
                    SaplBinaryCodec.writeMultiAuthorizationSubscription(subscriptions), MAX_SUBSCRIPTIONS);
            assertThat(readBack).isEqualTo(subscriptions);
        }

        @Test
        @DisplayName("a multi-subscription with more subscriptions than allowed is refused before any is read")
        void whenMultiSubscriptionAnnouncesMoreThanAllowedThenItIsRefused() {
            val payload = WireCases.bytesOfHex("00 00 80 89 7a");
            assertThat(outcome(() -> SaplBinaryCodec.readMultiAuthorizationSubscription(payload, MAX_SUBSCRIPTIONS)))
                    .isEqualTo(TOO_MANY_SUBSCRIPTIONS);
        }

        @Test
        @DisplayName("arrays nested to the limit are written, one level more is refused")
        void whenArraysAreNestedToTheLimitOrBeyondThenOnlyTheLimitIsWritten() {
            val atLimit = subscriptionWithSubject(nestedArray(DEPTH_LIMIT));
            val beyond  = subscriptionWithSubject(nestedArray(DEPTH_LIMIT + 1));
            val written = List.of(
                    outcome(() -> SaplBinaryCodec
                            .readAuthorizationSubscription(SaplBinaryCodec.writeAuthorizationSubscription(atLimit))),
                    outcome(() -> SaplBinaryCodec.writeAuthorizationSubscription(beyond)));
            assertThat(written).containsExactly(atLimit, TOO_DEEP);
        }

        @Test
        @DisplayName("a text with an unpaired surrogate, which UTF-8 cannot carry, is refused")
        void whenTextHoldsAnUnpairedSurrogateThenWritingIsRefused() {
            val subscription = subscriptionWithSubject(Value.of("a\uD800b"));
            assertThat(outcome(() -> SaplBinaryCodec.writeAuthorizationSubscription(subscription)))
                    .isEqualTo(UNPAIRED_SURROGATE);
        }

        private static Value nestedArray(int depth) {
            Value value = Value.EMPTY_ARRAY;
            for (var level = 1; level < depth; level++) {
                value = new ArrayValue(new Value[] { value });
            }
            return value;
        }
    }

    private static AuthorizationSubscription subscriptionWithSubject(Value subject) {
        return new AuthorizationSubscription(subject, Value.of("read"), Value.of("doc"), Value.UNDEFINED,
                Value.EMPTY_OBJECT);
    }

    /** The subscription as a vector states it: an error in a part counts as any error. */
    private static AuthorizationSubscription asStated(AuthorizationSubscription subscription) {
        return new AuthorizationSubscription(WireCases.withAnyError(subscription.subject()),
                WireCases.withAnyError(subscription.action()), WireCases.withAnyError(subscription.resource()),
                WireCases.withAnyError(subscription.environment()), subscription.secrets());
    }
}
