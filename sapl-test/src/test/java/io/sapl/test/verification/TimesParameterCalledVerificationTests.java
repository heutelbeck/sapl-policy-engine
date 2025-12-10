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
import io.sapl.test.SaplTestException;
import io.sapl.test.mocking.MockCall;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.CoreMatchers.any;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.number.OrderingComparison.comparesEqualTo;

class TimesParameterCalledVerificationTests {

    @Test
    void test() {
        final var runInfo = new MockRunInformation("foo");
        runInfo.saveCall(new MockCall(Value.of("bar"), Value.of(1)));
        runInfo.saveCall(new MockCall(Value.of("xxx"), Value.of(2)));
        runInfo.saveCall(new MockCall(Value.of("yyy"), Value.of(3)));
        runInfo.saveCall(new MockCall(Value.of("xxx"), Value.of(2)));

        final var matcher            = comparesEqualTo(2);
        final var expectedParameters = new LinkedList<Matcher<Value>>();
        expectedParameters.add(is(Value.of("xxx")));
        expectedParameters.add(is(Value.of(2)));
        final var verification = new TimesParameterCalledVerification(new TimesCalledVerification(matcher),
                expectedParameters);

        assertThatNoException().isThrownBy(() -> verification.verify(runInfo));

        assertThat(runInfo.getCalls()).hasSize(4);
        assertThat(runInfo.getCalls().get(0).isUsed()).isFalse();
        assertThat(runInfo.getCalls().get(1).isUsed()).isTrue();
        assertThat(runInfo.getCalls().get(2).isUsed()).isFalse();
        assertThat(runInfo.getCalls().get(3).isUsed()).isTrue();
    }

    @Test
    void test_assertionError() {
        final var runInfo = new MockRunInformation("foo");
        runInfo.saveCall(new MockCall(Value.of("bar"), Value.of(1)));
        runInfo.saveCall(new MockCall(Value.of("xxx"), Value.of(2)));
        runInfo.saveCall(new MockCall(Value.of("yyy"), Value.of(3)));
        runInfo.saveCall(new MockCall(Value.of("xxx"), Value.of(3)));

        final var matcher            = comparesEqualTo(2);
        final var expectedParameters = new LinkedList<Matcher<Value>>();
        expectedParameters.add(is(Value.of("xxx")));
        expectedParameters.add(is(Value.of(2)));
        final var verification = new TimesParameterCalledVerification(new TimesCalledVerification(matcher),
                expectedParameters);

        assertThatExceptionOfType(AssertionError.class).isThrownBy(() -> verification.verify(runInfo));

        assertThat(runInfo.getCalls()).hasSize(4);
        assertThat(runInfo.getCalls().get(0).isUsed()).isFalse();
        assertThat(runInfo.getCalls().get(1).isUsed()).isTrue();
        assertThat(runInfo.getCalls().get(2).isUsed()).isFalse();
        assertThat(runInfo.getCalls().get(3).isUsed()).isFalse();
    }

    @Test
    void test_assertionError_tooOftenCalled() {
        final var runInfo = new MockRunInformation("foo");
        runInfo.saveCall(new MockCall(Value.of("bar"), Value.of(1)));
        runInfo.saveCall(new MockCall(Value.of("xxx"), Value.of(2)));
        runInfo.saveCall(new MockCall(Value.of("xxx"), Value.of(3)));
        runInfo.saveCall(new MockCall(Value.of("xxx"), Value.of(3)));

        final var matcher = comparesEqualTo(2);

        final var expectedParameters = new LinkedList<Matcher<Value>>();
        expectedParameters.add(is(Value.of("xxx")));
        expectedParameters.add(any(Value.class));
        final var verification = new TimesParameterCalledVerification(new TimesCalledVerification(matcher),
                expectedParameters);

        assertThatExceptionOfType(AssertionError.class).isThrownBy(() -> verification.verify(runInfo));

        assertThat(runInfo.getCalls()).hasSize(4);
        assertThat(runInfo.getCalls().get(0).isUsed()).isFalse();
        assertThat(runInfo.getCalls().get(1).isUsed()).isTrue();
        assertThat(runInfo.getCalls().get(2).isUsed()).isTrue();
        assertThat(runInfo.getCalls().get(3).isUsed()).isTrue();
    }

    @Test
    void test_MultipleParameterTimesVerifications_WithAnyMatcher_OrderingMatters() {
        final var runInfo = new MockRunInformation("foo");
        runInfo.saveCall(new MockCall(Value.of("bar"), Value.of(1)));
        runInfo.saveCall(new MockCall(Value.of("xxx"), Value.of(2)));
        runInfo.saveCall(new MockCall(Value.of("yyy"), Value.of(3)));
        runInfo.saveCall(new MockCall(Value.of("xxx"), Value.of(3)));

        final var matcher = comparesEqualTo(1);

        final var expectedParameters = new LinkedList<Matcher<Value>>();
        expectedParameters.add(is(Value.of("xxx")));
        expectedParameters.add(is(Value.of(2)));
        final var verification = new TimesParameterCalledVerification(new TimesCalledVerification(matcher),
                expectedParameters);

        assertThatNoException().isThrownBy(() -> verification.verify(runInfo));

        assertThat(runInfo.getCalls()).hasSize(4);
        assertThat(runInfo.getCalls().get(0).isUsed()).isFalse();
        assertThat(runInfo.getCalls().get(1).isUsed()).isTrue();
        assertThat(runInfo.getCalls().get(2).isUsed()).isFalse();
        assertThat(runInfo.getCalls().get(3).isUsed()).isFalse();

        final var matcher2            = comparesEqualTo(1);
        final var expectedParameters2 = new LinkedList<Matcher<Value>>();
        expectedParameters2.add(is(Value.of("xxx")));
        expectedParameters2.add(any(Value.class));
        final var verification2 = new TimesParameterCalledVerification(new TimesCalledVerification(matcher2),
                expectedParameters2);

        assertThatNoException().isThrownBy(() -> verification2.verify(runInfo));

        assertThat(runInfo.getCalls()).hasSize(4);
        assertThat(runInfo.getCalls().get(0).isUsed()).isFalse();
        assertThat(runInfo.getCalls().get(1).isUsed()).isTrue();
        assertThat(runInfo.getCalls().get(2).isUsed()).isFalse();
        assertThat(runInfo.getCalls().get(3).isUsed()).isTrue();
    }

    private static Stream<Arguments> provideTestCases() {
        // @formatter:off
		return Stream.of(
				// test_assertionError_verificationMessage
			    Arguments.of("VerificationMessage", "VerificationMessage"),
				// test_assertionError_VerificationMessage_Empty
			    Arguments.of("", "Error verifying the expected number of calls to the mock"),
				// test_assertionError_VerificationMessage_Null
			    Arguments.of(null, "Error verifying the expected number of calls to the mock")
			);
		// @formatter:on
    }

    @ParameterizedTest
    @MethodSource("provideTestCases")
    void verifyMessage(String given, String expected) {
        final var runInfo = new MockRunInformation("foo");
        runInfo.saveCall(new MockCall(Value.of("bar"), Value.of(1)));
        runInfo.saveCall(new MockCall(Value.of("xxx"), Value.of(2)));
        runInfo.saveCall(new MockCall(Value.of("yyy"), Value.of(3)));
        runInfo.saveCall(new MockCall(Value.of("xxx"), Value.of(3)));

        final var matcher = comparesEqualTo(2);

        final var expectedParameters = new LinkedList<Matcher<Value>>();
        expectedParameters.add(is(Value.of("xxx")));
        expectedParameters.add(is(Value.of(2)));
        final var verification = new TimesParameterCalledVerification(new TimesCalledVerification(matcher),
                expectedParameters);

        assertThatThrownBy(() -> verification.verify(runInfo, given)).isInstanceOf(AssertionError.class)
                .hasMessageContaining(expected);

        assertThat(runInfo.getCalls()).hasSize(4);
        assertThat(runInfo.getCalls().get(0).isUsed()).isFalse();
        assertThat(runInfo.getCalls().get(1).isUsed()).isTrue();
        assertThat(runInfo.getCalls().get(2).isUsed()).isFalse();
        assertThat(runInfo.getCalls().get(3).isUsed()).isFalse();
    }

    @Test
    void test_Exception_CountOfExpectedParameterNotEqualsFunctionCallParametersCount() {
        final var runInfo = new MockRunInformation("foo");
        runInfo.saveCall(new MockCall(Value.of("bar"), Value.of(1)));
        runInfo.saveCall(new MockCall(Value.of("xxx"), Value.of(2)));
        runInfo.saveCall(new MockCall(Value.of("yyy"), Value.of(3)));
        runInfo.saveCall(new MockCall(Value.of("xxx"), Value.of(3)));

        final var matcher            = comparesEqualTo(2);
        final var expectedParameters = new LinkedList<Matcher<Value>>();
        expectedParameters.add(is(Value.of("xxx")));
        final var verification = new TimesParameterCalledVerification(new TimesCalledVerification(matcher),
                expectedParameters);

        assertThatExceptionOfType(SaplTestException.class).isThrownBy(() -> verification.verify(runInfo));
    }

}
