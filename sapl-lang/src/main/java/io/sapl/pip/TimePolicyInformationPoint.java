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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pip.Attribute;
import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.api.validation.Number;
import io.sapl.api.validation.Text;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@RequiredArgsConstructor
@PolicyInformationPoint(name = TimePolicyInformationPoint.NAME, description = TimePolicyInformationPoint.DESCRIPTION)
public class TimePolicyInformationPoint {

	public static final String NAME = "time";

	public static final String DESCRIPTION = "Policy Information Point and attributes for retrieving current date and time information";

	private static final Flux<Val> DEFAULT_UPDATE_INTERVAL_IN_MS = Flux.just(Val.of(1000L));

	private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_DATE_TIME
			.withZone(ZoneId.from(ZoneOffset.UTC));

	private final Clock clock;

	@Attribute(docs = "Emits the current number of seconds passed since the epoc (1. Januar 1970, 00:00 Uhr UTC). The first value is emitted instantly the follwoing timestamps are sent once every second.")
	public Flux<Val> secondsSinceEpoc() {
		return secondsSinceEpoc(DEFAULT_UPDATE_INTERVAL_IN_MS);
	}

	@Attribute(docs = "Emits the current number of seconds passed since the epoc (1. Januar 1970, 00:00 Uhr UTC). The first value is emitted instantly the follwoing timestamps are sent once every time the number of provided milliseconds passed.")
	public Flux<Val> secondsSinceEpoc(@Number Flux<Val> updateIntervalInMillis) {
		return intstantNow(updateIntervalInMillis).map(Instant::getEpochSecond).map(Val::of);
	}

	@Attribute(docs = "Emits the current date and time as an ISO8601 String in UTC. The first time is emitted instantly. After that the time is updated once every second.")
	public Flux<Val> now() {
		return now(DEFAULT_UPDATE_INTERVAL_IN_MS);
	}

	@Attribute(docs = "Emits the current date and time as an ISO8601 String in UTC. The first time is emitted instantly. After that the time is updated once every second.")
	public Flux<Val> now(@Number Flux<Val> updateIntervalInMillis) {
		return intstantNow(updateIntervalInMillis).map(ISO_FORMATTER::format).map(Val::of);
	}

	private Duration valMsToNonZeroDuration(Val val) {
		var duration = Duration.ofMillis(val.get().asLong());
		if (duration.isZero())
			throw new PolicyEvaluationException("Time update interval must not be zero.");
		return duration;
	}

	private Flux<Instant> intstantNow(Flux<Val> pollIntervalInMillis) {
		return pollIntervalInMillis.map(this::valMsToNonZeroDuration).switchMap(this::instantNow);
	}

	private Flux<Instant> instantNow(Duration pollIntervalInMillis) {
		var first = Flux.just(clock.instant());
		var following = Flux.just(0).repeat().delayElements(pollIntervalInMillis).map(__ -> clock.instant());
		return Flux.concat(first, following);
	}

	@Attribute(docs = "Returns the system default time-zone.")
	public Flux<Val> systemTimeZone() {
		return Val.fluxOf(ZoneId.systemDefault().toString());
	}

	@Attribute(docs = "")
	public Flux<Val> nowIsAfter(@Text Flux<Val> time) {
		return time.map(this::valToInstant).switchMap(this::nowIsAfter).map(Val::of);
	}

	@Attribute(docs = "")
	public Flux<Val> nowIsBefore(@Text Flux<Val> time) {
		return time.map(this::valToInstant).switchMap(this::nowIsBefore).map(Val::of);
	}

	private Instant valToInstant(Val val) {
		return Instant.parse(val.getText());
	}

	private Flux<Boolean> nowIsAfter(Instant anInstant) {
		return isAfter(anInstant, clock.instant());
	}

	private Flux<Boolean> nowIsBefore(Instant anInstant) {
		return isAfter(clock.instant(), anInstant);
	}

	private Flux<Boolean> isAfter(Instant intstantA, Instant instantB) {
		if (instantB.isAfter(intstantA))
			return Flux.just(true);
		var initial = Flux.just(false);
		var eventual = Flux.just(true).delayElements(Duration.between(instantB, intstantA));
		return Flux.concat(initial, eventual);
	}

	@Attribute(docs = "Returns true while the current time is between the two given times (ISO Strings). Will emit updates if the time changes and enters or exits the provided time interval.")
	public Flux<Val> nowIsBetween(@Text Flux<Val> startTime, @Text Flux<Val> endTime) {
		var startInstants = startTime.map(this::valToInstant);
		var endInstants = endTime.map(this::valToInstant);
		return Flux
				.combineLatest(times -> Tuples.of((Instant) times[1], (Instant) times[2]), startInstants, endInstants)
				.switchMap(this::nowIsBetween).map(Val::of);
	}

	public Flux<Boolean> nowIsBetween(Tuple2<Instant, Instant> startAndEnd) {
		return nowIsBetween(startAndEnd.getT1(), startAndEnd.getT2());
	}

	public Flux<Boolean> nowIsBetween(Instant start, Instant end) {
		var now = clock.instant();
		if (now.isAfter(end))
			return Flux.just(false);
		if (start.isAfter(now))
			return isAfter(now, start);

		var initial = Flux.just(false);
		var duringIsBetween = Flux.just(true).delayElements(Duration.between(now, start));
		var eventual = Flux.just(false).delayElements(Duration.between(start, end));

		return Flux.concat(initial, duringIsBetween, eventual);
	}

	@Attribute(docs = "A preiodically toggling signal. Will be true for the first duration (ms) and then false for the second duration (ms). This will repeat periodically. Note, that the cycle will completely reset if the durations are updated. The attribute will forget its stat ein this case.")
	public Flux<Val> toggle(Flux<Val> trueDurationMs, Flux<Val> falseDurationMs) {
		return Flux.combineLatest(durations -> Tuples.of((Duration) durations[1], (Duration) durations[2]),
				trueDurationMs.map(this::valMsToNonZeroDuration), falseDurationMs.map(this::valMsToNonZeroDuration))
				.switchMap(this::toggle).map(Val::of);
	}

	private Flux<Boolean> toggle(Tuple2<Duration, Duration> durations) {
		var initial = Flux.just(true);
		var waitTillFalse = Flux.just(false).delayElements(durations.getT1());
		var waitTillTrue = Flux.just(false).delayElements(durations.getT2());
		var repeatingTail = Flux.concat(waitTillFalse, waitTillTrue).repeat();
		return Flux.concat(initial, repeatingTail);
	}

}
