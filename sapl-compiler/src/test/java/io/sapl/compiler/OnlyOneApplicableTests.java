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
package io.sapl.compiler;

import io.sapl.api.pdp.Decision;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static io.sapl.util.CombiningAlgorithmTestUtil.assertDecision;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class OnlyOneApplicableTests {

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void onlyOneApplicableTests(String description, String policySet, Decision expectedDecision) {
        assertDecision(policySet, expectedDecision);
    }

    private static Stream<Arguments> onlyOneApplicableTests() {
        return Stream.of(arguments("No policies match returns NOT_APPLICABLE", """
                set "test" only-one-applicable
                policy "never matches 1" permit subject == "non-matching1"
                policy "never matches 2" deny subject == "non-matching2"
                """, Decision.NOT_APPLICABLE),

                arguments("Single permit returns PERMIT", """
                        set "test" only-one-applicable
                        policy "permit policy" permit
                        policy "never matches" deny subject == "non-matching"
                        """, Decision.PERMIT),

                arguments("Single deny returns DENY", """
                        set "test" only-one-applicable
                        policy "deny policy" deny
                        policy "never matches" permit subject == "non-matching"
                        """, Decision.DENY),

                arguments("Multiple applicable returns INDETERMINATE", """
                        set "test" only-one-applicable
                        policy "permit policy" permit
                        policy "deny policy" deny
                        """, Decision.INDETERMINATE),

                arguments("Multiple permits returns INDETERMINATE", """
                        set "test" only-one-applicable
                        policy "permit policy 1" permit
                        policy "permit policy 2" permit
                        """, Decision.INDETERMINATE),

                arguments("Multiple denies returns INDETERMINATE", """
                        set "test" only-one-applicable
                        policy "deny policy 1" deny
                        policy "deny policy 2" deny
                        """, Decision.INDETERMINATE),

                arguments("Single applicable permit with not applicable returns PERMIT", """
                        set "test" only-one-applicable
                        policy "permit policy" permit
                        policy "not applicable 1" deny subject == "non-matching1"
                        policy "not applicable 2" permit subject == "non-matching2"
                        """, Decision.PERMIT));
    }
}
