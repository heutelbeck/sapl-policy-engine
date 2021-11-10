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

import static java.time.temporal.ChronoUnit.MILLIS;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
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

	@Attribute(docs = "Emits the current date and time as an ISO8601 String in UTC. The first time is emitted instantly. After that the time is updated once every second.")
	public Flux<Val> now() {
		return now(DEFAULT_UPDATE_INTERVAL_IN_MS);
	}

	@Attribute(docs = "Emits the current date and time as an ISO8601 String in UTC. The first time is emitted instantly. After that the time is updated once every second.")
	public Flux<Val> now(@Number Flux<Val> updateIntervalInMillis) {
		return intstantNow(updateIntervalInMillis).map(ISO_FORMATTER::format).map(Val::of);
	}

	private Duration valMsToNonZeroDuration(Val val) {
		var duration = Duration.ofMillis(val.getLong());
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

	@Attribute(docs = "Returns true, if the current time in ISO UTC is after the provided time parameter, also in ISO UTC.")
	public Flux<Val> nowIsAfter(@Text Flux<Val> time) {
		return time.map(this::valToInstant).switchMap(this::nowIsAfter).map(Val::of);
	}

	@Attribute(docs = "Returns true, if the current local time in UTC (e.g., \"17:00\") is before the providec checkpoint time.")
	public Flux<Val> localTimeIsAfter(@Text Flux<Val> checkpoint) {
		return checkpoint.map(Val::getText).map(LocalTime::parse).switchMap(this::localTimeIsAfter).map(Val::of);
	}

	private Flux<Boolean> localTimeIsAfter(LocalTime checkpoint) {
		return localTimeIsAfter(localTimeUtc(), checkpoint);
	}

	private LocalTime localTimeUtc() {
		return LocalTime.from(clock.instant().atZone(ZoneId.of("UTC")));
	}

	private Flux<Boolean> localTimeIsAfter(LocalTime now, LocalTime checkpoint) {

		if (checkpoint.equals(LocalTime.MIN))
			return Flux.just(Boolean.TRUE);

		if (checkpoint.equals(LocalTime.MAX))
			return Flux.just(Boolean.FALSE);

		if (now.isAfter(checkpoint)) {
			var initial = Flux.just(Boolean.TRUE);
			var tillMidnight = boolAfterTimeDifference(false, now, LocalTime.MAX);
			var initialDay = Flux.concat(initial, tillMidnight);
			return Flux.concat(initialDay, afterCheckpointEventsFollowingDays(checkpoint));
		}

		var initial = Flux.just(Boolean.FALSE);
		var tillCheckpoint = boolAfterTimeDifference(true, now, checkpoint);
		var tillMidnight = boolAfterTimeDifference(false, checkpoint, LocalTime.MAX);
		var initialDay = Flux.concat(initial, tillCheckpoint, tillMidnight);
		return Flux.concat(initialDay, afterCheckpointEventsFollowingDays(checkpoint));

	}

	private Flux<Boolean> afterCheckpointEventsFollowingDays(LocalTime checkpoint) {
		var startOfDay = boolAfterTimeDifference(true, LocalTime.MIN, checkpoint);
		var endOfDay = boolAfterTimeDifference(false, checkpoint, LocalTime.MAX);
		return Flux.concat(startOfDay, endOfDay).repeat();
	}

	@Attribute(docs = "Returns true, while the local UTC time (e.g., \"13:34:21\") is between the two provided times of the day. If the time of the first parameter is after the time of the second parameter, the intervall ist considered to be the one between the to times, crossing the midnight border of the days.")
	public Flux<Val> localTimeIsBetween(@Text Flux<Val> startTime, @Text Flux<Val> endTime) {
		var startTimes = startTime.map(Val::getText).map(LocalTime::parse);
		var endTimes = endTime.map(Val::getText).map(LocalTime::parse);
		return Flux.combineLatest(times -> Tuples.of((LocalTime) times[0], (LocalTime) times[1]), startTimes, endTimes)
				.switchMap(this::localTimeIsBetween).map(Val::of);
	}

	private Flux<Boolean> localTimeIsBetween(Tuple2<LocalTime, LocalTime> startAndEnd) {
		return nowIsBetween(startAndEnd.getT1(), startAndEnd.getT2());
	}

	private Flux<Boolean> nowIsBetween(LocalTime t1, LocalTime t2) {

		if (t1.equals(t2))
			return Flux.just(Boolean.FALSE);

		if (t1.equals(LocalTime.MIN) && t2.equals(LocalTime.MAX))
			return Flux.just(Boolean.TRUE);

		if (t1.equals(LocalTime.MAX) && t2.equals(LocalTime.MIN))
			return Flux.just(Boolean.TRUE);

		var intervalWrapsAroundMidnight = t1.isAfter(t2);

		if (intervalWrapsAroundMidnight)
			return nowIsBetweenAscendingTimes(t2, t1).map(this::negate);

		return nowIsBetweenAscendingTimes(t1, t2);
	}

	private boolean negate(boolean val) {
		return !val;
	}

	private Flux<Boolean> nowIsBetweenAscendingTimes(LocalTime start, LocalTime end) {
		var now = localTimeUtc();

		Flux<Boolean> initialStates;
		if (now.isBefore(start)) {
			var initial = Flux.just(Boolean.FALSE);
			var tillStart = boolAfterTimeDifference(true, now, start);
			initialStates = Flux.concat(initial, tillStart);
		} else if (now.isAfter(end)) {
			var initial = Flux.just(Boolean.FALSE);
			var timeTillIntervalStarts = Duration
					.ofMillis(MILLIS.between(now, LocalTime.MAX) + MILLIS.between(LocalTime.MIN, start));
			var tillStart = Flux.just(Boolean.TRUE).delayElements(timeTillIntervalStarts);
			initialStates = Flux.concat(initial, tillStart);
		} else {
			// starts inside of interval
			var initial = Flux.just(Boolean.TRUE);
			var tillIntervalEnd = boolAfterTimeDifference(false, now, end);
			var timeTillIntervalStarts = Duration
					.ofMillis(MILLIS.between(end, LocalTime.MAX) + MILLIS.between(LocalTime.MIN, start));
			var tillStart = Flux.just(Boolean.TRUE).delayElements(timeTillIntervalStarts);
			initialStates = Flux.concat(initial, tillIntervalEnd, tillStart);
		}

		var tillEnd = boolAfterTimeDifference(false, start, end);
		var tillStartNextDay = boolAfterTimeDifference(true, start, end);
		var repeated = Flux.concat(tillEnd, tillStartNextDay).repeat();

		return Flux.concat(initialStates, repeated);
	}

	private Flux<Boolean> boolAfterTimeDifference(boolean val, LocalTime start, LocalTime end) {
		return Flux.just(val).delayElements(Duration.ofMillis(MILLIS.between(start, end)));
	}

	@Attribute(docs = "Returns true while the current local time in UTC is before the providec checkpoint time.")
	public Flux<Val> localTimeIsBefore(@Text Flux<Val> checkpoint) {
		return checkpoint.map(Val::getText).map(LocalTime::parse).switchMap(this::localTimeIsAfter).map(this::negate)
				.map(Val::of);
	}

	@Attribute(docs = "Returns true, while the current UTC time is before the provided checkpoint time.")
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
		return isAfter(anInstant, clock.instant()).map(this::negate);
	}

	private Flux<Boolean> isAfter(Instant intstantA, Instant instantB) {
		if (instantB.isAfter(intstantA))
			return Flux.just(Boolean.TRUE);
		var initial = Flux.just(Boolean.FALSE);
		var eventual = Flux.just(Boolean.TRUE).delayElements(Duration.between(instantB, intstantA));
		return Flux.concat(initial, eventual);
	}

	@Attribute(docs = "Returns true while the current time is between the two given times (ISO Strings). Will emit updates if the time changes and enters or exits the provided time interval.")
	public Flux<Val> nowIsBetween(@Text Flux<Val> startTime, @Text Flux<Val> endTime) {
		var startInstants = startTime.map(this::valToInstant);
		var endInstants = endTime.map(this::valToInstant);
		return Flux
				.combineLatest(times -> Tuples.of((Instant) times[0], (Instant) times[1]), startInstants, endInstants)
				.switchMap(this::nowIsBetween).map(Val::of);
	}

	public Flux<Boolean> nowIsBetween(Tuple2<Instant, Instant> startAndEnd) {
		return nowIsBetween(startAndEnd.getT1(), startAndEnd.getT2());
	}

	public Flux<Boolean> nowIsBetween(Instant start, Instant end) {
		var now = clock.instant();
		if (now.isAfter(end))
			return Flux.just(Boolean.FALSE);

		if (now.isAfter(start)) {
			var initial = Flux.just(Boolean.TRUE);
			var eventual = Flux.just(Boolean.FALSE).delayElements(Duration.between(now, end));
			return Flux.concat(initial, eventual);
		}

		var initial = Flux.just(Boolean.FALSE);
		var duringIsBetween = Flux.just(Boolean.TRUE).delayElements(Duration.between(now, start));
		var eventual = Flux.just(Boolean.FALSE).delayElements(Duration.between(start, end));

		return Flux.concat(initial, duringIsBetween, eventual);
	}

	@Attribute(docs = "A preiodically toggling signal. Will be true for the first duration (ms) and then false for the second duration (ms). This will repeat periodically. Note, that the cycle will completely reset if the durations are updated. The attribute will forget its stat ein this case.")
	public Flux<Val> toggle(Flux<Val> trueDurationMs, Flux<Val> falseDurationMs) {
		return Flux.combineLatest(durations -> Tuples.of((Duration) durations[0], (Duration) durations[1]),
				trueDurationMs.map(this::valMsToNonZeroDuration), falseDurationMs.map(this::valMsToNonZeroDuration))
				.switchMap(this::toggle).map(Val::of);
	}

	private Flux<Boolean> toggle(Tuple2<Duration, Duration> durations) {
		var initial = Flux.just(Boolean.TRUE);
		var waitTillFalse = Flux.just(Boolean.FALSE).delayElements(durations.getT1());
		var waitTillTrue = Flux.just(Boolean.TRUE).delayElements(durations.getT2());
		var repeatingTail = Flux.concat(waitTillFalse, waitTillTrue).repeat();
		return Flux.concat(initial, repeatingTail);
	}

}
