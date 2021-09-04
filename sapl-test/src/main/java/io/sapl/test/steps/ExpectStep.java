package io.sapl.test.steps;

import java.time.Duration;
import java.util.function.Predicate;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;

import org.hamcrest.Matcher;

/**
 * This step is in charge of defining the expected results.
 * Next Step available : {@link VerifyStep} or again an {@link ExpectStep} -> therefore returning composite {@link ExpectOrVerifyStep}
 */
public interface ExpectStep {

    //Sync expect methods

    /**
     * Asserts that the first emitted {@link AuthorizationDecision} of the policy evaluation is a {@link Decision#PERMIT}
     *
     * @return {@link VerifyStep} to verify your test case.
     */
    VerifyStep expectPermit();

    /**
     * Asserts that the first emitted {@link AuthorizationDecision} of the policy evaluation is a {@link Decision#DENY}
     *
     * @return {@link VerifyStep} to verify your test case.
     */
    VerifyStep expectDeny();

    /**
     * Asserts that the first emitted {@link AuthorizationDecision} of the policy evaluation is a {@link Decision#INDETERMINATE}
     *
     * @return {@link VerifyStep} to verify your test case.
     */
    VerifyStep expectIndeterminate();

    /**
     * Asserts that the first emitted {@link AuthorizationDecision} of the policy evaluation is a {@link Decision#NOT_APPLICABLE}
     *
     * @return {@link VerifyStep} to verify your test case.
     */
    VerifyStep expectNotApplicable();

    /**
     * Allow custom validation of {@link AuthorizationDecision}
     *
     * @param An {@link AuthorizationDecision} object which has to be equal to the first emitted {@link AuthorizationDecision}
     * @return {@link VerifyStep} to verify your test case.
     */
    VerifyStep expect(AuthorizationDecision authDec);

    /**
     * Allow custom validation of {@link AuthorizationDecision}
     *
     * @param pred {@link Predicate<AuthorizationDecision>} to validate the first emitted {@link AuthorizationDecision}
     * @return {@link VerifyStep} to verify your test case.
     */
    VerifyStep expect(Predicate<AuthorizationDecision> pred);

    /**
     * Allow custom validation of {@link AuthorizationDecision}
     *
     * @param {@link Matcher<AuthorizationDecision>} to validate the first emitted {@link AuthorizationDecision}
     * @return {@link VerifyStep} to verify your test case.
     */
    VerifyStep expect(Matcher<AuthorizationDecision> matcher);


    //Async expect methods


    /**
     * Asserts that the current emitted {@link AuthorizationDecision} of the policy evaluation is a {@link Decision#PERMIT}
     *
     * @return {@link ExpectOrVerifyStep} to define another {@link ExpectStep} or {@link VerifyStep}
     */
    ExpectOrVerifyStep expectNextPermit();

    /**
     * Asserts that the next @param emitted values of {@link AuthorizationDecision} of the policy evaluation is a {@link Decision#PERMIT}
     *
     * @return {@link ExpectOrVerifyStep} to define another {@link ExpectStep} or {@link VerifyStep}
     */
    ExpectOrVerifyStep expectNextPermit(Integer count);

    /**
     * Asserts that the current emitted {@link AuthorizationDecision} of the policy evaluation is a {@link Decision#DENY}
     *
     * @return {@link ExpectOrVerifyStep} to define another {@link ExpectStep} or {@link VerifyStep}
     */
    ExpectOrVerifyStep expectNextDeny();

    /**
     * Asserts that the next @param emitted values of {@link AuthorizationDecision} of the policy evaluation is a {@link Decision#DENY}
     *
     * @return {@link ExpectOrVerifyStep} to define another {@link ExpectStep} or {@link VerifyStep}
     */
    ExpectOrVerifyStep expectNextDeny(Integer count);

    /**
     * Asserts that the current emitted {@link AuthorizationDecision} of the policy evaluation is a {@link Decision#INDETERMINATE}
     *
     * @return {@link ExpectOrVerifyStep} to define another {@link ExpectStep} or {@link VerifyStep}
     */
    ExpectOrVerifyStep expectNextIndeterminate();

    /**
     * Asserts that the next @param emitted values of {@link AuthorizationDecision} of the policy evaluation is a {@link Decision#INDETERMINATE}
     *
     * @return {@link ExpectOrVerifyStep} to define another {@link ExpectStep} or {@link VerifyStep}
     */
    ExpectOrVerifyStep expectNextIndeterminate(Integer count);

    /**
     * Asserts that the first emitted {@link AuthorizationDecision} of the policy evaluation is a {@link Decision#NOT_APPLICABLE}
     *
     * @return {@link ExpectOrVerifyStep} to define another {@link ExpectStep} or {@link VerifyStep}
     */
    ExpectOrVerifyStep expectNextNotApplicable();

    /**
     * Asserts that the next @param emitted values of {@link AuthorizationDecision} of the policy evaluation is a {@link Decision#NOT_APPLICABLE}
     *
     * @return {@link ExpectOrVerifyStep} to define another {@link ExpectStep} or {@link VerifyStep}
     */
    ExpectOrVerifyStep expectNextNotApplicable(Integer count);

    /**
     * Allow custom validation of {@link AuthorizationDecision}
     *
     * @param authDec An {@link AuthorizationDecision} object which has to be equal to the current emitted {@link AuthorizationDecision}
     * @return {@link ExpectOrVerifyStep} to define another {@link ExpectStep} or {@link VerifyStep}
     */
    ExpectOrVerifyStep expectNext(AuthorizationDecision authDec);

    /**
     * Allow custom validation of {@link AuthorizationDecision}
     *
     * @param {@link Matcher<AuthorizationDecision>} to validate the current emitted {@link AuthorizationDecision}
     * @return {@link ExpectOrVerifyStep} to define another {@link ExpectStep} or {@link VerifyStep}
     */
    ExpectOrVerifyStep expectNext(Matcher<AuthorizationDecision> matcher);

    /**
     * Allow custom validation of {@link AuthorizationDecision}
     *
     * @param pred {@link Predicate<AuthorizationDecision>} to validate the current emitted {@link AuthorizationDecision}
     * @return {@link ExpectOrVerifyStep} to define another {@link ExpectStep} or {@link VerifyStep}
     */
    ExpectOrVerifyStep expectNext(Predicate<AuthorizationDecision> pred);


    /**
     * Mock the return value of a PIP in the SAPL policy
     *
     * @param importName the reference in the SAPL policy to the PIP
     * @param returns    the mocked return value
     * @return {@link ExpectOrVerifyStep} to define another {@link ExpectStep} or {@link VerifyStep}
     */
    ExpectOrVerifyStep thenAttribute(String importName, Val returns);

    // handle virtual time

    /**
     * Pauses the evaluation of steps
     *
     * @param duration Pause for this {#link Duration}
     * @return {@link ExpectOrVerifyStep} to define another {@link ExpectStep} or {@link VerifyStep}
     */
    ExpectOrVerifyStep thenAwait(Duration duration);

    /**
     * Lets the stream play out for a given {#link Duration} but fails the test if any signal occurs during that time
     *
     * @param duration Wait for this {#link Duration}
     * @return {@link ExpectOrVerifyStep} to define another {@link ExpectStep} or {@link VerifyStep}
     */
    ExpectOrVerifyStep expectNoEvent(Duration duration);
}