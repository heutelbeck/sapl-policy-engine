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
import io.sapl.api.interpreter.Val;
import io.sapl.functions.TemporalFunctionLibrary;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Slf4j
public class ClockPolicyInformationPointTickerTest {

    // private static final Logger log = LoggerFactory.getLogger(ClockPolicyInformationPointTickerTest.class);

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
        clockPip.now(Val.of(jsonNodeMock), Collections.emptyMap()).blockFirst();
    }

    @Test
    void doTest() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant ref = Instant.now().minus(1, ChronoUnit.HOURS).plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        log.info("{} is after {} =  {}", now, ref, now.isAfter(ref));

        LocalTime timeNow = now.atZone(ZoneOffset.UTC).toLocalTime();
        LocalTime timeRef = ref.atZone(ZoneOffset.UTC).toLocalTime();
        log.info("{} is after {} =  {}", timeNow, timeRef, timeNow.isAfter(timeRef));

        System.out.println(LocalTime.now().truncatedTo(ChronoUnit.DAYS));

        var d1 = Duration.ofHours(23);
        var d2 = Duration.ofMinutes(78);
        var d3 = Duration.ofSeconds(12);
        log.info("{} is less than {} = {}", d2, d3, d3.compareTo(d1));
    }

    @Test
    void testClockAfter() throws Exception {
        var clockPip = new ClockPolicyInformationPoint();

        assertThat(clockPip.clockAfter(Val.UNDEFINED, Collections.emptyMap(), Val.of(Instant.now().plusSeconds(10L).toString()))
                .blockFirst().getBoolean(), is(false)
        );
        assertThat(clockPip.clockAfter(Val.UNDEFINED, Collections.emptyMap(), Val.of(Instant.now().minusSeconds(10L).toString()))
                .blockFirst().getBoolean(), is(true)
        );

        StepVerifier
                .withVirtualTime(() -> clockPip.clockAfter2(Val.UNDEFINED, Collections.emptyMap(), Val.of(Instant.now().plusSeconds(10L).toString())))
                .expectSubscription()
                .thenAwait(Duration.ofHours(25))
                .expectNextCount(4)
                .thenCancel().verify(Duration.ofMillis(200));

    }

    @Test
    void testClockBefore() {
        var clockPip = new ClockPolicyInformationPoint();

        assertThat(clockPip.clockBefore(Val.UNDEFINED, Collections.emptyMap(), Val.of(Instant.now().plusSeconds(10L).toString()))
                .blockFirst().getBoolean(), is(true)
        );
        assertThat(clockPip.clockBefore(Val.UNDEFINED, Collections.emptyMap(), Val.of(Instant.now().minusSeconds(10L).toString()))
                .blockFirst().getBoolean(), is(false)
        );
    }

}
