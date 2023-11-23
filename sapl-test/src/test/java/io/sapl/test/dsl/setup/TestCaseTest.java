package io.sapl.test.dsl.setup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.sapl.test.Helper;
import io.sapl.test.SaplTestException;
import io.sapl.test.dsl.interfaces.StepConstructor;
import io.sapl.test.grammar.sAPLTest.ExpectChain;
import io.sapl.test.grammar.sAPLTest.FixtureRegistration;
import io.sapl.test.grammar.sAPLTest.GivenStep;
import io.sapl.test.grammar.sAPLTest.TestException;
import io.sapl.test.grammar.sAPLTest.TestSuite;
import io.sapl.test.grammar.sAPLTest.Value;
import io.sapl.test.steps.ExpectOrVerifyStep;
import io.sapl.test.steps.GivenOrWhenStep;
import io.sapl.test.steps.VerifyStep;
import io.sapl.test.steps.WhenStep;
import java.util.Collections;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TestCaseTest {
    @Mock
    private StepConstructor stepConstructorMock;
    @Mock
    private TestSuite testSuiteMock;
    @Mock
    private io.sapl.test.grammar.sAPLTest.TestCase dslTestCase;

    private final MockedStatic<org.assertj.core.api.Assertions> assertionsMockedStatic = mockStatic(org.assertj.core.api.Assertions.class, Answers.RETURNS_DEEP_STUBS);

    @AfterEach
    void tearDown() {
        assertionsMockedStatic.close();
    }

    private List<GivenStep> mockGivenSteps(final List<GivenStep> givenSteps) {
        final var mockedGivenSteps = Helper.mockEList(givenSteps);
        when(dslTestCase.getGivenSteps()).thenReturn(mockedGivenSteps);
        return mockedGivenSteps;
    }

    private List<FixtureRegistration> mockFixtureRegistrations(final List<FixtureRegistration> fixtureRegistrations) {
        final var mockedFixtureRegistrations = Helper.mockEList(fixtureRegistrations);
        when(dslTestCase.getRegistrations()).thenReturn(mockedFixtureRegistrations);
        return mockedFixtureRegistrations;
    }

    private io.sapl.test.grammar.sAPLTest.Object mockEnvironment() {
        final var environment = mock(io.sapl.test.grammar.sAPLTest.Object.class);
        when(dslTestCase.getEnvironment()).thenReturn(environment);
        return environment;
    }

    private GivenOrWhenStep mockTestFixture(final List<FixtureRegistration> fixtureRegistrations, final io.sapl.test.grammar.sAPLTest.Object environment, final boolean needsMocks) {
        final var testFixtureMock = mock(GivenOrWhenStep.class);
        when(stepConstructorMock.buildTestFixture(eq(fixtureRegistrations), any(TestSuite.class), eq(environment), eq(needsMocks))).thenReturn(testFixtureMock);
        return testFixtureMock;
    }

    private void mockTestCaseWithTestException(io.sapl.test.grammar.sAPLTest.TestCase testCaseWithExceptionMock) {
        final var testExceptionMock = mock(TestException.class);
        when(testCaseWithExceptionMock.getExpect()).thenReturn(testExceptionMock);

        assertionsMockedStatic.when(() -> Assertions.assertThatExceptionOfType(SaplTestException.class).isThrownBy(any())).thenAnswer(invocationOnMock -> {
            if (invocationOnMock.getArgument(0) instanceof ThrowableAssert.ThrowingCallable callable) {
                callable.call();
            } else {
                fail("No proper ThrowingCallable was passed as an argument!");
            }
            return null;
        });
    }

    private VerifyStep mockTestBuildingChain(final List<GivenStep> givenSteps, final GivenOrWhenStep testFixture, final io.sapl.test.grammar.sAPLTest.TestCase testCase) {
        final var expectMock = mock(ExpectChain.class);
        when(testCase.getExpect()).thenReturn(expectMock);

        final var whenStepMock = mock(WhenStep.class);
        when(stepConstructorMock.constructWhenStep(givenSteps, testFixture)).thenReturn(whenStepMock);

        final var expectOrVerifyStepMock = mock(ExpectOrVerifyStep.class);
        when(stepConstructorMock.constructExpectStep(testCase, whenStepMock)).thenReturn(expectOrVerifyStepMock);

        final var verifyStepMock = mock(VerifyStep.class);
        when(stepConstructorMock.constructVerifyStep(testCase, expectOrVerifyStepMock)).thenReturn(verifyStepMock);

        return verifyStepMock;
    }

    private Runnable assertDynamicTestAndGetRunnable() {
        when(dslTestCase.getName()).thenReturn("test1");

        final var result = io.sapl.test.dsl.setup.TestCase.from(stepConstructorMock, testSuiteMock, dslTestCase);

        assertEquals("test1", result.getIdentifier());

        return result;
    }

    @Test
    void from_withStepConstructorBeingNull_throwsSaplTestException() {
        final var exception = assertThrows(SaplTestException.class, () -> TestCase.from(null, testSuiteMock, dslTestCase));

        assertEquals("One or more parameter(s) are null", exception.getMessage());
    }

    @Test
    void from_withTestSuiteBeingNull_throwsSaplTestException() {
        final var exception = assertThrows(SaplTestException.class, () -> TestCase.from(stepConstructorMock, null, dslTestCase));

        assertEquals("One or more parameter(s) are null", exception.getMessage());
    }

    @Test
    void from_withTestCaseBeingNull_throwsSaplTestException() {
        final var exception = assertThrows(SaplTestException.class, () -> TestCase.from(stepConstructorMock, testSuiteMock, null));

        assertEquals("One or more parameter(s) are null", exception.getMessage());
    }

    @Test
    void from_withStepConstructorAndTestSuiteAndTestCaseBeingNull_throwsSaplTestException() {
        final var exception = assertThrows(SaplTestException.class, () -> TestCase.from(null, null, null));

        assertEquals("One or more parameter(s) are null", exception.getMessage());
    }

    @Test
    void from_withNullTestCaseName_throwsSaplTestException() {
        when(dslTestCase.getName()).thenReturn(null);

        final var exception = assertThrows(SaplTestException.class, () -> TestCase.from(stepConstructorMock, testSuiteMock, dslTestCase));

        assertEquals("Name of the test case is null", exception.getMessage());
    }

    @Test
    void from_withNullEnvironmentBuildsFixtureWithoutEnvironment_returnsTestCase() throws Throwable {
        when(dslTestCase.getEnvironment()).thenReturn(null);

        final var fixtureRegistrationsMock = mockFixtureRegistrations(Collections.emptyList());
        final var givenStepsMock = mockGivenSteps(Collections.emptyList());

        final var testFixtureMock = mockTestFixture(fixtureRegistrationsMock, null, false);

        final var verifyStepMock = mockTestBuildingChain(givenStepsMock, testFixtureMock, dslTestCase);

        assertDynamicTestAndGetRunnable().run();

        verify(verifyStepMock, times(1)).verify();
    }

    @Test
    void from_withNonObjectEnvironmentBuildsFixtureWithoutEnvironment_returnsTestCase() throws Throwable {
        final var valueMock = mock(Value.class);
        when(dslTestCase.getEnvironment()).thenReturn(valueMock);

        final var fixtureRegistrationsMock = mockFixtureRegistrations(Collections.emptyList());
        final var givenStepsMock = mockGivenSteps(Collections.emptyList());

        final var testFixtureMock = mockTestFixture(fixtureRegistrationsMock, null, false);

        final var verifyStepMock = mockTestBuildingChain(givenStepsMock, testFixtureMock, dslTestCase);

        assertDynamicTestAndGetRunnable().run();

        verify(verifyStepMock, times(1)).verify();
    }

    @Test
    void from_withEnvironmentBuildsFixtureWithEnvironment_returnsTestCase() throws Throwable {
        final var environmentMock = mockEnvironment();
        final var fixtureRegistrationsMock = mockFixtureRegistrations(Collections.emptyList());
        final var givenStepsMock = mockGivenSteps(Collections.emptyList());

        final var testFixtureMock = mockTestFixture(fixtureRegistrationsMock, environmentMock, false);

        final var verifyStepMock = mockTestBuildingChain(givenStepsMock, testFixtureMock, dslTestCase);

        assertDynamicTestAndGetRunnable().run();

        verify(verifyStepMock, times(1)).verify();
    }

    @Test
    void from_withNullFixtureRegistrations_returnsTestCase() throws Throwable {
        final var environmentMock = mockEnvironment();

        when(dslTestCase.getRegistrations()).thenReturn(null);

        final var givenStepsMock = mockGivenSteps(Collections.emptyList());

        final var testFixtureMock = mockTestFixture(null, environmentMock, false);

        final var verifyStepMock = mockTestBuildingChain(givenStepsMock, testFixtureMock, dslTestCase);

        assertDynamicTestAndGetRunnable().run();

        verify(verifyStepMock, times(1)).verify();
    }

    @Test
    void from_withNullGivenStepsNeedsNoMocks_returnsTestCase() throws Throwable {
        final var environmentMock = mockEnvironment();

        final var fixtureRegistrationsMock = mockFixtureRegistrations(Collections.emptyList());

        when(dslTestCase.getGivenSteps()).thenReturn(null);

        final var testFixtureMock = mockTestFixture(fixtureRegistrationsMock, environmentMock, false);

        final var verifyStepMock = mockTestBuildingChain(null, testFixtureMock, dslTestCase);

        assertDynamicTestAndGetRunnable().run();

        verify(verifyStepMock, times(1)).verify();
    }

    @Test
    void from_withGivenStepsNeedsMocks_returnsTestCase() throws Throwable {
        final var environmentMock = mockEnvironment();

        final var fixtureRegistrationsMock = mockFixtureRegistrations(Collections.emptyList());

        final var givenStepMock = mock(GivenStep.class);
        final var givenStepsMock = mockGivenSteps(List.of(givenStepMock));

        final var testFixtureMock = mockTestFixture(fixtureRegistrationsMock, environmentMock, true);

        final var verifyStepMock = mockTestBuildingChain(givenStepsMock, testFixtureMock, dslTestCase);

        assertDynamicTestAndGetRunnable().run();

        verify(verifyStepMock, times(1)).verify();
    }

    @Test
    void from_testFixtureBuilderThrowsSaplTestException_throwsSaplTestException() {
        final var environmentMock = mockEnvironment();

        final var fixtureRegistrationsMock = mockFixtureRegistrations(Collections.emptyList());

        mockGivenSteps(Collections.emptyList());

        when(stepConstructorMock.buildTestFixture(fixtureRegistrationsMock, testSuiteMock, environmentMock, false)).thenThrow(new SaplTestException("could not build fixture"));

        final var test = assertDynamicTestAndGetRunnable();

        final var exception = assertThrows(SaplTestException.class, test::run);

        assertEquals("could not build fixture", exception.getMessage());
    }

    @Test
    void from_withExpectedTestException_returnsTestCase() throws Throwable {
        final var environmentMock = mockEnvironment();

        final var fixtureRegistrationsMock = mockFixtureRegistrations(Collections.emptyList());

        final var givenStepsMock = mockGivenSteps(Collections.emptyList());

        final var testFixtureMock = mockTestFixture(fixtureRegistrationsMock, environmentMock, false);

        mockTestCaseWithTestException(dslTestCase);

        assertDynamicTestAndGetRunnable().run();

        verify(stepConstructorMock, times(1)).constructWhenStep(givenStepsMock, testFixtureMock);
    }

    @Test
    void from_whenStepBuilderThrowsSaplTestException_throwsSaplTestException() {
        final var environmentMock = mockEnvironment();

        final var fixtureRegistrationsMock = mockFixtureRegistrations(Collections.emptyList());

        final var givenStepsMock = mockGivenSteps(Collections.emptyList());

        final var testFixtureMock = mockTestFixture(fixtureRegistrationsMock, environmentMock, false);

        when(stepConstructorMock.constructWhenStep(givenStepsMock, testFixtureMock)).thenThrow(new SaplTestException("could not build fixture"));

        final var test = assertDynamicTestAndGetRunnable();

        final var exception = assertThrows(SaplTestException.class, test::run);

        assertEquals("could not build fixture", exception.getMessage());
    }

    @Test
    void from_expectStepBuilderThrowsSaplTestException_throwsSaplTestException() {
        final var environmentMock = mockEnvironment();

        final var fixtureRegistrationsMock = mockFixtureRegistrations(Collections.emptyList());

        final var givenStepsMock = mockGivenSteps(Collections.emptyList());

        final var testFixtureMock = mockTestFixture(fixtureRegistrationsMock, environmentMock, false);

        final var whenStepMock = mock(WhenStep.class);
        when(stepConstructorMock.constructWhenStep(givenStepsMock, testFixtureMock)).thenReturn(whenStepMock);
        when(stepConstructorMock.constructExpectStep(dslTestCase, whenStepMock)).thenThrow(new SaplTestException("could not build expectStep"));

        final var test = assertDynamicTestAndGetRunnable();

        final var exception = assertThrows(SaplTestException.class, test::run);

        assertEquals("could not build expectStep", exception.getMessage());
    }

    @Test
    void from_verifyStepBuilderThrowsSaplTestException_throwsSaplTestException() {
        final var environmentMock = mockEnvironment();

        final var fixtureRegistrationsMock = mockFixtureRegistrations(Collections.emptyList());

        final var givenStepsMock = mockGivenSteps(Collections.emptyList());

        final var testFixtureMock = mockTestFixture(fixtureRegistrationsMock, environmentMock, false);

        final var whenStepMock = mock(WhenStep.class);
        when(stepConstructorMock.constructWhenStep(givenStepsMock, testFixtureMock)).thenReturn(whenStepMock);

        final var expectOrVerifyStepMock = mock(ExpectOrVerifyStep.class);
        when(stepConstructorMock.constructExpectStep(dslTestCase, whenStepMock)).thenReturn(expectOrVerifyStepMock);

        when(stepConstructorMock.constructVerifyStep(dslTestCase, expectOrVerifyStepMock)).thenThrow(new SaplTestException("could not build verifyStep"));

        final var test = assertDynamicTestAndGetRunnable();

        final var exception = assertThrows(SaplTestException.class, test::run);

        assertEquals("could not build verifyStep", exception.getMessage());
    }

    @Test
    void from_verifyThrowsSaplTestException_throwsSaplTestException() {
        final var environmentMock = mockEnvironment();

        final var fixtureRegistrationsMock = mockFixtureRegistrations(Collections.emptyList());

        final var givenStepsMock = mockGivenSteps(Collections.emptyList());

        final var testFixtureMock = mockTestFixture(fixtureRegistrationsMock, environmentMock, false);

        final var whenStepMock = mock(WhenStep.class);
        when(stepConstructorMock.constructWhenStep(givenStepsMock, testFixtureMock)).thenReturn(whenStepMock);

        final var expectOrVerifyStepMock = mock(ExpectOrVerifyStep.class);
        when(stepConstructorMock.constructExpectStep(dslTestCase, whenStepMock)).thenReturn(expectOrVerifyStepMock);

        final var verifyStepMock = mock(VerifyStep.class);
        when(stepConstructorMock.constructVerifyStep(dslTestCase, expectOrVerifyStepMock)).thenReturn(verifyStepMock);

        doThrow(new SaplTestException("could not verify")).when(verifyStepMock).verify();

        final var test = assertDynamicTestAndGetRunnable();

        final var exception = assertThrows(SaplTestException.class, test::run);

        assertEquals("could not verify", exception.getMessage());
    }
}