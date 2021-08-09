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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import io.sapl.api.interpreter.Val;
import io.sapl.functions.TemporalFunctionLibrary;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.stubbing.Answer;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNotNull;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class ClockPolicyInformationPointTickerTest {

    @Test
    public void ticker() {
        final ClockPolicyInformationPoint clockPip = new ClockPolicyInformationPoint();
        StepVerifier.withVirtualTime(() -> clockPip.ticker(Val.UNDEFINED, Collections.emptyMap(), Flux.just(Val.of(30L)))).expectSubscription()
                .expectNoEvent(Duration.ofSeconds(30)).consumeNextWith(node -> {
                    /* the first node is provided some nano seconds after its creation */
                })
                .expectNoEvent(Duration.ofSeconds(30)).consumeNextWith(node -> {
                    var localDateTime = LocalDateTime.now();
                    var actual = TemporalFunctionLibrary.localDateTime(node).get().textValue();
                    var expected = localDateTime.truncatedTo(ChronoUnit.SECONDS).toString();
                    assertEquals(expected, actual);
                }).expectNoEvent(Duration.ofSeconds(30)).consumeNextWith(node -> {
                    var localTime = LocalTime.now();
                    var actual = TemporalFunctionLibrary.localTime(node).get().textValue();
                    var expected = localTime.truncatedTo(ChronoUnit.SECONDS).toString();
                    assertEquals(expected, actual);
                }).expectNoEvent(Duration.ofSeconds(30)).consumeNextWith(node -> {
                    var localTime = LocalTime.now();
                    var actual = TemporalFunctionLibrary.localHour(node).get().numberValue();
                    var expected = BigDecimal.valueOf(localTime.getHour());
                    assertEquals(expected.longValue(), actual.longValue());
                }).expectNoEvent(Duration.ofSeconds(30)).consumeNextWith(node -> {
                    var localTime = LocalTime.now();
                    var actual = TemporalFunctionLibrary.localMinute(node).get().numberValue();
                    var expected = BigDecimal.valueOf(localTime.getMinute());
                    assertEquals(expected.longValue(), actual.longValue());
                }).expectNoEvent(Duration.ofSeconds(30)).consumeNextWith(node -> {
                    var localTime = LocalTime.now();
                    var actual = TemporalFunctionLibrary.localSecond(node).get().numberValue();
                    var expected = BigDecimal.valueOf(localTime.getSecond());
                    assertEquals(expected.longValue(), actual.longValue());
                }).thenCancel().verify();
    }

    @Test
    void testConvertToZoneId() {
        var jsonNodeMock = mock(JsonNode.class);
        when(jsonNodeMock.asText()).thenReturn(null);

        var clockPip = new ClockPolicyInformationPoint();
        var now = clockPip.now(Val.of(jsonNodeMock), Collections.emptyMap()).blockFirst();

        assertThat(now, notNullValue());
        assertThat(now.getValType(), is(JsonNodeType.STRING.toString()));
    }

    @Test
    void testClockAfter() throws Exception {
        var jsonNodeMock = mock(JsonNode.class);
        when(jsonNodeMock.asText()).thenReturn(null);
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

            StepVerifier.withVirtualTime(() -> clockPip.clockAfter(Val.of(jsonNodeMock), Collections.emptyMap(), time))
                    .expectSubscription()
                    .thenAwait(Duration.ofDays(1L))
                    .consumeNextWith(val -> {
                        // on same day just before midnight
                        //      -> clock should be slightly after reference time (23:59:30)
                        var isAfter = val.getBoolean();
                        assertThat(isAfter, is(true));
                    })
                    .consumeNextWith(val -> {
                        // exactly at midnight
                        //      -> clock should definitely before reference time  (23:59:30)
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

}
