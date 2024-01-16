/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

package io.sapl.test.dsl.interpreter;

import static io.sapl.test.Imports.whenParentValue;
import static io.sapl.test.dsl.ParserUtil.compareArgumentToStringLiteral;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.sapl.api.interpreter.Val;
import io.sapl.test.dsl.ParserUtil;
import io.sapl.test.grammar.sAPLTest.AnyVal;
import io.sapl.test.grammar.sAPLTest.Attribute;
import io.sapl.test.grammar.sAPLTest.AttributeWithParameters;
import io.sapl.test.grammar.sAPLTest.IsJsonNull;
import io.sapl.test.grammar.sAPLTest.StringLiteral;
import io.sapl.test.grammar.sAPLTest.ValMatcher;
import io.sapl.test.grammar.sAPLTest.ValWithMatcher;
import io.sapl.test.grammar.sAPLTest.ValWithValue;
import io.sapl.test.grammar.sAPLTest.Value;
import io.sapl.test.grammar.services.SAPLTestGrammarAccess;
import io.sapl.test.mocking.attribute.models.AttributeParameters;
import io.sapl.test.steps.GivenOrWhenStep;
import java.time.Duration;
import java.util.List;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AttributeInterpreterTest {
    @Mock
    private ValInterpreter        valInterpreterMock;
    @Mock
    private ValMatcherInterpreter matcherInterpreterMock;
    @Mock
    private DurationInterpreter   durationInterpreterMock;
    @InjectMocks
    private AttributeInterpreter  attributeInterpreter;
    @Mock
    private GivenOrWhenStep       givenOrWhenStepMock;

    private Attribute buildAttribute(final String input) {
        return ParserUtil.buildExpression(input, SAPLTestGrammarAccess::getGivenStepRule);
    }

    private AttributeWithParameters buildAttributeWithParameters(final String input) {
        return ParserUtil.buildExpression(input, SAPLTestGrammarAccess::getGivenStepRule);
    }

    @Nested
    @DisplayName("Interpret attribute")
    class InterpretAttributeTests {

        @Test
        void interpretAttribute_whenReturnValueIsNull_returnsGivenOrWhenStepWithExpectedAttributeMocking() {
            final var attribute = mock(Attribute.class);

            when(attribute.getName()).thenReturn("fooAttribute");
            when(attribute.getReturnValue()).thenReturn(null);

            when(givenOrWhenStepMock.givenAttribute("fooAttribute")).thenReturn(givenOrWhenStepMock);

            final var result = attributeInterpreter.interpretAttribute(givenOrWhenStepMock, attribute);

            assertEquals(givenOrWhenStepMock, result);
        }

        @Test
        void interpretAttribute_whenReturnValueIsEmpty_returnsGivenOrWhenStepWithExpectedAttributeMocking() {
            final var attribute = buildAttribute("attribute \"fooAttribute\"");
            when(givenOrWhenStepMock.givenAttribute("fooAttribute")).thenReturn(givenOrWhenStepMock);

            final var result = attributeInterpreter.interpretAttribute(givenOrWhenStepMock, attribute);

            assertEquals(givenOrWhenStepMock, result);
        }

        @Test
        void interpretAttribute_withReturnValueAndNoDuration_returnsGivenOrWhenStepWithExpectedAttributeMocking() {
            final var attribute = buildAttribute("attribute \"fooAttribute\" returns \"Foo\"");

            final var expectedReturnValue = Val.of("Foo");

            when(valInterpreterMock.getValFromValue(compareArgumentToStringLiteral("Foo")))
                    .thenReturn(expectedReturnValue);

            when(givenOrWhenStepMock.givenAttribute("fooAttribute", expectedReturnValue))
                    .thenReturn(givenOrWhenStepMock);

            final var result = attributeInterpreter.interpretAttribute(givenOrWhenStepMock, attribute);

            assertEquals(givenOrWhenStepMock, result);
        }

        @Test
        void interpretAttribute_withReturnValueAndDuration_returnsGivenOrWhenStepWithExpectedAttributeMocking() {
            final var expectedReturnValue = Val.of("Foo");

            final var attribute = buildAttribute("attribute \"fooAttribute\" returns \"Foo\" for \"PT5S\"");

            when(valInterpreterMock.getValFromValue(compareArgumentToStringLiteral("Foo")))
                    .thenReturn(expectedReturnValue);
            when(durationInterpreterMock.getJavaDurationFromDuration(attribute.getDuration()))
                    .thenReturn(Duration.ofSeconds(5));

            when(givenOrWhenStepMock.givenAttribute("fooAttribute", Duration.ofSeconds(5), expectedReturnValue))
                    .thenReturn(givenOrWhenStepMock);

            final var result = attributeInterpreter.interpretAttribute(givenOrWhenStepMock, attribute);

            assertEquals(givenOrWhenStepMock, result);
        }
    }

    @Nested
    @DisplayName("Interpret attribute with parameters")
    class InterpretAttributeWithParameters {
        @Mock
        private Matcher<io.sapl.api.interpreter.Val> parentValueMatcherMock;

        @Test
        void interpretAttributeWithParameters_withNullParameters_returnsGivenOrWhenStepWithExpectedAttributeMocking() {
            final var attributeWithParametersMock = mock(AttributeWithParameters.class);

            when(attributeWithParametersMock.getName()).thenReturn("fooAttribute");

            final var valMatcherMock = mock(ValMatcher.class);
            when(attributeWithParametersMock.getParentMatcher()).thenReturn(valMatcherMock);

            when(matcherInterpreterMock.getHamcrestValMatcher(valMatcherMock)).thenReturn(parentValueMatcherMock);

            final var returnValueMock = mock(Value.class);
            when(attributeWithParametersMock.getReturnValue()).thenReturn(returnValueMock);

            final var returnValMock = mock(Val.class);
            when(valInterpreterMock.getValFromValue(returnValueMock)).thenReturn(returnValMock);

            when(attributeWithParametersMock.getParameters()).thenReturn(null);

            when(givenOrWhenStepMock.givenAttribute("fooAttribute", whenParentValue(parentValueMatcherMock),
                    returnValMock)).thenReturn(givenOrWhenStepMock);

            final var result = attributeInterpreter.interpretAttributeWithParameters(givenOrWhenStepMock,
                    attributeWithParametersMock);

            assertEquals(givenOrWhenStepMock, result);
        }

        @Test
        void interpretAttributeWithParameters_withEmptyParameters_returnsGivenOrWhenStepWithExpectedAttributeMocking() {
            final var attributeWithParameters = buildAttributeWithParameters(
                    "attribute \"fooAttribute\" with parent value \"Foo\" returns \"BAR\"");

            final var expectedReturnValue = Val.of("BAR");
            when(valInterpreterMock.getValFromValue(compareArgumentToStringLiteral("BAR")))
                    .thenReturn(expectedReturnValue);
            when(matcherInterpreterMock.getHamcrestValMatcher(attributeWithParameters.getParentMatcher()))
                    .thenReturn(parentValueMatcherMock);

            when(givenOrWhenStepMock.givenAttribute("fooAttribute", whenParentValue(parentValueMatcherMock),
                    expectedReturnValue)).thenReturn(givenOrWhenStepMock);

            final var result = attributeInterpreter.interpretAttributeWithParameters(givenOrWhenStepMock,
                    attributeWithParameters);

            assertEquals(givenOrWhenStepMock, result);
        }

        @Test
        void interpretAttributeWithParameters_withMultipleParameters_returnsGivenOrWhenStepWithExpectedAttributeMocking() {
            final var matcher1Mock                      = mock(Matcher.class);
            final var matcher2Mock                      = mock(Matcher.class);
            final var attributeParametersArgumentCaptor = ArgumentCaptor.forClass(AttributeParameters.class);

            final var attributeWithParameters = buildAttributeWithParameters(
                    "attribute \"fooAttribute\" with parent value any and parameters \"FOO1\", matching null returns \"BAR\"");

            final var expectedReturnValue = Val.of("BAR");
            when(valInterpreterMock.getValFromValue(compareArgumentToStringLiteral("BAR")))
                    .thenReturn(expectedReturnValue);
            when(matcherInterpreterMock.getHamcrestValMatcher(any(AnyVal.class))).thenReturn(parentValueMatcherMock);
            when(matcherInterpreterMock.getHamcrestValMatcher(any(ValWithValue.class))).thenAnswer(invocationOnMock -> {
                final ValWithValue valWithValue = invocationOnMock.getArgument(0);

                assertEquals("FOO1", ((StringLiteral) valWithValue.getValue()).getString());
                return matcher1Mock;
            });

            when(matcherInterpreterMock.getHamcrestValMatcher(any(ValWithMatcher.class)))
                    .thenAnswer(invocationOnMock -> {
                        final ValWithMatcher valWithMatcher = invocationOnMock.getArgument(0);

                        assertInstanceOf(IsJsonNull.class, valWithMatcher.getMatcher());
                        return matcher2Mock;
                    });

            when(givenOrWhenStepMock.givenAttribute(eq("fooAttribute"), attributeParametersArgumentCaptor.capture(),
                    eq(expectedReturnValue))).thenReturn(givenOrWhenStepMock);

            final var result = attributeInterpreter.interpretAttributeWithParameters(givenOrWhenStepMock,
                    attributeWithParameters);

            final var attributeParameters = attributeParametersArgumentCaptor.getValue();

            assertArrayEquals(List.of(matcher1Mock, matcher2Mock).toArray(),
                    attributeParameters.getArgumentMatchers().getMatchers());
            assertEquals(parentValueMatcherMock, attributeParameters.getParentValueMatcher().getMatcher());

            assertEquals(givenOrWhenStepMock, result);
        }
    }

}
