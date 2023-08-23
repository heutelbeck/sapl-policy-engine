package io.sapl.test.services;

import static io.sapl.test.Imports.whenParentValue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.sapl.test.grammar.sAPLTest.Attribute;
import io.sapl.test.grammar.sAPLTest.AttributeWithParameters;
import io.sapl.test.grammar.sAPLTest.ParameterMatcher;
import io.sapl.test.grammar.sAPLTest.TemporalAmount;
import io.sapl.test.grammar.sAPLTest.Val;
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
import org.mockito.AdditionalAnswers;
import org.mockito.ArgumentCaptor;

class AttributeInterpreterTest {

    private AttributeInterpreter attributeInterpreter;
    private ValInterpreter valInterpreterMock;
    private MatcherInterpreter matcherInterpreterMock;
    private GivenOrWhenStep givenOrWhenStepMock;

    @BeforeEach
    void setUp() {
        valInterpreterMock = mock(ValInterpreter.class);
        matcherInterpreterMock = mock(MatcherInterpreter.class);
        givenOrWhenStepMock = mock(GivenOrWhenStep.class);

        attributeInterpreter = new AttributeInterpreter(valInterpreterMock, matcherInterpreterMock);
    }


    @Nested
    @DisplayName("Interpret attribute")
    class InterpretAttributeTests {
        @Test
        void interpretAttribute_whenReturnValueIsNull_returnsGivenOrWhenStepWithExpectedAttributeMocking() {
            final var attributeMock = mock(Attribute.class);

            when(attributeMock.getImportName()).thenReturn("fooAttribute");
            when(attributeMock.getReturn()).thenReturn(null);
            when(givenOrWhenStepMock.givenAttribute("fooAttribute")).thenReturn(givenOrWhenStepMock);

            final var result = attributeInterpreter.interpretAttribute(givenOrWhenStepMock, attributeMock);

            assertEquals(givenOrWhenStepMock, result);
        }

        @Test
        void interpretAttribute_whenReturnValueIsEmpty_returnsGivenOrWhenStepWithExpectedAttributeMocking() {
            final var attributeMock = mock(Attribute.class);
            final var eListMock = Helper.mockEList(List.<Val>of());
            when(attributeMock.getImportName()).thenReturn("fooAttribute");
            when(attributeMock.getReturn()).thenReturn(eListMock);
            when(givenOrWhenStepMock.givenAttribute("fooAttribute")).thenReturn(givenOrWhenStepMock);

            final var result = attributeInterpreter.interpretAttribute(givenOrWhenStepMock, attributeMock);

            assertEquals(givenOrWhenStepMock, result);
        }

        @Test
        void interpretAttribute_withReturnValueAndNoDuration_returnsGivenOrWhenStepWithExpectedAttributeMocking() {
            final var valMock = mock(Val.class);
            final var saplValMock = mock(io.sapl.api.interpreter.Val.class);
            final var eListMock = Helper.mockEList(List.of(valMock));
            final var attributeMock = mock(Attribute.class);

            when(attributeMock.getImportName()).thenReturn("fooAttribute");
            when(attributeMock.getReturn()).thenReturn(eListMock);

            when(valInterpreterMock.getValFromReturnValue(valMock)).thenReturn(saplValMock);
            when(attributeMock.getAmount()).thenReturn(null);

            when(givenOrWhenStepMock.givenAttribute("fooAttribute", saplValMock)).thenReturn(givenOrWhenStepMock);

            final var result = attributeInterpreter.interpretAttribute(givenOrWhenStepMock, attributeMock);

            assertEquals(givenOrWhenStepMock, result);
        }

        @Test
        void interpretAttribute_withReturnValueAndDuration_returnsGivenOrWhenStepWithExpectedAttributeMocking() {
            final var valMock = mock(Val.class);
            final var saplValMock = mock(io.sapl.api.interpreter.Val.class);
            final var eListMock = Helper.mockEList(List.of(valMock));
            final var attributeMock = mock(Attribute.class);
            final var temporalAmountMock = mock(TemporalAmount.class);

            when(attributeMock.getImportName()).thenReturn("fooAttribute");
            when(attributeMock.getReturn()).thenReturn(eListMock);

            when(valInterpreterMock.getValFromReturnValue(valMock)).thenReturn(saplValMock);
            when(attributeMock.getAmount()).thenReturn(temporalAmountMock);
            when(temporalAmountMock.getSeconds()).thenReturn(5);

            when(givenOrWhenStepMock.givenAttribute("fooAttribute", Duration.ofSeconds(5L), saplValMock)).thenReturn(givenOrWhenStepMock);

            final var result = attributeInterpreter.interpretAttribute(givenOrWhenStepMock, attributeMock);

            assertEquals(givenOrWhenStepMock, result);
        }
    }

    @Nested
    @DisplayName("Interpret attribute with parameters")
    class InterpretAttributeWithParameters {
        private AttributeWithParameters attributeWithParametersMock;
        private ParameterMatcher parentMatcherMock;
        private Matcher<io.sapl.api.interpreter.Val> parentValueMatcherMock;
        private Val returnValMock;
        private io.sapl.api.interpreter.Val returnValueMock;

        @BeforeEach
        void setUp() {
            attributeWithParametersMock = mock(AttributeWithParameters.class);
            when(attributeWithParametersMock.getImportName()).thenReturn("fooAttribute");

            parentMatcherMock = mock(ParameterMatcher.class);
            when(attributeWithParametersMock.getParentMatcher()).thenReturn(parentMatcherMock);

            parentValueMatcherMock = mock(Matcher.class);
            when(matcherInterpreterMock.getValMatcherFromParameterMatcher(parentMatcherMock)).thenReturn(parentValueMatcherMock);

            returnValMock = mock(Val.class);
            when(attributeWithParametersMock.getReturn()).thenReturn(returnValMock);

            returnValueMock = mock(io.sapl.api.interpreter.Val.class);
            when(valInterpreterMock.getValFromReturnValue(returnValMock)).thenReturn(returnValueMock);
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
            when(valInterpreterMock.getValFromReturnValue(returnValMock)).thenReturn(returnValueMock);

            final var eListMock = mock(EList.class, AdditionalAnswers.delegatesTo(List.of()));
            when(attributeWithParametersMock.getParameters()).thenReturn(eListMock);

            when(givenOrWhenStepMock.givenAttribute("fooAttribute", whenParentValue(parentValueMatcherMock), returnValueMock)).thenReturn(givenOrWhenStepMock);

            final var result = attributeInterpreter.interpretAttributeWithParameters(givenOrWhenStepMock, attributeWithParametersMock);

            assertEquals(givenOrWhenStepMock, result);
        }

        @Test
        void interpretAttributeWithParameters_withMultipleArguments_returnsGivenOrWhenStepWithExpectedAttributeMocking() {
            final var val1Mock = mock(Val.class);
            final var val2Mock = mock(Val.class);
            final var eListMock = mock(EList.class, AdditionalAnswers.delegatesTo(List.of(val1Mock, val2Mock)));
            when(attributeWithParametersMock.getParameters()).thenReturn(eListMock);

            final var matcher1Mock = mock(Matcher.class);
            final var matcher2Mock = mock(Matcher.class);

            when(valInterpreterMock.getValMatcherFromVal(val1Mock)).thenReturn(matcher1Mock);
            when(valInterpreterMock.getValMatcherFromVal(val2Mock)).thenReturn(matcher2Mock);

            final var attributeParametersArgumentCaptor = ArgumentCaptor.forClass(AttributeParameters.class);

            when(givenOrWhenStepMock.givenAttribute(eq("fooAttribute"), attributeParametersArgumentCaptor.capture(), eq(returnValueMock))).thenReturn(givenOrWhenStepMock);

            final var result = attributeInterpreter.interpretAttributeWithParameters(givenOrWhenStepMock, attributeWithParametersMock);

            final var attributeParameters = attributeParametersArgumentCaptor.getValue();

            assertArrayEquals(List.of(matcher1Mock, matcher2Mock).toArray(), attributeParameters.getArgumentMatchers().getMatchers());
            assertEquals(parentValueMatcherMock, attributeParameters.getParentValueMatcher().getMatcher());

            assertEquals(givenOrWhenStepMock, result);
        }
    }


}