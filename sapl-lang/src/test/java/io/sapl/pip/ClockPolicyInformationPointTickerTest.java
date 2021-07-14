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
import java.time.temporal.ChronoUnit;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Slf4j
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
        clockPip.now(Val.of(jsonNodeMock), Collections.emptyMap()).blockFirst();
    }

    @Test
    void testClockAfterWithReferenceTimeAfterNow() {
        var clockPip = new ClockPolicyInformationPoint();

        //FIXME: in case test is executed between 23:59:50 and 23:59:59:
        // 'referenceTimeAfterNow' is on the next day, resulting in a reference time that is actually before now
        // e.g. now = 23:59:50, referenceTimeAfterNow = 00:00:00
        var referenceTimeAfterNow = Val.of(Instant.now().plusSeconds(10L).toString());

        // expected results
        // < "now" : false, "referenceTimeAfterNow" : true, "00:00" : false, "referenceTimeAfterNow" : true, "00:00" : false, ... >

        clockPip.clockAfter(Val.UNDEFINED, Collections.emptyMap(), referenceTimeAfterNow)
                .map(val -> {
                    System.out.println(val.getBoolean());
                    return val;
                })
                .subscribe();


        // StepVerifier.withVirtualTime(() -> clockPip.clockAfter(Val.UNDEFINED, Collections.emptyMap(), referenceTimeAfterNow))
        //         .expectSubscription()
        //         .expectNoEvent(Duration.ofSeconds(30))
        //         .consumeNextWith(node -> {
        //             assertThat(node.getBoolean(), is(false));
        //         })
        //         .expectNoEvent(Duration.ofSeconds(30))
        //         .consumeNextWith(node -> {
        //             assertThat(node.getBoolean(), is(true));
        //         })
        //         .thenCancel().verify();
    }

}
