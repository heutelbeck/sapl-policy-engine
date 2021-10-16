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
import io.sapl.pip.Periods.PeriodListener;
import io.sapl.pip.Periods.PeriodProducer;
import io.sapl.pip.Schedules.ScheduleListener;
import io.sapl.pip.Schedules.ScheduleProducer;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink.OverflowStrategy;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple5;
import reactor.util.function.Tuples;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

@NoArgsConstructor
@PolicyInformationPoint(name = ClockPolicyInformationPoint.NAME, description = ClockPolicyInformationPoint.DESCRIPTION)
public class ClockPolicyInformationPoint {

    public static final String NAME = "clock";

    public static final String DESCRIPTION = "Policy Information Point and attributes for retrieving current date and time information";

    private static final Flux<Val> SYSTEM_DEFAULT_TIMEZONE_FLUX = Flux.just(Val.of(TimeZone.getDefault().toString()));


    @Attribute(docs = "Returns the current date and time in the systems default time zone as an  millisecond-based instant, measured from 1970-01-01T00:00Z (UTC).")
    public Flux<Val> millis(Val leftHand, Map<String, JsonNode> variables) {
        return millis(leftHand, variables, SYSTEM_DEFAULT_TIMEZONE_FLUX);
    }

    @Attribute(docs = "Returns the current date and time in the given time zone (e.g. 'UTC', 'ECT', 'Europe/Berlin', 'system') as an  millisecond-based instant, measured from 1970-01-01T00:00Z (UTC).")
    public Flux<Val> millis(Val leftHand, Map<String, JsonNode> variables, Flux<Val> zone) {
        enforceUndefinedLeftHand(leftHand);
        return zone.switchMap(timeZone -> {
            final ZoneId zoneId = convertToZoneId(timeZone);
            return Flux.just(Val.of(Instant.now().atZone(zoneId).toInstant().toEpochMilli()));
        });
    }

    @Attribute(docs = "Returns the system default time-zone.")
    public Flux<Val> timeZone(Val leftHand, Map<String, JsonNode> variables) {
        enforceUndefinedLeftHand(leftHand);
        return Val.fluxOf(ZoneId.systemDefault().toString());
    }


    @Attribute(docs = "Emits the current date and time every x milliseconds in the systems defaults time zone. x is the passed number value.")
    public Flux<Val> ticker(Val leftHand, Map<String, JsonNode> variables, Flux<Val> intervallInMillis) {
        return ticker(leftHand, variables, intervallInMillis, SYSTEM_DEFAULT_TIMEZONE_FLUX);
    }

    @Attribute(docs = "Emits the current date and time every x milliseconds in the given time zone (e.g. 'UTC', 'ECT', 'Europe/Berlin', 'system') as an ISO-8601 string with offset. x is the passed number value.")
    public Flux<Val> ticker(Val leftHand, Map<String, JsonNode> variables, Flux<Val> intervallInMillis, Flux<Val> zone) {
        enforceUndefinedLeftHand(leftHand);
        return Flux.combineLatest(intervallInMillis, zone, Tuples::of)
                .switchMap(intervallAndZoneTuple -> {
                    var millis = intervallAndZoneTuple.getT1();
                    var zoneId = convertToZoneId(intervallAndZoneTuple.getT2());

                    if (!millis.isNumber())
                        return Flux.error(new PolicyEvaluationException(
                                String.format("ticker parameter not a number. Was: %s", millis.toString())));

                    var millisValue = millis.get().asLong();

                    if (millisValue == 0)
                        return Flux.error(new PolicyEvaluationException("ticker parameter must not be zero"));

                    return Flux.<String>create(sink ->
                            Schedulers.single().schedulePeriodically(
                                    () -> sink.next(Instant.now().atZone(zoneId).toOffsetDateTime().toString()),
                                    0L, millisValue, TimeUnit.MILLISECONDS));
                }).map(Val::of);
    }

    @Attribute(docs = "Returns true if the local clock is after the provided time in the systems default time-zone. Only the time of the day is taken into account. Emits value every time, the result changes.")
    public Flux<Val> nowIsAfter(Val leftHand, Map<String, JsonNode> variables, Flux<Val> time) {
        return nowIsAfter(leftHand, variables, time, SYSTEM_DEFAULT_TIMEZONE_FLUX);
    }

    @Attribute(docs = "Returns true if the local clock is after the provided time in the specified time-zone. Only the time of the day is taken into account. Emits value every time, the result changes.")
    public Flux<Val> nowIsAfter(Val leftHand, Map<String, JsonNode> variables, Flux<Val> time, Flux<Val> zone) {
        enforceUndefinedLeftHand(leftHand);
        return Flux.combineLatest(time, zone, Tuples::of)
                .switchMap(timeAndZoneTuple -> {
                    var referenceTime = timeAndZoneTuple.getT1();
                    var timeZone = timeAndZoneTuple.getT2();

                    var referenceLocalTime = LocalTime.parse(referenceTime.getText());
                    var localOffset = toOffset(ZoneId.systemDefault(), referenceLocalTime);
                    var offset = toOffset(convertToZoneId(timeZone), referenceLocalTime);
                    var adjustedReference = referenceLocalTime.atOffset(localOffset).withOffsetSameInstant(offset)
                            .toLocalTime();

                    return Flux.create(sink -> ScheduleProducer.builder()
                            .referenceTime(adjustedReference)
                            .currentTime(LocalTime.now())
                            .listener(new ScheduleListener(sink))
                            .build().startScheduling(), OverflowStrategy.LATEST);
                });
    }

    @Attribute(docs = "Returns true if the local clock is before the provided time in the systems default time-zone. Only the time of the day is taken into account. Emits value every time, the result changes.")
    public Flux<Val> nowIsBefore(Val leftHand, Map<String, JsonNode> variables, Flux<Val> time) {
        return nowIsBefore(leftHand, variables, time, SYSTEM_DEFAULT_TIMEZONE_FLUX);
    }

    @Attribute(docs = "Returns true if the local clock is before the provided time in the specified time-zone. Only the time of the day is taken into account. Emits value every time, the result changes.")
    public Flux<Val> nowIsBefore(Val leftHand, Map<String, JsonNode> variables, Flux<Val> time, Flux<Val> zone) {
        return nowIsAfter(leftHand, variables, time, zone)
                .map(this::negateVal);
    }

    @Attribute(docs = "Returns true if the local clock is before the provided start time and before the end time in the systems default time-zone. Only the time of the day is taken into account. Emits value every time, the result changes.")
    public Flux<Val> nowIsBetween(Val leftHand, Map<String, JsonNode> variables, Flux<Val> start, Flux<Val> end) {
        return nowIsBetween(leftHand, variables, start, end, SYSTEM_DEFAULT_TIMEZONE_FLUX);
    }

    @Attribute(docs = "Returns true if the local clock is before the provided start time and before the end time in the provided time-zone. Only the time of the day is taken into account. Emits value every time, the result changes.")
    public Flux<Val> nowIsBetween(Val leftHand, Map<String, JsonNode> variables, Flux<Val> start, Flux<Val> end, Flux<Val> zone) {
        Flux<Val> nowIsAfterFlux = nowIsAfter(leftHand, variables, start, zone);
        Flux<Val> nowIsBeforeFlux = nowIsBefore(leftHand, variables, end, zone);

        return Flux.combineLatest(nowIsAfterFlux, nowIsBeforeFlux, this::combineBetween);
    }

    private Val combineBetween(Val nowIsAfterVal, Val nowIsBeforeVal) {
        if ((!nowIsAfterVal.isBoolean()) || (!nowIsBeforeVal.isBoolean())) throw new RuntimeException("values must be of type boolean");

        var nowIsBetween = nowIsAfterVal.getBoolean() && nowIsBeforeVal.getBoolean();

        return Val.of(nowIsBetween);
    }


    @Attribute(docs = "Sets a timer for the provided number of milliseconds. Emits Val.FALSE when created and Val.TRUE after timer elapsed.")
    public Flux<Val> trueIn(Val leftHand, Map<String, JsonNode> variables, Flux<Val> timerMillis) {
        enforceUndefinedLeftHand(leftHand);
        return timerMillis.switchMap(millis -> {
            if (!millis.isNumber())
                return Flux.error(new PolicyEvaluationException(
                        String.format("timer parameter not a number. Was: %s", millis)));

            var millisValue = millis.get().asLong();
            if (millisValue == 0)
                return Flux.error(new PolicyEvaluationException("ticker parameter must not be zero"));

            return Flux.concat(
                    Mono.just(Val.FALSE),
                    Mono.just(Val.TRUE).delayElement(Duration.ofMillis(millisValue))
            );
        });
    }

    @Attribute(docs = "Sets a timer for the provided number of milliseconds. Emits Val.TRUE when created and Val.FALSE after timer elapsed.")
    public Flux<Val> trueFor(Val leftHand, Map<String, JsonNode> variables, Flux<Val> timerMillis) {
        return trueIn(leftHand, variables, timerMillis)
                .map(this::negateVal);
    }

    @Attribute(docs = "TODO")
    public Flux<Val> periodicToggle(Val leftHand, Map<String, JsonNode> variables, Flux<Val> initialValue, Flux<Val> authorizedTimeInMillis,
                                    Flux<Val> unauthorizedTimeInMillis, Flux<Val> startTime) {
        return periodicToggle(leftHand, variables, initialValue, authorizedTimeInMillis, unauthorizedTimeInMillis, startTime, SYSTEM_DEFAULT_TIMEZONE_FLUX);
    }

    @Attribute(docs = "TODO")
    public Flux<Val> periodicToggle(Val leftHand, Map<String, JsonNode> variables, Flux<Val> initialValue, Flux<Val> authorizedTimeInMillis,
                                    Flux<Val> unauthorizedTimeInMillis, Flux<Val> startTime, Flux<Val> timeZone) {
        enforceUndefinedLeftHand(leftHand);

        return Flux.combineLatest(this::combinePeriodic, initialValue, authorizedTimeInMillis, unauthorizedTimeInMillis, startTime, timeZone)
                .switchMap(tuple -> {
                    var periodStartLocalTime = LocalTime.parse(tuple.getT4().getText());
                    var localOffset = toOffset(ZoneId.systemDefault(), periodStartLocalTime);
                    var offset = toOffset(convertToZoneId(tuple.getT5()), periodStartLocalTime);
                    var adjustedPeriodStart = periodStartLocalTime.atOffset(localOffset).withOffsetSameInstant(offset)
                            .toLocalTime();

                    return Flux.create(sink -> PeriodProducer.builder()
                            .initiallyAuthorized(tuple.getT1().getBoolean())
                            .authorizedTimeInMillis(tuple.getT2().get().asLong())
                            .unauthorizedTimeInMillis(tuple.getT3().get().asLong())
                            .periodStartTime(adjustedPeriodStart)
                            .currentTime(LocalTime.now())
                            .listener(new PeriodListener(sink))
                            .build().startScheduling(), OverflowStrategy.LATEST);
                });
    }

    private Tuple5<Val, Val, Val, Val, Val> combinePeriodic(Object[] objects) {
        if (objects.length != 5) throw new IllegalArgumentException("exactly 5 arguments must be provided");
        for (Object object : objects) {
            if (!(object instanceof Val)) throw new IllegalArgumentException("argument must be of type Val");

        }

        return Tuples.of((Val) objects[0], (Val) objects[1], (Val) objects[2], (Val) objects[3], (Val) objects[4]);
    }

    private void enforceUndefinedLeftHand(Val val) {
        throw new IllegalArgumentException("left hand value must be undefined but is of type" + val.getValType());
    }

    private Val negateVal(Val val) {
        return Val.of(!val.getBoolean());
    }


    private ZoneOffset toOffset(ZoneId zoneId, LocalTime localTime) {
        return zoneId.getRules().getOffset(LocalDate.now().atTime(localTime));
    }

    private ZoneId convertToZoneId(Val value) {
        if (value == null || value.isUndefined() || value.isNull() || !value.isTextual()) return ZoneId.systemDefault();

        final String text = value.getText() == null ? "" : value.getText().trim();
        final String zoneIdStr = text.length() == 0 ? "system" : text;
        if ("system".equalsIgnoreCase(zoneIdStr)) {
            return ZoneId.systemDefault();
        } else if (ZoneId.SHORT_IDS.containsKey(zoneIdStr)) {
            return ZoneId.of(zoneIdStr, ZoneId.SHORT_IDS);
        }
        return ZoneId.of(zoneIdStr);
    }

}