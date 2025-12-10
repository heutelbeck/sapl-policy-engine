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
package io.sapl.test.verification;

import io.sapl.api.model.Value;
import io.sapl.test.mocking.MockCall;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.number.OrderingComparison.comparesEqualTo;
import static org.hamcrest.number.OrderingComparison.greaterThanOrEqualTo;

class TimesCalledVerificationTests {

    @Test
    void test_is() {
        final var runInfo = new MockRunInformation("foo");
        runInfo.saveCall(new MockCall(Value.of("bar")));
        final var matcher      = is(1);
        final var verification = new TimesCalledVerification(matcher);

        assertThatNoException().isThrownBy(() -> verification.verify(runInfo));

    }

    @Test
    void test_comparesEqualTo() {
        final var runInfo = new MockRunInformation("foo");
        runInfo.saveCall(new MockCall(Value.of("bar")));
        final var matcher      = comparesEqualTo(1);
        final var verification = new TimesCalledVerification(matcher);

        assertThatNoException().isThrownBy(() -> verification.verify(runInfo));
    }

    @Test
    void test_comparesEqualTo_multipleCalls() {
        final var runInfo = new MockRunInformation("foo");
        runInfo.saveCall(new MockCall(Value.of("bar")));
        runInfo.saveCall(new MockCall(Value.of("xxx")));
        final var matcher      = comparesEqualTo(2);
        final var verification = new TimesCalledVerification(matcher);

        assertThatNoException().isThrownBy(() -> verification.verify(runInfo));
    }

    @Test
    void test_comparesEqualTo_assertionError() {
        MockRunInformation runInfo      = new MockRunInformation("foo");
        final var          matcher      = comparesEqualTo(1);
        final var          verification = new TimesCalledVerification(matcher);

        assertThatThrownBy(() -> verification.verify(runInfo)).isInstanceOf(AssertionError.class);
    }

    @Test
    void test_greaterThanOrEqualTo() {
        final var runInfo = new MockRunInformation("foo");
        runInfo.saveCall(new MockCall(Value.of("bar")));
        final var matcher      = greaterThanOrEqualTo(1);
        final var verification = new TimesCalledVerification(matcher);

        assertThatNoException().isThrownBy(() -> verification.verify(runInfo));
    }

    @Test
    void test_greaterThanOrEqualTo_assertionError() {
        MockRunInformation runInfo      = new MockRunInformation("foo");
        final var          matcher      = greaterThanOrEqualTo(1);
        final var          verification = new TimesCalledVerification(matcher);

        assertThatThrownBy(() -> verification.verify(runInfo)).isInstanceOf(AssertionError.class);
    }

    private static Stream<Arguments> provideTestCases() {
        // @formatter:off
		return Stream.of(
				// test_verificationMessageEmpty
			    Arguments.of("", "Error verifying the expected number"),

				// test_verificationMessageNull
			    Arguments.of(null, "Error verifying the expected number"),

				// test_verificationMessageNotEmpty
			    Arguments.of("VerificationMessage", "VerificationMessage")
			);
		// @formatter:on
    }

    @ParameterizedTest
    @MethodSource("provideTestCases")
    void checkVerificationMessage(String given, String expected) {
        final var runInfo = new MockRunInformation("foo");
        runInfo.saveCall(new MockCall(Value.of("bar")));
        final var matcher      = is(2);
        final var verification = new TimesCalledVerification(matcher);

        assertThatThrownBy(() -> verification.verify(runInfo, given)).isInstanceOf(AssertionError.class)
                .hasMessageContaining(expected);
    }
}
