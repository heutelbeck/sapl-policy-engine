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
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.sapl.api.functions.FunctionInvocation;
import io.sapl.api.model.Value;
import io.sapl.functions.DefaultFunctionBroker;
import lombok.val;

@DisplayName("String codepoint positions through the function broker")
class StringCodePointTests {

    private static final String SYMBOL = "\uD83D\uDE42";
    private static final String TEXT   = "a" + SYMBOL + "b";

    @ParameterizedTest(name = "{0}")
    @MethodSource("codePointCalls")
    void whenFunctionsMeasureOrSliceTextThenTheyCountCodePoints(String description, String function,
            List<Value> parameters, Value expected) {
        val broker = new DefaultFunctionBroker();
        broker.load(new StringFunctionLibrary());
        broker.load(new StandardFunctionLibrary());

        val result = broker.evaluateFunction(new FunctionInvocation(function, parameters));

        assertThat(result).as(description).isEqualTo(expected);
    }

    static Stream<Arguments> codePointCalls() {
        return Stream.of(call("string length", "string.length", Value.of(3), Value.of(TEXT)),
                call("standard text length", "standard.length", Value.of(3), Value.of(TEXT)),
                call("combining mark counts separately", "string.length", Value.of(2), Value.of("e\u0301")),
                call("empty text", "string.length", Value.of(0), Value.of("")),
                call("first occurrence", "string.indexOf", Value.of(2), Value.of(TEXT), Value.of("b")),
                call("last occurrence", "string.lastIndexOf", Value.of(3), Value.of(TEXT + SYMBOL), Value.of(SYMBOL)),
                call("first empty search", "string.indexOf", Value.of(0), Value.of(TEXT), Value.of("")),
                call("last empty search", "string.lastIndexOf", Value.of(3), Value.of(TEXT), Value.of("")),
                call("absent search", "string.indexOf", Value.of(-1), Value.of(TEXT), Value.of("z")),
                call("substring after first point", "string.substring", Value.of(SYMBOL + "b"), Value.of(TEXT),
                        Value.of(1)),
                call("substring at end", "string.substring", Value.of(""), Value.of(TEXT), Value.of(3)),
                call("complete supplementary point", "string.substringRange", Value.of(SYMBOL), Value.of(TEXT),
                        Value.of(1), Value.of(2)),
                call("after supplementary point", "string.substringRange", Value.of("b"), Value.of(TEXT), Value.of(2),
                        Value.of(3)),
                call("complete range", "string.substringRange", Value.of(TEXT), Value.of(TEXT), Value.of(0),
                        Value.of(3)),
                call("empty end range", "string.substringRange", Value.of(""), Value.of(TEXT), Value.of(3),
                        Value.of(3)),
                call("start before zero", "string.substringRange", Value.error("Start index out of bounds: -1."),
                        Value.of(TEXT), Value.of(-1), Value.of(9)),
                call("start checked before end", "string.substringRange", Value.error("Start index out of bounds: 4."),
                        Value.of(TEXT), Value.of(4), Value.of(0)),
                call("end beyond codepoint length", "string.substringRange", Value.error("End index out of bounds: 4."),
                        Value.of(TEXT), Value.of(0), Value.of(4)),
                call("reversed range", "string.substringRange", Value.error("End index out of bounds: 1."),
                        Value.of(TEXT), Value.of(2), Value.of(1)),
                call("supplementary left padding", "string.leftPad", Value.of(SYMBOL + SYMBOL + "a" + SYMBOL),
                        Value.of("a" + SYMBOL), Value.of(4), Value.of(SYMBOL)),
                call("supplementary right padding", "string.rightPad", Value.of("a" + SYMBOL + SYMBOL + SYMBOL),
                        Value.of("a" + SYMBOL), Value.of(4), Value.of(SYMBOL)),
                call("ASCII pad follows point count", "string.leftPad", Value.of("00a" + SYMBOL),
                        Value.of("a" + SYMBOL), Value.of(4), Value.of("0")),
                call("padding at current length", "string.rightPad", Value.of(TEXT), Value.of(TEXT), Value.of(3),
                        Value.of(SYMBOL)),
                call("negative padding target is unchanged", "string.leftPad", Value.of(TEXT), Value.of(TEXT),
                        Value.of(-1), Value.of(SYMBOL)),
                call("invalid pad checked before no-op", "string.rightPad",
                        Value.error("Padding must be exactly one character."), Value.of(TEXT), Value.of(-1),
                        Value.of("ab")),
                call("empty pad", "string.leftPad", Value.error("Padding must be exactly one character."),
                        Value.of(TEXT), Value.of(5), Value.of("")),
                call("padding count cap", "string.leftPad",
                        Value.error("Count exceeds maximum allowed value of 10000."), Value.of(""), Value.of(10001),
                        Value.of(SYMBOL)),
                call("standard array still counts elements", "standard.length", Value.of(2),
                        Value.ofArray(Value.of(SYMBOL), Value.of("a"))),
                call("standard object still counts keys", "standard.length", Value.of(0), Value.EMPTY_OBJECT),
                call("standard wrong type unchanged", "standard.length",
                        Value.error("Argument must be a text, array, or object."), Value.of(1)));
    }

    private static Arguments call(String description, String function, Value expected, Value... parameters) {
        return arguments(description, function, List.of(parameters), expected);
    }
}
