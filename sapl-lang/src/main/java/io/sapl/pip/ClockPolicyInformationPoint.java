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
import io.sapl.api.validation.Text;
import io.sapl.pip.Schedules.ScheduleListener;
import io.sapl.pip.Schedules.ScheduleProducer;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink.OverflowStrategy;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@NoArgsConstructor
@PolicyInformationPoint(name = ClockPolicyInformationPoint.NAME, description = ClockPolicyInformationPoint.DESCRIPTION)
public class ClockPolicyInformationPoint {

    public static final String NAME = "clock";

    public static final String DESCRIPTION = "Policy Information Point and attributes for retrieving current date and time information";
    private static final String PARAMETER_NOT_AN_ISO_8601_STRING = "Parameter not an ISO 8601 string";

    @Attribute(docs = "Returns the current date and time in the given time zone (e.g. 'UTC', 'ECT', 'Europe/Berlin', 'system') as an ISO-8601 string with time offset.")
    public Flux<Val> now(@Text Val zone, Map<String, JsonNode> variables) {
        final ZoneId zoneId = convertToZoneId(zone);
        final OffsetDateTime now = Instant.now().atZone(zoneId).toOffsetDateTime();
        return Val.fluxOf(now.toString());
    }

    @Attribute(docs = "Returns the current date and time in the given time zone (e.g. 'UTC', 'ECT', 'Europe/Berlin', 'system') as an  millisecond-based instant, measured from 1970-01-01T00:00Z (UTC).")
    public Flux<Val> millis(Val zone, Map<String, JsonNode> variables) {
        final ZoneId zoneId = convertToZoneId(zone);
        return Flux.just(Val.of(Instant.now().atZone(zoneId).toInstant().toEpochMilli()));
    }

    @Attribute(docs = "Returns the system default time-zone.")
    public Flux<Val> timeZone(@Text Val value, Map<String, JsonNode> variables) {
        return Val.fluxOf(ZoneId.systemDefault().toString());
    }

    @Attribute(docs = "Emits every x seconds the current date and time in the given time zone (e.g. 'UTC', 'ECT', 'Europe/Berlin', 'system') as an ISO-8601 string with offset. x is the passed number value.")
    public Flux<Val> ticker(Val zone, Map<String, JsonNode> variables, Flux<Val> intervallInSeconds) {
        final ZoneId zoneId = convertToZoneId(zone);
        return intervallInSeconds.switchMap(seconds -> {
            if (!seconds.isNumber())
                return Flux.error(new PolicyEvaluationException(
                        String.format("ticker parameter not a number. Was: %s", seconds.toString())));

            var secondsValue = seconds.get().asLong();

            if (secondsValue == 0)
                return Flux.error(new PolicyEvaluationException("ticker parameter must not be zero"));

            return Flux.<String>create(sink ->
                    Schedulers.single().schedulePeriodically(
                            () -> sink.next(Instant.now().atZone(zoneId).toOffsetDateTime().toString()),
                            0L, secondsValue, TimeUnit.SECONDS));

        }).map(Val::of);
    }


    @Attribute(docs = "Returns if the local clock is after the provided time in the specified time-zone. Only the time of the day is taken into account. Emits value every time, the result changes.")
    public Flux<Val> after(@Text Val zone, Map<String, JsonNode> variables, Flux<Val> time) {
        return time.switchMap(referenceTime -> {
            var referenceLocalTime = LocalTime.parse(referenceTime.getText());
            var localOffset = toOffset(ZoneId.systemDefault(), referenceLocalTime);
            var offset = toOffset(convertToZoneId(zone), referenceLocalTime);
            var adjustedReference = referenceLocalTime.atOffset(localOffset).withOffsetSameInstant(offset)
                    .toLocalTime();

            return Flux.create(sink -> ScheduleProducer.builder()
                    .referenceTime(adjustedReference)
                    .currentTime(LocalTime.now())
                    .listener(new ScheduleListener(sink))
                    .build().startScheduling(), OverflowStrategy.LATEST);
        });
    }

    @Attribute(docs = "Returns if the local clock is before the provided local time. Only the time of the day is taken into account. Emits value every time, the result changes.")
    public Flux<Val> before(Val zone, Map<String, JsonNode> variables, Flux<Val> time) {
        return after(zone, variables, time)
                .map(this::negateVal);
    }

    /*
     *  var timeout = <timeout("10s")>; <sofort "false" , nach 10s "true">
     */
    @Attribute(docs = "Sets a timer for the provided number of seconds. Emits Val.FALSE when created and Val.TRUE after timer elapsed.")
    public Flux<Val> timer(Val leftHand, Map<String, JsonNode> variables, Flux<Val> timerSeconds) {
        return timerSeconds.switchMap(seconds -> {
            if (!seconds.isNumber())
                return Flux.error(new PolicyEvaluationException(
                        String.format("timer parameter not a number. Was: %s", seconds)));

            var secondsValue = seconds.get().asLong();
            if (secondsValue == 0)
                return Flux.error(new PolicyEvaluationException("ticker parameter must not be zero"));

            return Flux.concat(
                    Mono.just(Val.FALSE),
                    Mono.just(Val.TRUE).delayElement(Duration.ofSeconds(seconds.get().numberValue().longValue()))
            );
        });
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