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
import io.sapl.pip.Schedules.ScheduleListener;
import io.sapl.pip.Schedules.ScheduleProducer;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink.OverflowStrategy;
import reactor.core.publisher.Mono;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;

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
    public Flux<Val> millis(Map<String, JsonNode> variables) {
        return Flux.just(Val.of(Instant.now().toEpochMilli()));
    }

    public Flux<Val> dayOfTheWeek(@Text Val value, Map<String, JsonNode> variables) {
        final ZoneId zoneId = convertToZoneId(value.get());
        final OffsetDateTime now = Instant.now().atZone(zoneId).toOffsetDateTime();
        return Val.fluxOf(now.getDayOfWeek().name());
    }

    public Flux<Val> timeZone(@Text Val value, Map<String, JsonNode> variables) {
        return Val.fluxOf(ZoneId.systemDefault().toString());
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


    public Flux<Val> clockAfter(@Text Val zone, Map<String, JsonNode> variables, @Text Val time) {
        //e.g. time -> "15:00", "09:00" & zone -> "UTC" -> "Europe/Berlin"
        var referenceLocalTime = LocalTime.parse(time.getText());

        return Flux.create(sink -> ScheduleProducer.builder()
                .referenceTime(referenceLocalTime)
                .currentTime(LocalTime.now())
                .listener(new ScheduleListener(sink))
                .build().startScheduling(), OverflowStrategy.LATEST);
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

    private ZoneOffset toOffset(ZoneId zoneId) {
        Instant instant = Instant.now(); //can be LocalDateTime
        ZoneId systemZone = ZoneId.systemDefault(); // my timezone
        return systemZone.getRules().getOffset(instant);
    }

}