package io.sapl.test.dsl.setup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.sapl.test.dsl.interpreter.DefaultExpectStepConstructor;
import io.sapl.test.dsl.interpreter.DefaultTestFixtureConstructor;
import io.sapl.test.dsl.interpreter.DefaultVerifyStepConstructor;
import io.sapl.test.dsl.interpreter.DefaultWhenStepConstructor;
import io.sapl.test.grammar.sAPLTest.FixtureRegistration;
import io.sapl.test.grammar.sAPLTest.GivenStep;
import io.sapl.test.grammar.sAPLTest.TestCase;
import io.sapl.test.grammar.sAPLTest.TestSuite;
import io.sapl.test.steps.ExpectOrVerifyStep;
import io.sapl.test.steps.ExpectStep;
import io.sapl.test.steps.GivenOrWhenStep;
import io.sapl.test.steps.VerifyStep;
import io.sapl.test.steps.WhenStep;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultStepConstructorTest {
    @Mock
    private DefaultExpectStepConstructor defaultExpectStepConstructorMock;
    @Mock
    private DefaultTestFixtureConstructor defaultTestFixtureConstructorMock;
    @Mock
    private DefaultVerifyStepConstructor verifyStepBuilderMock;
    @Mock
    private DefaultWhenStepConstructor whenStepBuilderMock;
    @InjectMocks
    private DefaultStepConstructor defaultStepConstructor;

    @Test
    void constructExpectStep_callsExpectStepBuilder_returnsExpectStep() {
        final var testCaseMock = mock(TestCase.class);
        final var whenStepMock = mock(WhenStep.class);

        final var expectStepMock = mock(ExpectStep.class);

        when(defaultExpectStepConstructorMock.constructExpectStep(testCaseMock, whenStepMock)).thenReturn(expectStepMock);

        final var result = defaultStepConstructor.constructExpectStep(testCaseMock, whenStepMock);

        assertEquals(expectStepMock, result);
    }

    @Test
    void buildTestFixture_callsTestFixtureBuilderWithoutMocks_returnsGivenOrWhenStep() {
        final var fixtureRegistrations = List.<FixtureRegistration>of();
        final var testSuiteMock = mock(TestSuite.class);
        final var environmentMock = mock(io.sapl.test.grammar.sAPLTest.Object.class);

        final var givenOrWhenStepMock = mock(GivenOrWhenStep.class);

        when(defaultTestFixtureConstructorMock.buildTestFixture(fixtureRegistrations, testSuiteMock, environmentMock, false)).thenReturn(givenOrWhenStepMock);

        final var result = defaultStepConstructor.buildTestFixture(fixtureRegistrations, testSuiteMock, environmentMock, false);

        assertEquals(givenOrWhenStepMock, result);
    }

    @Test
    void buildTestFixture_callsTestFixtureBuilderWithMocks_returnsGivenOrWhenStep() {
        final var fixtureRegistrations = List.<FixtureRegistration>of();
        final var testSuiteMock = mock(TestSuite.class);
        final var environmentMock = mock(io.sapl.test.grammar.sAPLTest.Object.class);

        final var givenOrWhenStepMock = mock(GivenOrWhenStep.class);

        when(defaultTestFixtureConstructorMock.buildTestFixture(fixtureRegistrations, testSuiteMock, environmentMock, true)).thenReturn(givenOrWhenStepMock);

        final var result = defaultStepConstructor.buildTestFixture(fixtureRegistrations, testSuiteMock, environmentMock, true);

        assertEquals(givenOrWhenStepMock, result);
    }

    @Test
    void constructVerifyStep_callsVerifyStepBuilder_returnsVerifyStep() {
        final var testCaseMock = mock(TestCase.class);
        final var expectOrVerifyStepMock = mock(ExpectOrVerifyStep.class);

        final var verifyStepMock = mock(VerifyStep.class);

        when(verifyStepBuilderMock.constructVerifyStep(testCaseMock, expectOrVerifyStepMock)).thenReturn(verifyStepMock);

        final var result = defaultStepConstructor.constructVerifyStep(testCaseMock, expectOrVerifyStepMock);

        assertEquals(verifyStepMock, result);
    }

    @Test
    void constructWhenStep() {
        final var givenSteps = List.<GivenStep>of();
        final var givenOrWhenStepMock = mock(GivenOrWhenStep.class);

        final var whenStepMock = mock(WhenStep.class);

        when(whenStepBuilderMock.constructWhenStep(givenSteps, givenOrWhenStepMock)).thenReturn(whenStepMock);

        final var result = defaultStepConstructor.constructWhenStep(givenSteps, givenOrWhenStepMock);

        assertEquals(whenStepMock, result);
    }
}