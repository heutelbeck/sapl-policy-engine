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
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pip.Attribute;
import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.api.validation.Long;
import io.sapl.api.validation.Text;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

@Slf4j
@NoArgsConstructor
@PolicyInformationPoint(name = ClockPolicyInformationPoint.NAME, description = ClockPolicyInformationPoint.DESCRIPTION)
public class ClockPolicyInformationPoint {

    public static final String NAME = "clock";

    public static final String DESCRIPTION = "Policy Information Point and attributes for retrieving current date and time information";
    private static final String PARAMETER_NOT_AN_ISO_8601_STRING = "Parameter not an ISO 8601 string";

    @Attribute(docs = "Returns the current date and time in the given time zone (e.g. 'UTC', 'ECT', 'Europe/Berlin', 'system') as an ISO-8601 string with time offset.")
    public Flux<Val> now(@Text Val value, Map<String, JsonNode> variables) {
        final ZoneId zoneId = convertToZoneId(value.get());
        final OffsetDateTime now = Instant.now().atZone(zoneId).toOffsetDateTime();
        return Val.fluxOf(now.toString());
    }

    @Attribute(docs = "Gets the current millisecond instant of the clock. This returns the millisecond-based instant, measured from 1970-01-01T00:00Z (UTC)")
    public Flux<Val> millis(Val leftHand, Map<String, JsonNode> variables) {
        return Flux.just(Val.of(Instant.now().toEpochMilli()));
    }

    public Flux<Val> dayOfTheWeek(@Text Val value, Map<String, JsonNode> variables) {
        final ZoneId zoneId = convertToZoneId(value.get());
        final OffsetDateTime now = Instant.now().atZone(zoneId).toOffsetDateTime();
        return Val.fluxOf(now.getDayOfWeek().name());
    }

    private ZoneId convertToZoneId(JsonNode value) {
        final String text = value.asText() == null ? "" : value.asText().trim();
        final String zoneIdStr = text.length() == 0 ? "system" : text;
        if ("system".equals(zoneIdStr)) {
            return ZoneId.systemDefault();
        } else if (ZoneId.SHORT_IDS.containsKey(zoneIdStr)) {
            return ZoneId.of(zoneIdStr, ZoneId.SHORT_IDS);
        }
        return ZoneId.of(zoneIdStr);
    }

    @Attribute(docs = "Emits every x seconds the current UTC date and time as an ISO-8601 string. x is the passed number value.")
    public Flux<Val> ticker(Val leftHand, Map<String, JsonNode> variables, Flux<Val> intervallInSeconds) {
        return intervallInSeconds.switchMap(seconds -> {
            if (!seconds.isNumber())
                return Flux.error(new PolicyEvaluationException(
                        String.format("ticker parameter not a number. Was: %s", seconds.toString())));

            var secondsValue = seconds.get().asLong();

            if (secondsValue == 0)
                return Flux.error(new PolicyEvaluationException("ticker parameter must not be zero"));

            // concatenate with a single number leading to have one time-stamp immediately.
            // Else PEPs have to wait for the interval to pass once before getting a first
            // decision.
            return Flux.concat(Flux.just(0), Flux.interval(Duration.ofSeconds(secondsValue)));
        }).map(__ -> Val.of(Instant.now().toString()));
    }

    /*
     * var time = "09:37"
     * var isAfter = <clock.clockAfter(time)>; should produce < "10:00" true, "00:00" false, "09:37" true, "00:00" false>
     */
    public Flux<Val> clockAfter(Val leftHand, Map<String, JsonNode> variables, @Text Val time) {
        var reference = nodeToInstant(time);
        var now = Instant.now();
        var nowIsBeforeReference = now.isBefore(reference);
        // log.info("{} is before {}: {}", now, reference, nowIsBeforeReference);

        var nextMidnight = Instant.now().plus(1L, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);
        var nextReferenceTime = reference.plus(1L, ChronoUnit.DAYS);
        // log.info("nextMidnight: {}, nextReferenceTime: {}", nextMidnight, nextReferenceTime);

        var delayToNextMidnight = Duration.between(now, nextMidnight);
        var delayToNextReferenceTime = nowIsBeforeReference ? Duration.between(now, reference)
                : Duration.between(now, nextReferenceTime);
        // log.info("delayToNextMidnight: {}, delayToNextReferenceTime: {}", delayToNextMidnight, delayToNextReferenceTime);

        //emits value every 24 hours at midnight
        var midnightFlux = Flux.interval(delayToNextMidnight, Duration.ofHours(24));
        //emits value every 24 hours at reference time
        var referenceTimeFlux = Flux.interval(delayToNextReferenceTime, Duration.ofHours(24));

        //if reference is after "now", next change is is at specified time
        //if reference is before "now", next change is at midnight of the next day

        // concatenate with a single number leading to have one time-stamp immediately.
        // Else PEPs have to wait for the interval to pass once before getting a first
        // decision.
        return Flux.concat(Flux.just(0), midnightFlux.mergeWith(referenceTimeFlux))
                .map(__ -> Val.of(Instant.now().isAfter(reference)));
    }

    public Flux<Val> clockAfter2(Val leftHand, Map<String, JsonNode> variables, @Text Val time) {
        var instantRef = nodeToInstant(time);
        var localTimeRef = instantRef.atZone(ZoneOffset.UTC).toLocalTime();

        var delayToNextMidnight = delayUntilNextMidnight();
        var delayToNextReferenceTime = delayUntilNextReference(localTimeRef);
        log.info("delayToNextMidnight: {}, delayToNextReferenceTime: {}", delayToNextMidnight, delayToNextReferenceTime);

        var firstTuple = delayToNextMidnight.compareTo(delayToNextReferenceTime) > 0
                ? Tuples.of("reference", delayToNextReferenceTime)
                : Tuples.of("reference", delayToNextMidnight);

        ConcurrentLinkedQueue<Tuple2<String, Duration>> queue = new ConcurrentLinkedQueue<>();
        queue.offer(firstTuple);

        var delayFlux = Flux.<Tuple2<String, Duration>>generate(sink -> {
            val tuple2 = queue.poll();
            if (tuple2 == null) {
                sink.complete();
            } else {
                if (tuple2.getT1().equalsIgnoreCase("reference")) {
                    queue.offer(Tuples.of("midnight", delayUntilNextMidnight()));
                } else {
                    queue.offer(Tuples.of("reference", delayUntilNextReference(localTimeRef)));
                }

                sink.next(tuple2);
            }
        }).repeatWhen(it -> it.delayElements(Duration.ofSeconds(10))).delayUntil(tuple -> Mono.delay(tuple.getT2()));

        return Flux.concat(Flux.just(Tuples.of("now", Duration.ZERO)), delayFlux)
                .map(tuple2 -> {
                    var localTimeNow = Instant.now().atZone(ZoneOffset.UTC).toLocalTime();
                    log.info("{} (clock) is after {} (ref): {}", localTimeNow, localTimeRef, localTimeNow.isAfter(localTimeRef));

                    return Val.of(localTimeNow.isAfter(localTimeRef));
                });
    }

    private Duration delayUntilNextMidnight() {
        var now = Instant.now();
        var nextMidnight = Instant.now().plus(1L, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);

        return Duration.between(now, nextMidnight);
    }

    private Duration delayUntilNextReference(LocalTime localTimeRef) {
        ZonedDateTime zonedNow = Instant.now().atZone(ZoneOffset.UTC);
        var localTimeNow = zonedNow.toLocalTime();
        var nowBefore = localTimeNow.isBefore(localTimeRef);

        var nextReferenceTime = zonedNow.toLocalDate() //Today
                .plusDays(1L) // Tomorrow
                .atTime(localTimeRef) // Tomorrow at reference Time
                .atZone(ZoneOffset.UTC);

        return nowBefore ? Duration.between(localTimeNow, localTimeRef) // same day
                : Duration.between(zonedNow, nextReferenceTime); // next day
    }

    public Flux<Val> clockBefore(Val leftHand, Map<String, JsonNode> variables, @Text Val time) {
        return clockAfter(leftHand, variables, time)
                .map(this::negateVal);
    }

    private Val negateVal(Val val) {
        return Val.of(!val.getBoolean());
    }

    /*
     *  var timeout = <timeout("10s")>; <sofort "false" , nach 10s "true">
     */
    public Flux<Val> timeout(Val leftHand, Map<String, JsonNode> variables, @Long Val timeout) {
        return Flux.concat(
                Mono.just(Val.FALSE),
                Mono.just(Val.TRUE).delayElement(Duration.ofMillis(timeout.get().asLong()))
        );
    }

    private static Instant nodeToInstant(Val time) {
        if (time.isNull() || time.isUndefined()) throw new DateTimeException("provided time value is null or undefined");

        return Instant.parse(time.get().asText());
    }

}