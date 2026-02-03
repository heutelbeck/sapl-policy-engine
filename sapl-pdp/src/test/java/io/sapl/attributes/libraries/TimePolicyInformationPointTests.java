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
package io.sapl.attributes.libraries;

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.attributes.CachingAttributeBroker;
import io.sapl.attributes.InMemoryAttributeRepository;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("TimePolicyInformationPoint")
class TimePolicyInformationPointTests {

    @Test
    void whenNowThenEmitsUpdates() {
        val now        = Instant.parse("2021-11-08T13:00:00Z");
        val nowPlusOne = Instant.parse("2021-11-08T13:00:01Z");
        val nowPlusTwo = Instant.parse("2021-11-08T13:00:02Z");
        val clock      = mock(Clock.class);
        when(clock.instant()).thenReturn(now, nowPlusOne, nowPlusTwo);
        val sut = new TimePolicyInformationPoint(clock);
        StepVerifier.withVirtualTime(sut::now).expectNext(Value.of(now.toString())).thenAwait(Duration.ofSeconds(2))
                .expectNext(Value.of(nowPlusOne.toString()), Value.of(nowPlusTwo.toString())).thenCancel().verify();
    }

    @Test
    void whenNowWithZeroDelayThenFails() {
        val clock = mock(Clock.class);
        val sut   = new TimePolicyInformationPoint(clock);
        StepVerifier.<Value>withVirtualTime(() -> sut.now(Value.of(0L))).expectNextMatches(ErrorValue.class::isInstance)
                .verifyComplete();
    }

    @Test
    void whenSystemTimeZoneThenIsRetrieved() {
        val sut = new TimePolicyInformationPoint(mock(Clock.class)).systemTimeZone().next();
        StepVerifier.create(sut).expectNextMatches(TextValue.class::isInstance).verifyComplete();
    }

    @Test
    void whenNowIsAfterThenEmitsCorrectValues() {
        val startingTime = Instant.parse("2021-11-08T13:00:00Z");
        val checkpoint   = Value.of("2021-11-08T14:30:00Z");
        val clock        = mock(Clock.class);
        when(clock.instant()).thenReturn(startingTime);
        val sut = new TimePolicyInformationPoint(clock);
        StepVerifier.<Value>withVirtualTime(() -> sut.nowIsAfter(checkpoint)).expectNext(Value.FALSE)
                .thenAwait(Duration.ofMinutes(91L)).expectNext(Value.TRUE).verifyComplete();
    }

    @Test
    void whenLocalTimeIsAfterMidnightThenAlwaysTrue() {
        val startingTime = Instant.parse("2021-11-08T13:00:00Z");
        val checkpoint   = Value.of(LocalTime.MIN.toString());
        val clock        = mock(Clock.class);
        when(clock.instant()).thenReturn(startingTime);
        val sut = new TimePolicyInformationPoint(clock);
        StepVerifier.<Value>withVirtualTime(() -> sut.localTimeIsAfter(checkpoint)).expectNext(Value.TRUE)
                .verifyComplete();
    }

    @Test
    void whenLocalTimeIsAfterMaxTimeThenNeverTrue() {
        val startingTime = Instant.parse("2021-11-08T13:00:00Z");
        val checkpoint   = Value.of(LocalTime.MAX.toString());
        val clock        = mock(Clock.class);
        when(clock.instant()).thenReturn(startingTime);
        val sut = new TimePolicyInformationPoint(clock);
        StepVerifier.<Value>withVirtualTime(() -> sut.localTimeIsAfter(checkpoint)).expectNext(Value.FALSE)
                .verifyComplete();
    }

    @Test
    void whenLocalTimeIsBetweenNullSizeIntervalThenNeverTrue() {
        val startingTime = Instant.parse("2021-11-08T13:00:00Z");
        val start        = Value.of("12:00");
        val end          = Value.of("12:00");
        val clock        = mock(Clock.class);
        when(clock.instant()).thenReturn(startingTime);
        val sut = new TimePolicyInformationPoint(clock);
        StepVerifier.<Value>withVirtualTime(() -> sut.localTimeIsBetween(start, end)).expectNext(Value.FALSE)
                .verifyComplete();
    }

    @Test
    void whenLocalTimeIsBetweenMinMaxIntervalThenAlwaysTrue() {
        val startingTime = Instant.parse("2021-11-08T13:00:00Z");
        val start        = Value.of(LocalTime.MIN.toString());
        val end          = Value.of(LocalTime.MAX.toString());
        val clock        = mock(Clock.class);
        when(clock.instant()).thenReturn(startingTime);
        val sut = new TimePolicyInformationPoint(clock);
        StepVerifier.<Value>withVirtualTime(() -> sut.localTimeIsBetween(start, end)).expectNext(Value.TRUE)
                .verifyComplete();
    }

    @Test
    void whenLocalTimeIntervalStartsAtMinButNotTillMaxThenReturnsTrue() {
        val startingTime = Instant.parse("2021-11-08T13:00:00Z");
        val start        = Value.of(LocalTime.MIN.toString());
        val end          = Value.of("22:00");
        val clock        = mock(Clock.class);
        when(clock.instant()).thenReturn(startingTime);
        val sut = new TimePolicyInformationPoint(clock);
        StepVerifier.<Value>withVirtualTime(() -> sut.localTimeIsBetween(start, end)).expectNext(Value.TRUE)
                .thenCancel().verify();
    }

    @Test
    void whenLocalTimeIntervalStartsAtMaxButNotTillMinThenReturnsTrue() {
        val startingTime = Instant.parse("2021-11-08T13:00:00Z");
        val start        = Value.of(LocalTime.MAX.toString());
        val end          = Value.of("14:00");
        val clock        = mock(Clock.class);
        when(clock.instant()).thenReturn(startingTime);
        val sut = new TimePolicyInformationPoint(clock);
        StepVerifier.<Value>withVirtualTime(() -> sut.localTimeIsBetween(start, end)).expectNext(Value.TRUE)
                .thenCancel().verify();
    }

    @Test
    void whenLocalTimeIsBetweenMaxMinIntervalThenAlwaysTrue() {
        val startingTime = Instant.parse("2021-11-08T13:00:00Z");
        val start        = Value.of(LocalTime.MAX.toString());
        val end          = Value.of(LocalTime.MIN.toString());
        val clock        = mock(Clock.class);
        when(clock.instant()).thenReturn(startingTime);
        val sut = new TimePolicyInformationPoint(clock);
        StepVerifier.<Value>withVirtualTime(() -> sut.localTimeIsBetween(start, end)).expectNext(Value.TRUE)
                .verifyComplete();
    }

    @Test
    void whenLocalTimeBetweenStartingBeforeIntervalThenEmitsCorrectSequence() {
        val startingTime = Instant.parse("2021-11-08T13:00:00Z");
        val start        = Value.of("14:00:00");
        val end          = Value.of("15:00");
        val clock        = mock(Clock.class);
        when(clock.instant()).thenReturn(startingTime);
        val sut = new TimePolicyInformationPoint(clock);
        // @formatter:off
		StepVerifier.<Value>withVirtualTime(() -> sut.localTimeIsBetween(start, end))
			.expectNext(Value.FALSE)
			.thenAwait(Duration.ofHours(1L)).expectNext(Value.TRUE)
			.thenAwait(Duration.ofHours(1L)).expectNext(Value.FALSE)
			.thenAwait(Duration.ofHours(23L)).expectNext(Value.TRUE)
			.thenAwait(Duration.ofHours(1L)).expectNext(Value.FALSE)
			.thenAwait(Duration.ofHours(23L)).expectNext(Value.TRUE)
			.thenAwait(Duration.ofHours(1L)).expectNext(Value.FALSE)
			.thenAwait(Duration.ofHours(23L)).expectNext(Value.TRUE)
			.thenCancel().verify();
		// @formatter:on
    }

    @Test
    void whenLocalTimeBetweenStartingBeforeIntervalReversedThenEmitsCorrectSequence() {
        val startingTime = Instant.parse("2021-11-08T13:00:00Z");
        val start        = Value.of("15:00");
        val end          = Value.of("14:00:00");
        val clock        = mock(Clock.class);
        when(clock.instant()).thenReturn(startingTime);
        val sut = new TimePolicyInformationPoint(clock);
        // @formatter:off
		StepVerifier.<Value>withVirtualTime(() -> sut.localTimeIsBetween(start, end))
			.expectNext(Value.TRUE)
			.thenAwait(Duration.ofHours(1L)).expectNext(Value.FALSE)
			.thenAwait(Duration.ofHours(1L)).expectNext(Value.TRUE)
			.thenAwait(Duration.ofHours(23L)).expectNext(Value.FALSE)
			.thenAwait(Duration.ofHours(1L)).expectNext(Value.TRUE)
			.thenAwait(Duration.ofHours(23L)).expectNext(Value.FALSE)
			.thenAwait(Duration.ofHours(1L)).expectNext(Value.TRUE)
			.thenAwait(Duration.ofHours(23L)).expectNext(Value.FALSE)
			.thenCancel().verify();
		// @formatter:on
    }

    @Test
    void whenLocalTimeBetweenStartingInsideOfIntervalThenEmitsCorrectSequence() {
        val startingTime = Instant.parse("2021-11-08T15:00:00Z");
        val start        = Value.of("14:00:00");
        val end          = Value.of("16:00");
        val clock        = mock(Clock.class);
        when(clock.instant()).thenReturn(startingTime);
        val sut = new TimePolicyInformationPoint(clock);
        // @formatter:off
		StepVerifier.<Value>withVirtualTime(() -> sut.localTimeIsBetween(start, end))
			.expectNext(Value.TRUE)
			.thenAwait(Duration.ofHours(1L)).expectNext(Value.FALSE)
			.thenAwait(Duration.ofHours(22L)).expectNext(Value.TRUE)
			.thenAwait(Duration.ofHours(2L)).expectNext(Value.FALSE)
			.thenAwait(Duration.ofHours(22L)).expectNext(Value.TRUE)
			.thenAwait(Duration.ofHours(2L)).expectNext(Value.FALSE)
			.thenAwait(Duration.ofHours(22L)).expectNext(Value.TRUE)
			.thenCancel().verify();
		// @formatter:on
    }

    @Test
    void whenLocalTimeBetweenStartingAfterIntervalThenEmitsCorrectSequence() {
        val startingTime = Instant.parse("2021-11-08T18:00:00Z");
        val start        = Value.of("14:00:00");
        val end          = Value.of("16:00");
        val clock        = mock(Clock.class);
        when(clock.instant()).thenReturn(startingTime);
        val sut = new TimePolicyInformationPoint(clock);
        // @formatter:off
		StepVerifier.<Value>withVirtualTime(() -> sut.localTimeIsBetween(start, end))
			.expectNext(Value.FALSE)
			.thenAwait(Duration.ofHours(20L)).expectNext(Value.TRUE)
			.thenAwait(Duration.ofHours(2L)).expectNext(Value.FALSE)
			.thenAwait(Duration.ofHours(22L)).expectNext(Value.TRUE)
			.thenAwait(Duration.ofHours(2L)).expectNext(Value.FALSE)
			.thenAwait(Duration.ofHours(22L)).expectNext(Value.TRUE)
			.thenAwait(Duration.ofHours(2L)).expectNext(Value.FALSE)
			.thenAwait(Duration.ofHours(22L)).expectNext(Value.TRUE)
			.thenCancel().verify();
		// @formatter:on
    }

    @Test
    void whenStartingTimeIsAfterCheckpointThenEmitsCorrectSequence() {
        val startingTime = Instant.parse("2021-11-08T13:00:00Z");
        val checkpoint   = Value.of("12:00");
        val clock        = mock(Clock.class);
        when(clock.instant()).thenReturn(startingTime);
        val sut = new TimePolicyInformationPoint(clock);
        // @formatter:off
		StepVerifier.<Value>withVirtualTime(() -> sut.localTimeIsAfter(checkpoint))
			.expectNext(Value.TRUE)
			.thenAwait(Duration.ofHours(11L)).expectNext(Value.FALSE)
			.thenAwait(Duration.ofHours(12L)).expectNext(Value.TRUE)
			.thenAwait(Duration.ofHours(12L)).expectNext(Value.FALSE)
			.thenAwait(Duration.ofHours(12L)).expectNext(Value.TRUE)
			.thenAwait(Duration.ofHours(12L)).expectNext(Value.FALSE)
			.thenCancel().verify();
		// @formatter:on
    }

    @Test
    void whenStartingTimeIsBeforeCheckpointThenEmitsCorrectSequence() {
        val startingTime = Instant.parse("2021-11-08T11:00:00Z");
        val checkpoint   = Value.of("12:00");
        val clock        = mock(Clock.class);
        when(clock.instant()).thenReturn(startingTime);
        val sut = new TimePolicyInformationPoint(clock);
        // @formatter:off
		StepVerifier.<Value>withVirtualTime(() -> sut.localTimeIsAfter(checkpoint))
			.expectNext(Value.FALSE)
			.thenAwait(Duration.ofHours(1L)).expectNext(Value.TRUE)
			.thenAwait(Duration.ofHours(12L)).expectNext(Value.FALSE)
			.thenAwait(Duration.ofHours(12L)).expectNext(Value.TRUE)
			.thenAwait(Duration.ofHours(12L)).expectNext(Value.FALSE)
			.thenAwait(Duration.ofHours(12L)).expectNext(Value.TRUE)
			.thenAwait(Duration.ofHours(12L)).expectNext(Value.FALSE)
			.thenCancel().verify();
		// @formatter:on
    }

    @Test
    void whenStartingTimeIBeforeStartingBeforeThenEmitsCorrectSequence() {
        val startingTime = Instant.parse("2021-11-08T11:00:00Z");
        val checkpoint   = Value.of("12:00");
        val clock        = mock(Clock.class);
        when(clock.instant()).thenReturn(startingTime);
        val sut = new TimePolicyInformationPoint(clock);
        // @formatter:off
		StepVerifier.<Value>withVirtualTime(() -> sut.localTimeIsBefore(checkpoint))
			.expectNext(Value.TRUE)
			.thenAwait(Duration.ofHours(1L)).expectNext(Value.FALSE)
			.thenAwait(Duration.ofHours(12L)).expectNext(Value.TRUE)
			.thenAwait(Duration.ofHours(12L)).expectNext(Value.FALSE)
			.thenAwait(Duration.ofHours(12L)).expectNext(Value.TRUE)
			.thenAwait(Duration.ofHours(12L)).expectNext(Value.FALSE)
			.thenAwait(Duration.ofHours(12L)).expectNext(Value.TRUE)
			.thenCancel().verify();
		// @formatter:on
    }

    @Test
    void whenNowIsBetweenStartAfterIntervalThenReturnsFalse() {
        val startingTime  = Instant.parse("2021-11-08T13:00:40Z");
        val intervalStart = Value.of("2021-11-08T13:00:05Z");
        val intervalEnd   = Value.of("2021-11-08T13:00:10Z");

        val clock = mock(Clock.class);
        when(clock.instant()).thenReturn(startingTime);
        val sut = new TimePolicyInformationPoint(clock);

        StepVerifier.<Value>withVirtualTime(() -> sut.nowIsBetween(intervalStart, intervalEnd)).expectNext(Value.FALSE)
                .verifyComplete();
    }

    @Test
    void whenNowIsBetweenStartBetweenThenTransitionsCorrectly() {
        val startingTime  = Instant.parse("2021-11-08T13:00:05Z");
        val intervalStart = Value.of("2021-11-08T13:00:00Z");
        val intervalEnd   = Value.of("2021-11-08T13:00:10Z");

        val clock = mock(Clock.class);
        when(clock.instant()).thenReturn(startingTime);
        val sut = new TimePolicyInformationPoint(clock);
        StepVerifier.<Value>withVirtualTime(() -> sut.nowIsBetween(intervalStart, intervalEnd)).expectNext(Value.TRUE)
                .thenAwait(Duration.ofSeconds(5L)).expectNext(Value.FALSE).verifyComplete();
    }

    @Test
    void whenNowIsBetweenStartBeforeThenTransitionsCorrectly() {
        val startingTime  = Instant.parse("2021-11-08T13:00:00Z");
        val intervalStart = Value.of("2021-11-08T13:00:05Z");
        val intervalEnd   = Value.of("2021-11-08T13:00:10Z");

        val clock = mock(Clock.class);
        when(clock.instant()).thenReturn(startingTime);
        val sut = new TimePolicyInformationPoint(clock);
        StepVerifier.<Value>withVirtualTime(() -> sut.nowIsBetween(intervalStart, intervalEnd)).expectNext(Value.FALSE)
                .thenAwait(Duration.ofSeconds(5L)).expectNext(Value.TRUE).thenAwait(Duration.ofSeconds(5L))
                .expectNext(Value.FALSE).verifyComplete();
    }

    @Test
    void whenNowIsAlwaysAfterCheckpointThenReturnsTrue() {
        val now        = Instant.parse("2021-11-08T13:00:00Z");
        val checkpoint = Value.of("2021-11-01T14:30:00Z");
        val clock      = mock(Clock.class);
        when(clock.instant()).thenReturn(now);
        val sut = new TimePolicyInformationPoint(clock);
        StepVerifier.<Value>withVirtualTime(() -> sut.nowIsAfter(checkpoint)).expectNext(Value.TRUE).verifyComplete();
    }

    @Test
    void whenNowIsBeforeThenTransitionsCorrectly() {
        val now        = Instant.parse("2021-11-08T13:00:00Z");
        val checkpoint = Value.of("2021-11-08T14:30:00Z");
        val clock      = mock(Clock.class);
        when(clock.instant()).thenReturn(now);
        val sut = new TimePolicyInformationPoint(clock);
        StepVerifier.<Value>withVirtualTime(() -> sut.nowIsBefore(checkpoint)).expectNext(Value.TRUE)
                .thenAwait(Duration.ofMinutes(91L)).expectNext(Value.FALSE).verifyComplete();
    }

    @Test
    void whenToggleThenAlternatesCorrectly() {
        val sut = new TimePolicyInformationPoint(mock(Clock.class));
        StepVerifier.<Value>withVirtualTime(() -> sut.toggle(Value.of(5_000L), Value.of(1_000L))).expectNext(Value.TRUE)
                .thenAwait(Duration.ofMillis(5_0000L)).expectNext(Value.FALSE).thenAwait(Duration.ofMillis(1_000L))
                .expectNext(Value.TRUE).thenCancel().verify();
    }

    @Test
    void whenBrokerLoadsTimePipThenLibraryIsAvailable() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        val broker     = new CachingAttributeBroker(repository);
        val pip        = new TimePolicyInformationPoint(Clock.systemUTC());

        broker.loadPolicyInformationPointLibrary(pip);

        assertThat(broker.getLoadedLibraryNames()).contains("time");
    }

    @Test
    void whenLoadLibraryWithoutAnnotationThenThrowsException() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        val broker     = new CachingAttributeBroker(repository);

        class NotAnnotated {
            @SuppressWarnings("unused")
            public Value someAttribute() {
                return Value.of("test");
            }
        }

        assertThatThrownBy(() -> broker.loadPolicyInformationPointLibrary(new NotAnnotated()))
                .hasMessageContaining("must be annotated with @PolicyInformationPoint");
    }

    @Test
    void whenLoadDuplicateLibraryThenThrowsException() {
        val repository = new InMemoryAttributeRepository(Clock.systemUTC());
        val broker     = new CachingAttributeBroker(repository);
        val pip        = new TimePolicyInformationPoint(Clock.systemUTC());

        broker.loadPolicyInformationPointLibrary(pip);

        assertThatThrownBy(
                () -> broker.loadPolicyInformationPointLibrary(new TimePolicyInformationPoint(Clock.systemUTC())))
                .hasMessageContaining("Library already loaded: time");
    }

}
