package io.sapl.pip;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

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
	public void secondsSinceEpoc_EmitsUpdates() {
		var now = Instant.parse("2021-11-08T13:00:00Z");
		var nowPlusOne = Instant.parse("2021-11-08T13:00:01Z");
		var nowPlusTwo = Instant.parse("2021-11-08T13:00:02Z");
		var clock = mock(Clock.class);
		when(clock.instant()).thenReturn(now, nowPlusOne, nowPlusTwo);
		var sut = new TimePolicyInformationPoint(clock);
		StepVerifier.withVirtualTime(() -> sut.secondsSinceEpoc()).expectNext(Val.of(now.getEpochSecond()))
				.thenAwait(Duration.ofSeconds(2))
				.expectNext(Val.of(nowPlusOne.getEpochSecond()), Val.of(nowPlusTwo.getEpochSecond())).thenCancel()
				.verify();
	}

	@Test
	public void secondsSinceEpoc_zeroDelay_Fails() {
		var clock = mock(Clock.class);
		var sut = new TimePolicyInformationPoint(clock);
		StepVerifier.withVirtualTime(() -> sut.secondsSinceEpoc(Flux.just(Val.of(0L))))
				.expectError(PolicyEvaluationException.class).verify();
	}

	@Test
	public void now_zeroDelay_Fails() {
		var clock = mock(Clock.class);
		var sut = new TimePolicyInformationPoint(clock);
		StepVerifier.withVirtualTime(() -> sut.now(Flux.just(Val.of(0L)))).expectError(PolicyEvaluationException.class)
				.verify();
	}
}
