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
package io.sapl.functions.libraries;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.sapl.api.functions.FunctionInvocation;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.Value;
import io.sapl.functions.DefaultFunctionBroker;
import lombok.val;

@DisplayName("Historical policy dates")
class HistoricalTemporalFunctionLibraryTests {

    @ParameterizedTest(name = "{0}({1}, {2}) = {3}")
    @CsvFileSource(resources = "/time-historical.tsv", delimiter = '\t')
    @DisplayName("the time library reads and writes historical ISO years through the broker")
    void whenPolicyDateIsHistoricalThenTheBrokerPreservesItsInstant(String function, String input, String second,
            String expected) {
        val broker = new DefaultFunctionBroker();
        broker.load(new TemporalFunctionLibrary());
        val arguments = new ArrayList<Value>();
        arguments.add(Value.of(input));
        if ("time.plusSeconds".equals(function) || "time.plusDays".equals(function)) {
            arguments.add(Value.of(Long.parseLong(second)));
        } else if (!"unused".equals(second)) {
            arguments.add(Value.of(second));
        }
        if ("time.timeBetween".equals(function)) {
            arguments.add(Value.of("NANOS"));
        }
        val actual = broker.evaluateFunction(new FunctionInvocation(function, arguments));
        if ("error".equals(expected)) {
            assertThat(actual).isInstanceOf(ErrorValue.class);
        } else if ("true".equals(expected) || "false".equals(expected)) {
            assertThat(actual).isEqualTo(Value.of(Boolean.parseBoolean(expected)));
        } else if ("time.timeBetween".equals(function)) {
            assertThat(actual).isEqualTo(Value.of(Long.parseLong(expected)));
        } else {
            assertThat(actual).isEqualTo(Value.of(expected));
            if (("time.plusSeconds".equals(function) || "time.plusDays".equals(function)
                    || "time.dateTimeAtOffset".equals(function)) && !expected.startsWith("-10000")
                    && !expected.startsWith("+10000")) {
                val roundTrip = broker
                        .evaluateFunction(new FunctionInvocation("time.plusSeconds", List.of(actual, Value.of(0))));
                assertThat(roundTrip).isEqualTo(actual);
            }
        }
    }

    @ParameterizedTest(name = "{0}({1}, {2}, {3}) = {4}")
    @CsvFileSource(resources = "/time-historical-zones.tsv", delimiter = '\t')
    @DisplayName("regional zones preserve their historical and future offsets at the supported year boundaries")
    void whenRegionalDatesReachTheCalendarBoundaryThenTheBrokerUsesTheZoneRules(String function, String first,
            String second, String third, String expected) {
        val broker = new DefaultFunctionBroker();
        broker.load(new TemporalFunctionLibrary());
        val arguments = new ArrayList<Value>();
        arguments.add(Value.of(first));
        arguments.add(Value.of(second));
        if (!"unused".equals(third)) {
            arguments.add(Value.of(third));
        }
        val actual = broker.evaluateFunction(new FunctionInvocation(function, arguments));
        assertThat(actual).isEqualTo(Value.of(expected));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = { "ACT", "AET", "AGT", "ART", "AST", "BET", "BST", "CAT", "CNT", "CST", "CTT", "EAT", "ECT",
            "IET", "IST", "JST", "MIT", "NET", "NST", "PLT", "PNT", "PRT", "PST", "SST", "VST", "EST", "MST", "HST" })
    @DisplayName("short zone aliases are refused at every explicit time-library zone entry")
    void whenZoneIsAShortAliasThenTheBrokerRefusesIt(String alias) {
        val broker = new DefaultFunctionBroker();
        broker.load(new TemporalFunctionLibrary());
        for (val invocation : List.of(
                new FunctionInvocation("time.dateTimeAtZone", List.of(Value.of("1880-01-01T13:00"), Value.of(alias))),
                new FunctionInvocation("time.timeInZone",
                        List.of(Value.of("13:00"), Value.of("1880-01-01"), Value.of(alias))),
                new FunctionInvocation("time.toZone", List.of(Value.of("1880-01-01T13:00:00Z"), Value.of(alias))))) {
            assertThat(broker.evaluateFunction(invocation)).as("%s", invocation).isInstanceOf(ErrorValue.class);
        }
    }

}
