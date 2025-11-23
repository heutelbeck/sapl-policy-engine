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

class PermitUnlessDenyTests {

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void permitUnlessDenyTests(String description, String policySet, Decision expectedDecision) {
        assertDecision(policySet, expectedDecision);
    }

    private static Stream<Arguments> permitUnlessDenyTests() {
        return Stream.of(arguments("No policies match returns PERMIT", """
                set "test" permit-unless-deny
                policy "never matches" permit subject == "non-matching"
                """, Decision.PERMIT),

                arguments("Single permit returns PERMIT", """
                        set "test" permit-unless-deny
                        policy "permit policy" permit
                        """, Decision.PERMIT),

                arguments("Single deny returns DENY", """
                        set "test" permit-unless-deny
                        policy "deny policy" deny
                        """, Decision.DENY),

                arguments("Any deny returns DENY", """
                        set "test" permit-unless-deny
                        policy "permit policy" permit
                        policy "deny policy" deny
                        """, Decision.DENY),

                arguments("Transformation uncertainty returns DENY", """
                        set "test" permit-unless-deny
                        policy "permit with transformation 1" permit transform "resource1"
                        policy "permit with transformation 2" permit transform "resource2"
                        """, Decision.DENY),

                arguments("Only permit returns PERMIT", """
                        set "test" permit-unless-deny
                        policy "permit policy 1" permit
                        policy "permit policy 2" permit
                        """, Decision.PERMIT),

                arguments("Only not applicable returns PERMIT", """
                        set "test" permit-unless-deny
                        policy "not applicable 1" permit subject == "non-matching1"
                        policy "not applicable 2" deny subject == "non-matching2"
                        """, Decision.PERMIT));
    }
}
