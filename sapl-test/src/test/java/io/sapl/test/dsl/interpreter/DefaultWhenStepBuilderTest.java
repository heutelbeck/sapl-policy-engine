package io.sapl.test.dsl.interpreter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.sapl.test.grammar.sAPLTest.Attribute;
import io.sapl.test.grammar.sAPLTest.AttributeWithParameters;
import io.sapl.test.grammar.sAPLTest.Function;
import io.sapl.test.grammar.sAPLTest.FunctionInvokedOnce;
import io.sapl.test.grammar.sAPLTest.GivenStep;
import io.sapl.test.grammar.sAPLTest.VirtualTime;
import io.sapl.test.steps.GivenOrWhenStep;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultWhenStepBuilderTest {

    private FunctionInterpreter functionInterpreterMock;
    private AttributeInterpreter attributeInterpreterMock;

    private GivenOrWhenStep saplUnitTestFixtureMock;
    private DefaultWhenStepBuilder defaultWhenStepBuilder;

    @BeforeEach
    void setUp() {
        functionInterpreterMock = mock(FunctionInterpreter.class);
        attributeInterpreterMock = mock(AttributeInterpreter.class);
        saplUnitTestFixtureMock = mock(GivenOrWhenStep.class);

        defaultWhenStepBuilder = new DefaultWhenStepBuilder(functionInterpreterMock, attributeInterpreterMock);
    }

    @Test
    void constructWhenStep_handlesNullGivenSteps_returnsGivenUnitTestFixture() {
        final var result = defaultWhenStepBuilder.constructWhenStep(null, saplUnitTestFixtureMock);

        assertEquals(saplUnitTestFixtureMock, result);
        verifyNoInteractions(saplUnitTestFixtureMock);
    }

    @Test
    void constructWhenStep_handlesEmptyGivenSteps_returnsGivenUnitTestFixture() {
        final var result = defaultWhenStepBuilder.constructWhenStep(List.of(), saplUnitTestFixtureMock);

        assertEquals(saplUnitTestFixtureMock, result);
        verifyNoInteractions(saplUnitTestFixtureMock);
    }

    @Test
    void constructWhenStep_handlesUnknownTypeOfGivenStep_returnsGivenUnitTestFixture() {
        final var unknownGivenStepMock = mock(GivenStep.class);
        final var result = defaultWhenStepBuilder.constructWhenStep(List.of(unknownGivenStepMock), saplUnitTestFixtureMock);

        assertEquals(saplUnitTestFixtureMock, result);
        verifyNoInteractions(saplUnitTestFixtureMock);
    }

    @Test
    void constructWhenStep_handlesFunctionGivenStep_returnsAdjustedUnitTestFixture() {
        final var functionMock = mock(Function.class);

        when(functionInterpreterMock.interpretFunction(saplUnitTestFixtureMock, functionMock)).thenReturn(saplUnitTestFixtureMock);

        final var result = defaultWhenStepBuilder.constructWhenStep(List.of(functionMock), saplUnitTestFixtureMock);

        assertEquals(saplUnitTestFixtureMock, result);
    }

    @Test
    void constructWhenStep_handlesFunctionInvokedOnceGivenStep_returnsAdjustedUnitTestFixture() {
        final var functionInvokedOnceMock = mock(FunctionInvokedOnce.class);

        when(functionInterpreterMock.interpretFunctionInvokedOnce(saplUnitTestFixtureMock, functionInvokedOnceMock)).thenReturn(saplUnitTestFixtureMock);

        final var result = defaultWhenStepBuilder.constructWhenStep(List.of(functionInvokedOnceMock), saplUnitTestFixtureMock);

        assertEquals(saplUnitTestFixtureMock, result);
    }

    @Test
    void constructWhenStep_handlesAttributeGivenStep_returnsAdjustedUnitTestFixture() {
        final var attributeMock = mock(Attribute.class);

        when(attributeInterpreterMock.interpretAttribute(saplUnitTestFixtureMock, attributeMock)).thenReturn(saplUnitTestFixtureMock);

        final var result = defaultWhenStepBuilder.constructWhenStep(List.of(attributeMock), saplUnitTestFixtureMock);

        assertEquals(saplUnitTestFixtureMock, result);
    }

    @Test
    void constructWhenStep_handlesAttributeWithParametersGivenStep_returnsAdjustedUnitTestFixture() {
        final var attributeWithParametersMock = mock(AttributeWithParameters.class);

        when(attributeInterpreterMock.interpretAttributeWithParameters(saplUnitTestFixtureMock, attributeWithParametersMock)).thenReturn(saplUnitTestFixtureMock);

        final var result = defaultWhenStepBuilder.constructWhenStep(List.of(attributeWithParametersMock), saplUnitTestFixtureMock);

        assertEquals(saplUnitTestFixtureMock, result);
    }

    @Test
    void constructWhenStep_handlesVirtualTimeGivenStep_returnsAdjustedUnitTestFixture() {
        final var virtualTimeMock = mock(VirtualTime.class);

        when(saplUnitTestFixtureMock.withVirtualTime()).thenReturn(saplUnitTestFixtureMock);

        final var result = defaultWhenStepBuilder.constructWhenStep(List.of(virtualTimeMock), saplUnitTestFixtureMock);

        assertEquals(saplUnitTestFixtureMock, result);
    }

    @Test
    void constructWhenStep_handlesMultipleGivenSteps_returnsAdjustedUnitTestFixture() {
        final var functionMock = mock(Function.class);
        final var attributeMock = mock(Attribute.class);
        final var virtualTimeMock = mock(VirtualTime.class);

        when(functionInterpreterMock.interpretFunction(saplUnitTestFixtureMock, functionMock)).thenReturn(saplUnitTestFixtureMock);
        when(attributeInterpreterMock.interpretAttribute(saplUnitTestFixtureMock, attributeMock)).thenReturn(saplUnitTestFixtureMock);
        when(saplUnitTestFixtureMock.withVirtualTime()).thenReturn(saplUnitTestFixtureMock);

        final var result = defaultWhenStepBuilder.constructWhenStep(List.of(virtualTimeMock, functionMock, attributeMock), saplUnitTestFixtureMock);

        assertEquals(saplUnitTestFixtureMock, result);
        verify(functionInterpreterMock, times(1)).interpretFunction(saplUnitTestFixtureMock, functionMock);
        verify(attributeInterpreterMock, times(1)).interpretAttribute(saplUnitTestFixtureMock, attributeMock);
        verify(saplUnitTestFixtureMock, times(1)).withVirtualTime();
        verifyNoMoreInteractions(saplUnitTestFixtureMock, functionInterpreterMock, attributeInterpreterMock);
    }
}