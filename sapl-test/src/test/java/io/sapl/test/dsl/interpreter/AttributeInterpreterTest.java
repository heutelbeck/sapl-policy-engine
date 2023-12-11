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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.sapl.test.Helper;
import io.sapl.test.grammar.sAPLTest.Attribute;
import io.sapl.test.grammar.sAPLTest.AttributeWithParameters;
import io.sapl.test.grammar.sAPLTest.ValMatcher;
import io.sapl.test.grammar.sAPLTest.Value;
import io.sapl.test.mocking.attribute.models.AttributeParameters;
import io.sapl.test.steps.GivenOrWhenStep;
import java.time.Duration;
import java.util.List;
import org.eclipse.emf.common.util.EList;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.AdditionalAnswers;
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

    @Nested
    @DisplayName("Interpret attribute")
    class InterpretAttributeTests {
        @Test
        void interpretAttribute_whenReturnValueIsNull_returnsGivenOrWhenStepWithExpectedAttributeMocking() {
            final var attributeMock = mock(Attribute.class);

            when(attributeMock.getName()).thenReturn("fooAttribute");
            when(attributeMock.getReturnValue()).thenReturn(null);
            when(givenOrWhenStepMock.givenAttribute("fooAttribute")).thenReturn(givenOrWhenStepMock);

            final var result = attributeInterpreter.interpretAttribute(givenOrWhenStepMock, attributeMock);

            assertEquals(givenOrWhenStepMock, result);
        }

        @Test
        void interpretAttribute_whenReturnValueIsEmpty_returnsGivenOrWhenStepWithExpectedAttributeMocking() {
            final var attributeMock = mock(Attribute.class);
            final var eListMock     = Helper.mockEList(List.<Value>of());
            when(attributeMock.getName()).thenReturn("fooAttribute");
            when(attributeMock.getReturnValue()).thenReturn(eListMock);
            when(givenOrWhenStepMock.givenAttribute("fooAttribute")).thenReturn(givenOrWhenStepMock);

            final var result = attributeInterpreter.interpretAttribute(givenOrWhenStepMock, attributeMock);

            assertEquals(givenOrWhenStepMock, result);
        }

        @Test
        void interpretAttribute_withReturnValueAndNoDuration_returnsGivenOrWhenStepWithExpectedAttributeMocking() {
            final var valMock       = mock(Value.class);
            final var saplValMock   = mock(io.sapl.api.interpreter.Val.class);
            final var eListMock     = Helper.mockEList(List.of(valMock));
            final var attributeMock = mock(Attribute.class);

            when(attributeMock.getName()).thenReturn("fooAttribute");
            when(attributeMock.getReturnValue()).thenReturn(eListMock);

            when(valInterpreterMock.getValFromValue(valMock)).thenReturn(saplValMock);
            when(attributeMock.getDuration()).thenReturn(null);

            when(givenOrWhenStepMock.givenAttribute("fooAttribute", saplValMock)).thenReturn(givenOrWhenStepMock);

            final var result = attributeInterpreter.interpretAttribute(givenOrWhenStepMock, attributeMock);

            assertEquals(givenOrWhenStepMock, result);
        }

        @Test
        void interpretAttribute_withReturnValueAndDuration_returnsGivenOrWhenStepWithExpectedAttributeMocking() {
            final var valMock       = mock(Value.class);
            final var saplValMock   = mock(io.sapl.api.interpreter.Val.class);
            final var eListMock     = Helper.mockEList(List.of(valMock));
            final var attributeMock = mock(Attribute.class);
            final var durationMock  = mock(io.sapl.test.grammar.sAPLTest.Duration.class);

            when(attributeMock.getName()).thenReturn("fooAttribute");
            when(attributeMock.getReturnValue()).thenReturn(eListMock);
            when(attributeMock.getDuration()).thenReturn(durationMock);

            when(valInterpreterMock.getValFromValue(valMock)).thenReturn(saplValMock);

            when(durationInterpreterMock.getJavaDurationFromDuration(durationMock)).thenReturn(Duration.ofSeconds(5));

            when(givenOrWhenStepMock.givenAttribute("fooAttribute", Duration.ofSeconds(5), saplValMock))
                    .thenReturn(givenOrWhenStepMock);

            final var result = attributeInterpreter.interpretAttribute(givenOrWhenStepMock, attributeMock);

            assertEquals(givenOrWhenStepMock, result);
        }
    }

    @Nested
    @DisplayName("Interpret attribute with parameters")
    class InterpretAttributeWithParameters {
        private AttributeWithParameters              attributeWithParametersMock;
        private ValMatcher                           parentMatcherMock;
        private Matcher<io.sapl.api.interpreter.Val> parentValueMatcherMock;
        private Value                                returnValMock;
        private io.sapl.api.interpreter.Val          returnValueMock;

        @BeforeEach
        void setUp() {
            attributeWithParametersMock = mock(AttributeWithParameters.class);
            when(attributeWithParametersMock.getName()).thenReturn("fooAttribute");

            parentMatcherMock = mock(ValMatcher.class);
            when(attributeWithParametersMock.getParentMatcher()).thenReturn(parentMatcherMock);

            parentValueMatcherMock = mock(Matcher.class);
            when(matcherInterpreterMock.getHamcrestValMatcher(parentMatcherMock)).thenReturn(parentValueMatcherMock);

            returnValMock = mock(Value.class);
            when(attributeWithParametersMock.getReturnValue()).thenReturn(returnValMock);

            returnValueMock = mock(io.sapl.api.interpreter.Val.class);
            when(valInterpreterMock.getValFromValue(returnValMock)).thenReturn(returnValueMock);
        }

        @Test
        void interpretAttributeWithParameters_withNullArguments_returnsGivenOrWhenStepWithExpectedAttributeMocking() {
            when(attributeWithParametersMock.getParameters()).thenReturn(null);

            when(givenOrWhenStepMock.givenAttribute("fooAttribute", whenParentValue(parentValueMatcherMock), returnValueMock)).thenReturn(givenOrWhenStepMock);

            final var result = attributeInterpreter.interpretAttributeWithParameters(givenOrWhenStepMock, attributeWithParametersMock);

            assertEquals(givenOrWhenStepMock, result);
        }

        @Test
        void interpretAttributeWithParameters_withEmptyArguments_returnsGivenOrWhenStepWithExpectedAttributeMocking() {
            when(valInterpreterMock.getValFromValue(returnValMock)).thenReturn(returnValueMock);

            final var eListMock = mock(EList.class, AdditionalAnswers.delegatesTo(List.of()));
            when(attributeWithParametersMock.getParameters()).thenReturn(eListMock);

            when(givenOrWhenStepMock.givenAttribute("fooAttribute", whenParentValue(parentValueMatcherMock), returnValueMock)).thenReturn(givenOrWhenStepMock);

            final var result = attributeInterpreter.interpretAttributeWithParameters(givenOrWhenStepMock, attributeWithParametersMock);

            assertEquals(givenOrWhenStepMock, result);
        }

        @Test
        void interpretAttributeWithParameters_withMultipleArguments_returnsGivenOrWhenStepWithExpectedAttributeMocking() {
            final var valMatcher1Mock = mock(ValMatcher.class);
            final var valMatcher2Mock = mock(ValMatcher.class);
            final var eListMock       = mock(EList.class,
                    AdditionalAnswers.delegatesTo(List.of(valMatcher1Mock, valMatcher2Mock)));
            when(attributeWithParametersMock.getParameters()).thenReturn(eListMock);

            final var matcher1Mock = mock(Matcher.class);
            final var matcher2Mock = mock(Matcher.class);

            when(matcherInterpreterMock.getHamcrestValMatcher(valMatcher1Mock)).thenReturn(matcher1Mock);
            when(matcherInterpreterMock.getHamcrestValMatcher(valMatcher2Mock)).thenReturn(matcher2Mock);

            final var attributeParametersArgumentCaptor = ArgumentCaptor.forClass(AttributeParameters.class);

            when(givenOrWhenStepMock.givenAttribute(eq("fooAttribute"), attributeParametersArgumentCaptor.capture(),
                    eq(returnValueMock))).thenReturn(givenOrWhenStepMock);

            final var result = attributeInterpreter.interpretAttributeWithParameters(givenOrWhenStepMock,
                    attributeWithParametersMock);

            final var attributeParameters = attributeParametersArgumentCaptor.getValue();

            assertArrayEquals(List.of(matcher1Mock, matcher2Mock).toArray(),
                    attributeParameters.getArgumentMatchers().getMatchers());
            assertEquals(parentValueMatcherMock, attributeParameters.getParentValueMatcher().getMatcher());

            assertEquals(givenOrWhenStepMock, result);
        }
    }

}
