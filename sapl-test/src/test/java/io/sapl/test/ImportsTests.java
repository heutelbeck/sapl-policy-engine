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
package io.sapl.test;

import static io.sapl.test.Imports.anyTimes;
import static io.sapl.test.Imports.never;
import static io.sapl.test.Imports.times;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.test.mocking.MockCall;
import io.sapl.test.verification.MockRunInformation;

/**
 * Times Verification Convenience Test Cases for additional test cases see
 * TimesCalledVerificationTest
 */
class ImportsTests {

    @Test
    void test_times_specificNumber() {
        final var mockRunInformation = new MockRunInformation("test.test");
        mockRunInformation.saveCall(new MockCall(Val.of(1)));
        mockRunInformation.saveCall(new MockCall(Val.of(1)));
        final var verification = times(2);

        var isAssertionErrorThrown = false;
        try {
            verification.verify(mockRunInformation);
        } catch (AssertionError e) {
            isAssertionErrorThrown = true;
        }

        assertThat(isAssertionErrorThrown).isFalse();
    }

    @Test
    void test_times_specificNumber_failure() {
        final var mockRunInformation = new MockRunInformation("test.test");
        mockRunInformation.saveCall(new MockCall(Val.of(1)));
        final var verification = times(2);

        var isAssertionErrorThrown = false;
        try {
            verification.verify(mockRunInformation);
        } catch (AssertionError e) {
            isAssertionErrorThrown = true;
        }

        assertThat(isAssertionErrorThrown).isTrue();
    }

    @Test
    void test_times_never() {
        final var mockRunInformation = new MockRunInformation("test.test");
        final var verification       = never();

        var isAssertionErrorThrown = false;
        try {
            verification.verify(mockRunInformation);
        } catch (AssertionError e) {
            isAssertionErrorThrown = true;
        }

        assertThat(isAssertionErrorThrown).isFalse();
    }

    @Test
    void test_times_never_failure() {
        final var mockRunInformation = new MockRunInformation("test.test");
        mockRunInformation.saveCall(new MockCall(Val.of(1)));
        final var verification = never();

        var isAssertionErrorThrown = false;
        try {
            verification.verify(mockRunInformation);
        } catch (AssertionError e) {
            isAssertionErrorThrown = true;
        }

        assertThat(isAssertionErrorThrown).isTrue();
    }

    @Test
    void test_times_anyTimes_0() {
        final var mockRunInformation = new MockRunInformation("test.test");
        final var verification       = anyTimes();

        var isAssertionErrorThrown = false;
        try {
            verification.verify(mockRunInformation);
        } catch (AssertionError e) {
            isAssertionErrorThrown = true;
        }

        assertThat(isAssertionErrorThrown).isFalse();
    }

    @Test
    void test_times_anyTimes_1() {
        final var mockRunInformation = new MockRunInformation("test.test");
        mockRunInformation.saveCall(new MockCall(Val.of(1)));
        final var verification = anyTimes();

        var isAssertionErrorThrown = false;
        try {
            verification.verify(mockRunInformation);
        } catch (AssertionError e) {
            isAssertionErrorThrown = true;
        }

        assertThat(isAssertionErrorThrown).isFalse();
    }

    @Test
    void test_times_anyTimes_N() {
        final var mockRunInformation = new MockRunInformation("test.test");
        mockRunInformation.saveCall(new MockCall(Val.of(1)));
        mockRunInformation.saveCall(new MockCall(Val.of(1)));
        mockRunInformation.saveCall(new MockCall(Val.of(1)));
        mockRunInformation.saveCall(new MockCall(Val.of(1)));
        final var verification = anyTimes();

        var isAssertionErrorThrown = false;
        try {
            verification.verify(mockRunInformation);
        } catch (AssertionError e) {
            isAssertionErrorThrown = true;
        }

        assertThat(isAssertionErrorThrown).isFalse();
    }

}
