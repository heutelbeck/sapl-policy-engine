package io.sapl.pip;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

public class TimePolicyInformationPointTests {

	@Test
	public void contextIsAbleToLoadTimePolicyInformationPoint() {
		var sut = new TimePolicyInformationPoint(mock(Clock.class));
		assertDoesNotThrow(() -> new AnnotationAttributeContext(sut));
	}

	@Test
	public void now_EmitsUpdates() {
		var now = Instant.parse("2021-11-08T13:00:00Z");
		var nowPlusOne = Instant.parse("2021-11-08T13:00:01Z");
		var nowPlusTwo = Instant.parse("2021-11-08T13:00:02Z");
		var clock = mock(Clock.class);
		when(clock.instant()).thenReturn(now, nowPlusOne, nowPlusTwo);
		var sut = new TimePolicyInformationPoint(clock);
		StepVerifier.withVirtualTime(() -> sut.now()).expectNext(Val.of(now.toString()))
				.thenAwait(Duration.ofSeconds(2))
				.expectNext(Val.of(nowPlusOne.toString()), Val.of(nowPlusTwo.toString())).thenCancel().verify();
	}

	@Test
	public void now_zeroDelay_Fails() {
		var clock = mock(Clock.class);
		var sut = new TimePolicyInformationPoint(clock);
		StepVerifier.withVirtualTime(() -> sut.now(Flux.just(Val.of(0L)))).expectError(PolicyEvaluationException.class)
				.verify();
	}

	@Test
	public void systemTimeZone_isRetrieved() {
		var sut = new TimePolicyInformationPoint(mock(Clock.class));
		var zoneId = ZoneId.of("UTC");
		try (MockedStatic<ZoneId> mock = mockStatic(ZoneId.class, Mockito.CALLS_REAL_METHODS)) {
			mock.when(() -> ZoneId.systemDefault()).thenReturn(zoneId);
			StepVerifier.create(sut.systemTimeZone()).expectNext(Val.of("UTC")).verifyComplete();
		}
	}

	@Test
	public void nowIsAfterTest() {
		var startingTime = Instant.parse("2021-11-08T13:00:00Z");
		var ckeckpoint = Val.fluxOf("2021-11-08T14:30:00Z");
		var clock = mock(Clock.class);
		when(clock.instant()).thenReturn(startingTime);
		var sut = new TimePolicyInformationPoint(clock);
		StepVerifier.withVirtualTime(() -> sut.nowIsAfter(ckeckpoint)).expectNext(Val.FALSE)
				.thenAwait(Duration.ofMinutes(91L)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void nowIsBetweenStartAfterIntervalTest() {
		var startingTime = Instant.parse("2021-11-08T13:00:40Z");
		var intervalStart = Val.fluxOf("2021-11-08T13:00:05Z");
		var intervalEnd = Val.fluxOf("2021-11-08T13:00:10Z");

		var clock = mock(Clock.class);
		when(clock.instant()).thenReturn(startingTime);
		var sut = new TimePolicyInformationPoint(clock);

		StepVerifier.withVirtualTime(() -> sut.nowIsBetween(intervalStart, intervalEnd)).expectNext(Val.FALSE)
				.verifyComplete();
	}

	@Test
	public void nowIsBetweenStartBetweenTest() {
		var startingTime = Instant.parse("2021-11-08T13:00:05Z");
		var intervalStart = Val.fluxOf("2021-11-08T13:00:00Z");
		var intervalEnd = Val.fluxOf("2021-11-08T13:00:10Z");

		var clock = mock(Clock.class);
		when(clock.instant()).thenReturn(startingTime);
		var sut = new TimePolicyInformationPoint(clock);
		StepVerifier.withVirtualTime(() -> sut.nowIsBetween(intervalStart, intervalEnd)).expectNext(Val.TRUE)
				.thenAwait(Duration.ofSeconds(5L)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void nowIsBetweenStartBeforeTest() {
		var startingTime = Instant.parse("2021-11-08T13:00:00Z");
		var intervalStart = Val.fluxOf("2021-11-08T13:00:05Z");
		var intervalEnd = Val.fluxOf("2021-11-08T13:00:10Z");

		var clock = mock(Clock.class);
		when(clock.instant()).thenReturn(startingTime);
		var sut = new TimePolicyInformationPoint(clock);
		StepVerifier.withVirtualTime(() -> sut.nowIsBetween(intervalStart, intervalEnd)).expectNext(Val.FALSE)
				.thenAwait(Duration.ofSeconds(5L)).expectNext(Val.TRUE).thenAwait(Duration.ofSeconds(5L))
				.expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void nowIsAfterTestAlwaysAfter() {
		var now = Instant.parse("2021-11-08T13:00:00Z");
		var ckeckpoint = Val.fluxOf("2021-11-01T14:30:00Z");
		var clock = mock(Clock.class);
		when(clock.instant()).thenReturn(now);
		var sut = new TimePolicyInformationPoint(clock);
		StepVerifier.withVirtualTime(() -> sut.nowIsAfter(ckeckpoint)).expectNext(Val.TRUE).verifyComplete();
	}

	@Test
	public void nowIsBeforeTest() {
		var now = Instant.parse("2021-11-08T13:00:00Z");
		var ckeckpoint = Val.fluxOf("2021-11-08T14:30:00Z");
		var clock = mock(Clock.class);
		when(clock.instant()).thenReturn(now);
		var sut = new TimePolicyInformationPoint(clock);
		StepVerifier.withVirtualTime(() -> sut.nowIsBefore(ckeckpoint).log()).expectNext(Val.TRUE)
				.thenAwait(Duration.ofMinutes(91L)).expectNext(Val.FALSE).verifyComplete();
	}

	@Test
	public void toggleTest() {
		var sut = new TimePolicyInformationPoint(mock(Clock.class));
		StepVerifier.withVirtualTime(() -> sut.toggle(Val.fluxOf(5_000L), Val.fluxOf(1_000L))).expectNext(Val.TRUE)
				.thenAwait(Duration.ofMillis(5_0000L)).expectNext(Val.FALSE).thenAwait(Duration.ofMillis(1_000L))
				.expectNext(Val.TRUE).thenCancel().verify();
	}

}
