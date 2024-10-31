/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.test.steps;

import java.time.Duration;
import java.util.function.Predicate;

import org.hamcrest.Matcher;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;

/**
 * This step is in charge of defining the expected results. Next Step available
 * : {@link VerifyStep} or again an {@link ExpectStep}. Therefore, returning
 * composite {@link ExpectOrVerifyStep}
 */
public interface ExpectStep {

    // Sync expect methods

    /**
     * Asserts that the first emitted {@link AuthorizationDecision} of the policy
     * evaluation is a {@link io.sapl.api.pdp.Decision#PERMIT}
     *
     * @return {@link VerifyStep} to verify your test case.
     */
    VerifyStep expectPermit();

    /**
     * Asserts that the first emitted {@link AuthorizationDecision} of the policy
     * evaluation is a {@link io.sapl.api.pdp.Decision#DENY}
     *
     * @return {@link VerifyStep} to verify your test case.
     */
    VerifyStep expectDeny();

    /**
     * Asserts that the first emitted {@link AuthorizationDecision} of the policy
     * evaluation is a {@link io.sapl.api.pdp.Decision#INDETERMINATE}
     *
     * @return {@link VerifyStep} to verify your test case.
     */
    VerifyStep expectIndeterminate();

    /**
     * Asserts that the first emitted {@link AuthorizationDecision} of the policy
     * evaluation is a {@link io.sapl.api.pdp.Decision#NOT_APPLICABLE}
     *
     * @return {@link VerifyStep} to verify your test case.
     */
    VerifyStep expectNotApplicable();

    /**
     * Allow custom validation of {@link AuthorizationDecision}
     *
     * @param authDec An {@link AuthorizationDecision} object which has to be equal
     * to the first emitted {@link AuthorizationDecision}
     * @return {@link VerifyStep} to verify your test case.
     */
    VerifyStep expect(AuthorizationDecision authDec);

    /**
     * Allow custom validation of {@link AuthorizationDecision}
     *
     * @param pred {@code Predicate<AuthorizationDecision>} to validate the first
     * emitted {@link AuthorizationDecision}
     * @return {@link VerifyStep} to verify your test case.
     */
    VerifyStep expect(Predicate<AuthorizationDecision> pred);

    /**
     * Allow custom validation of {@link AuthorizationDecision}
     *
     * @param matcher {@link Matcher} to validate the first emitted
     * {@link AuthorizationDecision}
     * @return {@link VerifyStep} to verify your test case.
     */
    VerifyStep expect(Matcher<AuthorizationDecision> matcher);

    // Async expect methods

    /**
     * Asserts that the current emitted {@link AuthorizationDecision} of the policy
     * evaluation is a {@link io.sapl.api.pdp.Decision#PERMIT}
     *
     * @return {@link ExpectOrVerifyStep} to define another {@link ExpectStep} or
     * {@link VerifyStep}
     */
    ExpectOrVerifyStep expectNextPermit();

    /**
     * Asserts that the next @param emitted values of {@link AuthorizationDecision}
     * of the policy evaluation is a {@link io.sapl.api.pdp.Decision#PERMIT}
     *
     * @param count number of permits
     * @return {@link ExpectOrVerifyStep} to define another {@link ExpectStep} or
     * {@link VerifyStep}
     */
    ExpectOrVerifyStep expectNextPermit(Integer count);

    /**
     * Asserts that the current emitted {@link AuthorizationDecision} of the policy
     * evaluation is a {@link io.sapl.api.pdp.Decision#DENY}
     *
     * @return {@link ExpectOrVerifyStep} to define another {@link ExpectStep} or
     * {@link VerifyStep}
     */
    ExpectOrVerifyStep expectNextDeny();

    /**
     * Asserts that the next @param emitted values of {@link AuthorizationDecision}
     * of the policy evaluation is a {@link io.sapl.api.pdp.Decision#DENY}
     *
     * @param count expected number
     * @return {@link ExpectOrVerifyStep} to define another {@link ExpectStep} or
     * {@link VerifyStep}
     */
    ExpectOrVerifyStep expectNextDeny(Integer count);

    /**
     * Asserts that the current emitted {@link AuthorizationDecision} of the policy
     * evaluation is a {@link io.sapl.api.pdp.Decision#INDETERMINATE}
     *
     * @return {@link ExpectOrVerifyStep} to define another {@link ExpectStep} or
     * {@link VerifyStep}
     */
    ExpectOrVerifyStep expectNextIndeterminate();

    /**
     * Asserts that the next @param emitted values of {@link AuthorizationDecision}
     * of the policy evaluation is a {@link io.sapl.api.pdp.Decision#INDETERMINATE}
     *
     * @param count expected number
     * @return {@link ExpectOrVerifyStep} to define another {@link ExpectStep} or
     * {@link VerifyStep}
     */
    ExpectOrVerifyStep expectNextIndeterminate(Integer count);

    /**
     * Asserts that the first emitted {@link AuthorizationDecision} of the policy
     * evaluation is a {@link io.sapl.api.pdp.Decision#NOT_APPLICABLE}
     *
     * @return {@link ExpectOrVerifyStep} to define another {@link ExpectStep} or
     * {@link VerifyStep}
     */
    ExpectOrVerifyStep expectNextNotApplicable();

    /**
     * Asserts that the next @param emitted values of {@link AuthorizationDecision}
     * of the policy evaluation is a {@link io.sapl.api.pdp.Decision#NOT_APPLICABLE}
     *
     * @param count expected number
     * @return {@link ExpectOrVerifyStep} to define another {@link ExpectStep} or
     * {@link VerifyStep}
     */
    ExpectOrVerifyStep expectNextNotApplicable(Integer count);

    /**
     * Allow custom validation of {@link AuthorizationDecision}
     *
     * @param authDec An {@link AuthorizationDecision} object which has to be equal
     * to the current emitted {@link AuthorizationDecision}
     * @return {@link ExpectOrVerifyStep} to define another {@link ExpectStep} or
     * {@link VerifyStep}
     */
    ExpectOrVerifyStep expectNext(AuthorizationDecision authDec);

    /**
     * Allow custom validation of {@link AuthorizationDecision}
     *
     * @param matcher {@link Matcher} to validate the current emitted
     * {@link AuthorizationDecision}
     * @return {@link ExpectOrVerifyStep} to define another {@link ExpectStep} or
     * {@link VerifyStep}
     */
    ExpectOrVerifyStep expectNext(Matcher<AuthorizationDecision> matcher);

    /**
     * Allow custom validation of {@link AuthorizationDecision}
     *
     * @param pred {@link Predicate} to validate the current emitted
     * {@link AuthorizationDecision}
     * @return {@link ExpectOrVerifyStep} to define another {@link ExpectStep} or
     * {@link VerifyStep}
     */
    ExpectOrVerifyStep expectNext(Predicate<AuthorizationDecision> pred);

    /**
     * Mock the return value of a PIP in the SAPL policy
     *
     * @param importName the reference in the SAPL policy to the PIP
     * @param returns the mocked return value
     * @return {@link ExpectOrVerifyStep} to define another {@link ExpectStep} or
     * {@link VerifyStep}
     */
    ExpectOrVerifyStep thenAttribute(String importName, Val returns);

    // handle virtual time

    /**
     * Pauses the evaluation of steps
     *
     * @param duration Pause for this {#link Duration}
     * @return {@link ExpectOrVerifyStep} to define another {@link ExpectStep} or
     * {@link VerifyStep}
     */
    ExpectOrVerifyStep thenAwait(Duration duration);

    /**
     * Lets the stream play out for a given {#link Duration} but fails the test if
     * any signal occurs during that time
     *
     * @param duration Wait for this {#link Duration}
     * @return {@link ExpectOrVerifyStep} to define another {@link ExpectStep} or
     * {@link VerifyStep}
     */
    ExpectOrVerifyStep expectNoEvent(Duration duration);

}
