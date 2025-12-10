/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.test.unit;

import io.sapl.test.SaplTestException;
import io.sapl.test.SaplTestFixtureTemplate;
import io.sapl.test.steps.GivenStep;
import io.sapl.test.steps.WhenStep;
import io.sapl.test.utils.ClasspathHelper;

import java.util.function.Supplier;

/**
 * Fixture for constructing unit test cases for individual SAPL policies.
 * <p>
 * Provides a fluent API for building test cases that evaluate single SAPL
 * documents against authorization subscriptions. Supports mocking of functions
 * and attributes to isolate policy logic during testing.
 * <p>
 * Example usage:
 *
 * <pre>
 * var fixture = new SaplUnitTestFixture("policies/accessControl.sapl");
 * fixture.constructTestCaseWithMocks().givenFunction("time.now", Value.of("2025-01-15T10:00:00Z"))
 *         .when(AuthorizationSubscription.of("alice", "read", "document")).expectPermit().verify();
 * </pre>
 */
public class SaplUnitTestFixture extends SaplTestFixtureTemplate {

    private static final String ERROR_MESSAGE_MISSING_SAPL_DOCUMENT_NAME = """
            Before constructing a test case you have to specify the filename where to find your SAPL policy!

            Probably you forgot to call ".setSaplDocumentName("")\"""";

    private final Supplier<String> policySourceRetriever;

    /**
     * Creates a fixture for testing a policy loaded from the classpath.
     *
     * @param saplDocumentName path relative to your classpath to the sapl document.
     * If your policies are located at the root of the classpath or in the standard
     * path {@code "policies/"} in your {@code resources} folder you only have to
     * specify the name of the .sapl file. If your policies are located at some
     * special place you have to configure a relative path like
     * {@code "yourSpecialDirectory/policies/myPolicy.sapl"}
     */
    public SaplUnitTestFixture(String saplDocumentName) {
        this(saplDocumentName, true);
    }

    /**
     * Creates a fixture for testing a policy from a file path or directly from
     * source code.
     *
     * @param input the policy source code or file path
     * @param isFileInput true if input is a file path, false if it is the actual
     * policy source code
     */
    public SaplUnitTestFixture(String input, boolean isFileInput) {
        this.policySourceRetriever = () -> {
            if (input == null || input.isEmpty()) {
                throw new SaplTestException(ERROR_MESSAGE_MISSING_SAPL_DOCUMENT_NAME);
            }
            return isFileInput ? ClasspathHelper.readPolicyFromClasspath(input) : input;
        };
    }

    @Override
    public GivenStep constructTestCaseWithMocks() {
        return StepBuilder.newBuilderAtGivenStep(policySourceRetriever.get(), attributeBroker, functionBroker,
                variables);
    }

    @Override
    public WhenStep constructTestCase() {
        return StepBuilder.newBuilderAtWhenStep(policySourceRetriever.get(), attributeBroker, functionBroker,
                variables);
    }

}
