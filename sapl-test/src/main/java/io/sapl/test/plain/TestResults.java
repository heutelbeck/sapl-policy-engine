/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.test.plain;

import java.util.Map;

/**
 * Results of executing a batch of SAPL tests.
 *
 * @param total total number of test scenarios executed
 * @param passed number of scenarios that passed
 * @param failed number of scenarios that failed
 * @param failures map of scenario identifiers to their failure exceptions
 */
public record TestResults(int total, int passed, int failed, Map<String, Throwable> failures) {

    /**
     * Returns true if all tests passed.
     *
     * @return true if no tests failed
     */
    public boolean allPassed() {
        return failed == 0;
    }

    /**
     * Creates a successful result for the given number of tests.
     *
     * @param total the total number of tests that passed
     * @return a TestResults with all tests passing
     */
    public static TestResults success(int total) {
        return new TestResults(total, total, 0, Map.of());
    }

    /**
     * Creates a result with failures.
     *
     * @param total total number of tests
     * @param failures map of failed test identifiers to their exceptions
     * @return a TestResults reflecting the failures
     */
    public static TestResults withFailures(int total, Map<String, Throwable> failures) {
        return new TestResults(total, total - failures.size(), failures.size(), failures);
    }

}
