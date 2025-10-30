/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.functions;

import com.github.valfirst.slf4jtest.TestLogger;
import com.github.valfirst.slf4jtest.TestLoggerFactory;
import io.sapl.api.interpreter.Val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.event.Level;

import java.util.function.BiFunction;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingFunctionLibraryTests {

    private static final Val CTHULHU_MESSAGE = Val.of("Ph'nglui mglw'nafh Cthulhu R'lyeh wgah'nagl fhtagn");
    private static final Val SHOGGOTH_VALUE  = Val.of("The shoggoth approaches through non-Euclidean geometries");
    private static final Val NYARLATHOTEP    = Val.of("The Crawling Chaos whispers in a thousand tongues");
    private static final Val ERROR_VALUE     = Val.error("Hastur speaks the Unspeakable Name");

    private TestLogger testLogger;

    @BeforeEach
    void setupTestLogger() {
        testLogger = TestLoggerFactory.getTestLogger(LoggingFunctionLibrary.class);
        testLogger.clearAll();
    }

    @AfterEach
    void clearTestLogger() {
        testLogger.clearAll();
    }

    static Stream<Arguments> spyFunctions() {
        return Stream.of(
                Arguments.of("traceSpy", Level.TRACE, (BiFunction<Val, Val, Val>) LoggingFunctionLibrary::traceSpy),
                Arguments.of("debugSpy", Level.DEBUG, (BiFunction<Val, Val, Val>) LoggingFunctionLibrary::debugSpy),
                Arguments.of("infoSpy", Level.INFO, (BiFunction<Val, Val, Val>) LoggingFunctionLibrary::infoSpy),
                Arguments.of("warnSpy", Level.WARN, (BiFunction<Val, Val, Val>) LoggingFunctionLibrary::warnSpy),
                Arguments.of("errorSpy", Level.ERROR, (BiFunction<Val, Val, Val>) LoggingFunctionLibrary::errorSpy));
    }

    static Stream<Arguments> logFunctions() {
        return Stream.of(Arguments.of("trace", Level.TRACE, (BiFunction<Val, Val, Val>) LoggingFunctionLibrary::trace),
                Arguments.of("debug", Level.DEBUG, (BiFunction<Val, Val, Val>) LoggingFunctionLibrary::debug),
                Arguments.of("info", Level.INFO, (BiFunction<Val, Val, Val>) LoggingFunctionLibrary::info),
                Arguments.of("warn", Level.WARN, (BiFunction<Val, Val, Val>) LoggingFunctionLibrary::warn),
                Arguments.of("error", Level.ERROR, (BiFunction<Val, Val, Val>) LoggingFunctionLibrary::error));
    }

    @ParameterizedTest(name = "{0} returns input value unchanged")
    @MethodSource("spyFunctions")
    void spyFunctionReturnsValueUnchanged(String functionName, Level level, BiFunction<Val, Val, Val> function) {
        var result = function.apply(CTHULHU_MESSAGE, SHOGGOTH_VALUE);

        assertThat(result).isSameAs(SHOGGOTH_VALUE);
    }

    @ParameterizedTest(name = "{0} returns different value unchanged")
    @MethodSource("spyFunctions")
    void spyFunctionReturnsAnyValueUnchanged(String functionName, Level level, BiFunction<Val, Val, Val> function) {
        var result = function.apply(SHOGGOTH_VALUE, NYARLATHOTEP);

        assertThat(result).isSameAs(NYARLATHOTEP);
    }

    @ParameterizedTest(name = "{0} returns error value unchanged")
    @MethodSource("spyFunctions")
    void spyFunctionReturnsErrorValueUnchanged(String functionName, Level level, BiFunction<Val, Val, Val> function) {
        var result = function.apply(CTHULHU_MESSAGE, ERROR_VALUE);

        assertThat(result).isEqualTo(ERROR_VALUE);
    }

    @ParameterizedTest(name = "{0} returns undefined value unchanged")
    @MethodSource("spyFunctions")
    void spyFunctionReturnsUndefinedValueUnchanged(String functionName, Level level,
            BiFunction<Val, Val, Val> function) {
        var result = function.apply(CTHULHU_MESSAGE, Val.UNDEFINED);

        assertThat(result).isSameAs(Val.UNDEFINED);
    }

    @ParameterizedTest(name = "{0} logs at {1} level with correct message format")
    @MethodSource("spyFunctions")
    void spyFunctionLogsAtCorrectLevel(String functionName, Level expectedLevel, BiFunction<Val, Val, Val> function) {
        function.apply(CTHULHU_MESSAGE, SHOGGOTH_VALUE);

        assertThat(testLogger.getLoggingEvents()).hasSize(1);
        var event = testLogger.getLoggingEvents().getFirst();
        assertThat(event.getLevel()).isEqualTo(expectedLevel);
        assertThat(event.getMessage()).isEqualTo("[SAPL] {} {}");
        assertThat(event.getArguments()).containsExactly(CTHULHU_MESSAGE.getText(), SHOGGOTH_VALUE);
    }

    @ParameterizedTest(name = "{0} always returns true for regular values")
    @MethodSource("logFunctions")
    void logFunctionAlwaysReturnsTrue(String functionName, Level level, BiFunction<Val, Val, Val> function) {
        var result = function.apply(CTHULHU_MESSAGE, SHOGGOTH_VALUE);

        assertThat(result).isSameAs(Val.TRUE);
    }

    @ParameterizedTest(name = "{0} returns true for undefined values")
    @MethodSource("logFunctions")
    void logFunctionReturnsTrueForUndefined(String functionName, Level level, BiFunction<Val, Val, Val> function) {
        var result = function.apply(CTHULHU_MESSAGE, Val.UNDEFINED);

        assertThat(result).isSameAs(Val.TRUE);
    }

    @ParameterizedTest(name = "{0} returns true for error values")
    @MethodSource("logFunctions")
    void logFunctionReturnsTrueForErrors(String functionName, Level level, BiFunction<Val, Val, Val> function) {
        var result = function.apply(CTHULHU_MESSAGE, ERROR_VALUE);

        assertThat(result).isSameAs(Val.TRUE);
    }

    @ParameterizedTest(name = "{0} returns true for false values")
    @MethodSource("logFunctions")
    void logFunctionReturnsTrueForFalse(String functionName, Level level, BiFunction<Val, Val, Val> function) {
        var result = function.apply(CTHULHU_MESSAGE, Val.FALSE);

        assertThat(result).isSameAs(Val.TRUE);
    }

    @ParameterizedTest(name = "{0} logs at {1} level with correct message format")
    @MethodSource("logFunctions")
    void logFunctionLogsAtCorrectLevel(String functionName, Level expectedLevel, BiFunction<Val, Val, Val> function) {
        function.apply(CTHULHU_MESSAGE, SHOGGOTH_VALUE);

        assertThat(testLogger.getLoggingEvents()).hasSize(1);
        var event = testLogger.getLoggingEvents().getFirst();
        assertThat(event.getLevel()).isEqualTo(expectedLevel);
        assertThat(event.getMessage()).isEqualTo("[SAPL] {} {}");
        assertThat(event.getArguments()).containsExactly(CTHULHU_MESSAGE.getText(), SHOGGOTH_VALUE);
    }

}
