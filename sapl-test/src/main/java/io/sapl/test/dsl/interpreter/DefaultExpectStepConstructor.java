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
package io.sapl.test.dsl.interpreter;

import io.sapl.test.SaplTestException;
import io.sapl.test.grammar.sAPLTest.TestCase;
import io.sapl.test.steps.ExpectStep;
import io.sapl.test.steps.WhenStep;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class DefaultExpectStepConstructor {

    private final AuthorizationSubscriptionInterpreter authorizationSubscriptionInterpreter;

    ExpectStep constructExpectStep(final TestCase testCase, final WhenStep whenStep) {
        if (testCase == null || whenStep == null) {
            throw new SaplTestException("TestCase or whenStep is null");
        }

        final var dslWhenStep = testCase.getWhenStep();

        if (dslWhenStep == null) {
            throw new SaplTestException("TestCase does not contain a whenStep");
        }

        final var authorizationSubscription = dslWhenStep.getAuthorizationSubscription();

        if (authorizationSubscription == null) {
            throw new SaplTestException("No AuthorizationSubscription found");
        }

        final var mappedAuthorizationSubscription = authorizationSubscriptionInterpreter
                .constructAuthorizationSubscription(authorizationSubscription);

        return whenStep.when(mappedAuthorizationSubscription);
    }
}
