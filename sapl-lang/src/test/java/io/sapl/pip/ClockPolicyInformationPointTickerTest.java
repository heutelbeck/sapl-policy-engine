/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.pip;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.functions.StandardFunctionLibrary;
import io.sapl.functions.TemporalFunctionLibrary;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.interpreter.pip.AttributeContext;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.stubbing.Answer;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

class ClockPolicyInformationPointTickerTest {

    private static final Flux<Val> SYSTEM_DEFAULT_TIMEZONE_FLUX = Flux.just(Val.of(ZoneId.systemDefault().toString()));

    static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    static final DefaultSAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();
    static final AttributeContext ATTRIBUTE_CTX = new AnnotationAttributeContext();
    static final FunctionContext FUNCTION_CTX = new AnnotationFunctionContext();
    static final Map<String, JsonNode> SYSTEM_VARIABLES = Collections.unmodifiableMap(new HashMap<>());
    static final EvaluationContext PDP_EVALUATION_CONTEXT = new EvaluationContext(ATTRIBUTE_CTX, FUNCTION_CTX,
            SYSTEM_VARIABLES);

    static final String authzSubscription = "{ \"subject\": \"somebody\", \"action\": \"read\", \"resource\": {}, \"environment\": {}}";

    static AuthorizationSubscription authzSubscriptionObj;

    @BeforeAll
    static void beforeClass() throws InitializationException, JsonProcessingException {
        FUNCTION_CTX.loadLibrary(new StandardFunctionLibrary());
        FUNCTION_CTX.loadLibrary(new TemporalFunctionLibrary());
        ATTRIBUTE_CTX.loadPolicyInformationPoint(new ClockPolicyInformationPoint());
        authzSubscriptionObj = MAPPER.readValue(authzSubscription, AuthorizationSubscription.class);
    }

    @Test
    void test_streamingPolicyWithVirtualTime() throws InitializationException {

        final ClockPolicyInformationPoint clockPip = new ClockPolicyInformationPoint();
        StepVerifier.withVirtualTime(() -> clockPip.ticker(Val.UNDEFINED, Collections.emptyMap(), Flux.just(Val.of(2000L)), SYSTEM_DEFAULT_TIMEZONE_FLUX))
                .expectSubscription()
                .assertNext(val -> assertThat(val.isError(), is(false)))
                .expectNoEvent(Duration.ofSeconds(2))
                .thenAwait()
                .assertNext(val -> assertThat(val.isError(), is(false)))
                .expectNoEvent(Duration.ofSeconds(2))
                .thenAwait()
                .assertNext(val -> assertThat(val.isError(), is(false)))
                .expectNoEvent(Duration.ofSeconds(2))
                .thenAwait()
                .assertNext(val -> assertThat(val.isError(), is(false)))
                .expectNoEvent(Duration.ofSeconds(2))
                .thenAwait()
                .assertNext(val -> assertThat(val.isError(), is(false)))
                .expectNoEvent(Duration.ofSeconds(2))
                .thenAwait()
                .assertNext(val -> assertThat(val.isError(), is(false)))
                .thenCancel().verify();
    }

    @Test
    void ticker() {
        final ClockPolicyInformationPoint clockPip = new ClockPolicyInformationPoint();
        StepVerifier.withVirtualTime(() -> clockPip.ticker(Val.UNDEFINED, Collections.emptyMap(), Flux.just(Val.of(30000L)), SYSTEM_DEFAULT_TIMEZONE_FLUX))
                .expectSubscription()
                .expectNoEvent(Duration.ofSeconds(30)).consumeNextWith(node -> {
                    /* the first node is provided some nano seconds after its creation */
                })
                .expectNoEvent(Duration.ofSeconds(30)).consumeNextWith(node -> {
                    var localDateTime = LocalDateTime.now();
                    var actual = TemporalFunctionLibrary.atLocal(node).get().textValue();
                    var expected = localDateTime.truncatedTo(ChronoUnit.SECONDS).toString();
                    assertEquals(expected, actual);
                }).expectNoEvent(Duration.ofSeconds(30)).consumeNextWith(node -> {
                    var localTime = LocalTime.now();
                    var localNode = TemporalFunctionLibrary.atLocal(node);
                    var actual = TemporalFunctionLibrary.localTime(localNode).get().textValue();
                    var expected = localTime.truncatedTo(ChronoUnit.SECONDS).toString();
                    assertEquals(expected, actual);
                }).expectNoEvent(Duration.ofSeconds(30)).consumeNextWith(node -> {
                    var localTime = LocalTime.now();
                    var localNode = TemporalFunctionLibrary.atLocal(node);
                    var actual = TemporalFunctionLibrary.localHour(localNode).get().numberValue();
                    var expected = BigDecimal.valueOf(localTime.getHour());
                    assertEquals(expected.longValue(), actual.longValue());
                }).expectNoEvent(Duration.ofSeconds(30)).consumeNextWith(node -> {
                    var localTime = LocalTime.now();
                    var localNode = TemporalFunctionLibrary.atLocal(node);
                    var actual = TemporalFunctionLibrary.localMinute(localNode).get().numberValue();
                    var expected = BigDecimal.valueOf(localTime.getMinute());
                    assertEquals(expected.longValue(), actual.longValue());
                }).expectNoEvent(Duration.ofSeconds(30)).consumeNextWith(node -> {
                    var localTime = LocalTime.now();
                    var localNode = TemporalFunctionLibrary.atLocal(node);
                    var actual = TemporalFunctionLibrary.localSecond(localNode).get().numberValue();
                    var expected = BigDecimal.valueOf(localTime.getSecond());
                    assertEquals(expected.longValue(), actual.longValue());
                }).thenCancel().verify();
    }

    @Test
    void testClockNowAfter() throws Exception {
        var clockPip = new ClockPolicyInformationPoint();

        var tomorrowAtMidnight = LocalDate.now().plusDays(1).atStartOfDay();
        var todayJustBeforeMidnight = tomorrowAtMidnight.minus(30, ChronoUnit.SECONDS);
        var time = Val.of("23:59:30");

        AtomicBoolean atomicBoolean = new AtomicBoolean(false);
        try (MockedStatic<LocalTime> mock = mockStatic(LocalTime.class, CALLS_REAL_METHODS)) {

            mock.when(LocalTime::now).thenAnswer((Answer<?>) invocation -> {
                var beforeMidnight = atomicBoolean.get();
                atomicBoolean.set(!beforeMidnight);

                if (beforeMidnight) {
                    return todayJustBeforeMidnight.plusSeconds(5L).toLocalTime();
                } else {
                    return tomorrowAtMidnight.plusSeconds(5L).toLocalTime();
                }
            });

            StepVerifier.withVirtualTime(() -> clockPip.nowIsAfter(Val.UNDEFINED, Collections.emptyMap(), Flux.just(time), SYSTEM_DEFAULT_TIMEZONE_FLUX))
                    .expectSubscription()
                    .thenAwait(Duration.ofDays(1L))
                    .consumeNextWith(val -> {
                        // on same day just before midnight
                        //      -> clock should slightly be after reference time (23:59:30)
                        var isAfter = val.getBoolean();
                        assertThat(isAfter, is(true));
                    })
                    .consumeNextWith(val -> {
                        // exactly at midnight
                        //      -> clock should definitely be before reference time  (23:59:30)
                        var isAfter = val.getBoolean();
                        assertThat(isAfter, is(false));
                    })
                    .thenAwait(Duration.ofDays(2L))
                    .expectNextCount(4)
                    .expectNoEvent(Duration.ofHours(23))
                    .thenAwait(Duration.ofDays(1L))
                    .thenCancel().verify();
        }

    }

    @Test
    void testClockNowBefore() throws Exception {
        var clockPip = new ClockPolicyInformationPoint();

        var tomorrowAtMidnight = LocalDate.now().plusDays(1).atStartOfDay();
        var todayJustBeforeMidnight = tomorrowAtMidnight.minus(30, ChronoUnit.SECONDS);
        var time = Val.of("23:59:30");

        AtomicBoolean atomicBoolean = new AtomicBoolean(false);
        try (MockedStatic<LocalTime> mock = mockStatic(LocalTime.class, CALLS_REAL_METHODS)) {

            mock.when(LocalTime::now).thenAnswer((Answer<?>) invocation -> {
                var beforeMidnight = atomicBoolean.get();
                atomicBoolean.set(!beforeMidnight);

                if (beforeMidnight) {
                    return todayJustBeforeMidnight.plusSeconds(5L).toLocalTime();
                } else {
                    return tomorrowAtMidnight.plusSeconds(5L).toLocalTime();
                }
            });

            StepVerifier.withVirtualTime(() -> clockPip.nowIsBefore(Val.UNDEFINED, Collections.emptyMap(), Flux.just(time), SYSTEM_DEFAULT_TIMEZONE_FLUX))
                    .expectSubscription()
                    .thenAwait(Duration.ofDays(1L))
                    .consumeNextWith(val -> {
                        // on same day just before midnight
                        //      -> clock should be slightly after reference time (23:59:30)
                        var isAfter = val.getBoolean();
                        assertThat(isAfter, is(false));
                    })
                    .consumeNextWith(val -> {
                        // exactly at midnight
                        //      -> clock should definitely before reference time  (23:59:30)
                        var isAfter = val.getBoolean();
                        assertThat(isAfter, is(true));
                    })
                    .thenAwait(Duration.ofDays(2L))
                    .expectNextCount(4)
                    .expectNoEvent(Duration.ofHours(23))
                    .thenAwait(Duration.ofDays(1L))
                    .thenCancel().verify();
        }
    }

    @Test
    void testPeriodicToggle() throws Exception {
        var clockPip = new ClockPolicyInformationPoint();
        var nowLocalTime = Val.of(LocalTime.now().plusSeconds(5L).format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        var initiallyAuthorized = true;

        StepVerifier.withVirtualTime(() -> clockPip.periodicToggle(Val.UNDEFINED, Collections.emptyMap(),
                        Flux.just(Val.of(initiallyAuthorized)), Flux.just(Val.of(3000L)), Flux.just(Val.of(1000L)), Flux.just(nowLocalTime), SYSTEM_DEFAULT_TIMEZONE_FLUX))
                .expectSubscription()
                // .thenAwait(Duration.ofSeconds(10L))
                .consumeNextWith(val -> {
                    assertThat(val.getBoolean(), is(true));
                })
                .expectNoEvent(Duration.ofSeconds(7))
                .consumeNextWith(val -> {
                    assertThat(val.getBoolean(), is(true));
                })
                .expectNoEvent(Duration.ofSeconds(1))
                .consumeNextWith(val -> {
                    assertThat(val.getBoolean(), is(false));
                })
                .expectNoEvent(Duration.ofSeconds(3L))
                .consumeNextWith(val -> {
                    assertThat(val.getBoolean(), is(true));
                })
                .thenCancel().verify();
    }

    @Test
    void timeZoneTest() {
        var clockPip = new ClockPolicyInformationPoint();
        var timeZone = clockPip.timeZone(Val.UNDEFINED, Collections.emptyMap()).blockFirst().getText();

        assertThat(timeZone, is(ZoneId.systemDefault().toString()));
    }

    @Test
    void nowTest() {
        var clockPip = new ClockPolicyInformationPoint();
        var now = OffsetDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        // var nowInSystemTimeZone = clockPip.now(SYSTEM_TIMEZONE_VAL, Collections.emptyMap()).blockFirst().getText();
        var nowInSystemTimeZone = clockPip.ticker(Val.UNDEFINED, Collections.emptyMap(), Flux.just(Val.of(1000L)), SYSTEM_DEFAULT_TIMEZONE_FLUX)
                .blockFirst().getText();

        var offsetDateTime = DateTimeFormatter.ISO_DATE_TIME.parse(nowInSystemTimeZone, OffsetDateTime::from).truncatedTo(ChronoUnit.MINUTES);

        assertThat(offsetDateTime.compareTo(now), is(0));
    }

    @Test
    void millisTest() {
        var clockPip = new ClockPolicyInformationPoint();
        var millis = clockPip.millis(Val.UNDEFINED, Collections.emptyMap(), SYSTEM_DEFAULT_TIMEZONE_FLUX).blockFirst().get().numberValue()
                .longValue();

        assertThat(millis, is(greaterThanOrEqualTo(Instant.EPOCH.toEpochMilli())));
        assertThat(millis, is(lessThanOrEqualTo(System.currentTimeMillis())));
    }

    @Test
    void timerTest() {
        var clockPip = new ClockPolicyInformationPoint();
        var timerMillis = 30000L;

        StepVerifier.withVirtualTime(() -> clockPip.trueIn(Val.UNDEFINED, Collections.emptyMap(), Flux.just(Val.of(timerMillis))))
                .expectSubscription()
                .consumeNextWith(val -> {
                    assertThat(val.getBoolean(), is(false));
                })
                .expectNoEvent(Duration.ofMillis(timerMillis))
                .thenAwait(Duration.ofMillis(timerMillis))
                .consumeNextWith(val -> {
                    assertThat(val.getBoolean(), is(true));
                })
                .expectComplete().verify();
    }

    @Test
    void policyWithMillisBody() {
        var policyDefinition = "policy \"test\" " +
                "   permit action == \"read\" " +
                "   where " +
                "       var millis = <clock.millis(\"UTC\")>; " +
                "       var instant = time.ofEpochMillis(millis); " +
                "       time.validUTC(instant);";
        var expectedAuthzDecision = AuthorizationDecision.PERMIT;
        assertThatPolicyEvaluatesTo(policyDefinition, expectedAuthzDecision);
    }

    @Test
    void policyWithTimeZoneBody() {
        var policyDefinition = "policy \"test\" " +
                "   permit action == \"read\" " +
                "   where " +
                "       var timeZone = <clock.timeZone>; " +
                "       standard.length(timeZone) > 0; ";

        var expectedAuthzDecision = AuthorizationDecision.PERMIT;
        assertThatPolicyEvaluatesTo(policyDefinition, expectedAuthzDecision);
    }

    @Test
    void policyWithTrueInBody() {
        var policyDefinition = "policy \"test\" " +
                "   permit action == \"read\" " +
                "   where " +
                "      <clock.trueIn(5000)>; ";

        StepVerifier.withVirtualTime(() -> INTERPRETER.evaluate(authzSubscriptionObj, policyDefinition, PDP_EVALUATION_CONTEXT))
                .expectSubscription()
                .consumeNextWith(authzDecision -> {
                    assertThat(authzDecision, CoreMatchers.is(AuthorizationDecision.NOT_APPLICABLE));
                })
                .expectNoEvent(Duration.ofSeconds(5))
                .thenAwait(Duration.ofSeconds(5))
                .consumeNextWith(authzDecision -> {
                    assertThat(authzDecision, CoreMatchers.is(AuthorizationDecision.PERMIT));
                })
                .expectComplete().verify();
    }


    @Test
    void policyWithTrueForBody() {
        var policyDefinition = "policy \"test\" " +
                "   permit action == \"read\" " +
                "   where " +
                "      <clock.trueFor(5000)>; ";

        StepVerifier.withVirtualTime(() -> INTERPRETER.evaluate(authzSubscriptionObj, policyDefinition, PDP_EVALUATION_CONTEXT))
                .expectSubscription()
                .consumeNextWith(authzDecision -> {
                    assertThat(authzDecision, CoreMatchers.is(AuthorizationDecision.PERMIT));
                })
                .expectNoEvent(Duration.ofSeconds(5))
                .thenAwait(Duration.ofSeconds(5))
                .consumeNextWith(authzDecision -> {
                    assertThat(authzDecision, CoreMatchers.is(AuthorizationDecision.NOT_APPLICABLE));
                })
                .expectComplete().verify();
    }

    @Test
    void policyWithClockAfterBody() {
        var policyDefinition = "policy \"test\" " +
                "   permit action == \"read\" " +
                "   where " +
                "       var after =|<clock.nowIsAfter(\"00:00\",\"system\")>; " +
                "       var before =|<clock.nowIsBefore(\"23:59\",\"system\")>; " +
                "       after && before;";
        var expectedAuthzDecision = AuthorizationDecision.PERMIT;
        assertThatPolicyEvaluatesTo(policyDefinition, expectedAuthzDecision);
    }

    @Test
    void policyWithClockBetweenBody() {
        var policyDefinition = "policy \"test\" " +
                "   permit action == \"read\" " +
                "   where " +
                "       |<clock.nowIsBetween(\"00:00\",\"23:59\",\"system\")>; ";
        assertThatPolicyEvaluatesTo(policyDefinition, AuthorizationDecision.PERMIT);

        policyDefinition = "policy \"test\" " +
                "   permit action == \"read\" " +
                "   where " +
                "       |<clock.nowIsBetween(\"23:59\",\"00:00\",\"system\")>; ";
        assertThatPolicyEvaluatesTo(policyDefinition, AuthorizationDecision.NOT_APPLICABLE);
    }

    private void assertThatPolicyEvaluatesTo(String policyDefinition, AuthorizationDecision expectedAuthzDecision) {
        StepVerifier.create(INTERPRETER.evaluate(authzSubscriptionObj, policyDefinition, PDP_EVALUATION_CONTEXT))
                .assertNext(authzDecision -> {
                    assertThat(authzDecision, CoreMatchers.is(expectedAuthzDecision));
                }).verifyComplete();
    }
}
