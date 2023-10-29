/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.test.unit.usecase;

import static io.sapl.test.Imports.times;

import java.time.Duration;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.functions.TemporalFunctionLibrary;
import io.sapl.interpreter.InitializationException;
import io.sapl.test.SaplTestException;
import io.sapl.test.SaplTestFixture;
import io.sapl.test.unit.SaplUnitTestFixture;

class E_PolicyStreamingTests {

    private SaplTestFixture fixture;

    @BeforeEach
    void setUp() throws InitializationException {
        fixture = new SaplUnitTestFixture("policyStreaming").registerFunctionLibrary(new TemporalFunctionLibrary());
    }

    @Test
    void test_streamingPolicy() {
        var timestamp0 = Val.of("2021-02-08T16:16:01.000Z");
        var timestamp1 = Val.of("2021-02-08T16:16:02.000Z");
        var timestamp2 = Val.of("2021-02-08T16:16:03.000Z");
        var timestamp3 = Val.of("2021-02-08T16:16:04.000Z");
        var timestamp4 = Val.of("2021-02-08T16:16:05.000Z");
        var timestamp5 = Val.of("2021-02-08T16:16:06.000Z");

        fixture.constructTestCaseWithMocks()
                .givenAttribute("time.now", timestamp0, timestamp1, timestamp2, timestamp3, timestamp4, timestamp5)
                .when(AuthorizationSubscription.of("ROLE_DOCTOR", "read", "heartBeatData")).expectNextNotApplicable()
                .expectNextNotApplicable().expectNextNotApplicable().expectNextNotApplicable().expectNextPermit()
                .expectNextPermit().verify();
    }

    @Test
    void test_streamingPolicy_TimingAttributeMock() {
        var timestamp0 = Val.of("2021-02-08T16:16:01.000Z");
        var timestamp1 = Val.of("2021-02-08T16:16:02.000Z");
        var timestamp2 = Val.of("2021-02-08T16:16:03.000Z");
        var timestamp3 = Val.of("2021-02-08T16:16:04.000Z");
        var timestamp4 = Val.of("2021-02-08T16:16:05.000Z");
        var timestamp5 = Val.of("2021-02-08T16:16:06.000Z");

        fixture.constructTestCaseWithMocks().withVirtualTime()
                .givenAttribute("time.now", Duration.ofSeconds(10), timestamp0, timestamp1, timestamp2, timestamp3,
                        timestamp4, timestamp5)
                .when(AuthorizationSubscription.of("ROLE_DOCTOR", "read", "heartBeatData"))
                .thenAwait(Duration.ofSeconds(10)).expectNextNotApplicable().thenAwait(Duration.ofSeconds(10))
                .expectNextNotApplicable().thenAwait(Duration.ofSeconds(10)).expectNextNotApplicable()
                .thenAwait(Duration.ofSeconds(10)).expectNextNotApplicable().thenAwait(Duration.ofSeconds(10))
                .expectNextPermit().thenAwait(Duration.ofSeconds(10)).expectNextPermit()
                .thenAwait(Duration.ofSeconds(10)).verify();
    }

    @Test
    void test_streamingPolicy_TimingAttributeMock_WithoutVirtualTime() {
        var timestamp0       = Val.of("2021-02-08T16:16:01.000Z");
        var timestamp1       = Val.of("2021-02-08T16:16:02.000Z");
        var timestamp2       = Val.of("2021-02-08T16:16:03.000Z");
        var timestamp3       = Val.of("2021-02-08T16:16:04.000Z");
        var timestamp4       = Val.of("2021-02-08T16:16:05.000Z");
        var timestamp5       = Val.of("2021-02-08T16:16:06.000Z");
        var tenSeconds       = Duration.ofSeconds(10L);
        var fixtureWithMocks = fixture.constructTestCaseWithMocks();
        Assertions.assertThatExceptionOfType(SaplTestException.class)
                .isThrownBy(() -> fixtureWithMocks.givenAttribute("time.now", tenSeconds, timestamp0, timestamp1,
                        timestamp2, timestamp3, timestamp4, timestamp5));

    }

    @Test
    void test_streamingPolicyWithSimpleMockedFunction_ConsecutiveCalls() {
        var timestamp0 = Val.of("2021-02-08T16:16:01.000Z");
        var timestamp1 = Val.of("2021-02-08T16:16:02.000Z");

        fixture.constructTestCaseWithMocks().givenAttribute("time.now", timestamp0, timestamp1)
                .givenFunctionOnce("time.secondOf", Val.of(4)).givenFunctionOnce("time.secondOf", Val.of(5))
                .when(AuthorizationSubscription.of("ROLE_DOCTOR", "read", "heartBeatData")).expectNextNotApplicable()
                .expectNextPermit().verify(); // two times mock of function -> verify two times called
    }

    @Test
    void test_streamingPolicyWithSimpleMockedFunction_ArrayOfReturnValues() {
        fixture.constructTestCaseWithMocks()
                .givenAttribute("time.now", Val.of("value"), Val.of("doesn't"), Val.of("matter"))
                .givenFunctionOnce("time.secondOf", Val.of(3), Val.of(4), Val.of(5))
                .when(AuthorizationSubscription.of("ROLE_DOCTOR", "read", "heartBeatData")).expectNextNotApplicable()
                // three times mock of function -> verify two times called
                .expectNextNotApplicable().expectNextPermit().verify();
    }

    @Test
    void test_streamingPolicyWithSimpleMockedFunction_AlwaysReturn_VerifyTimesCalled() {
        fixture.constructTestCaseWithMocks()
                .givenAttribute("time.now", Val.of("value"), Val.of("doesn't"), Val.of("matter"))
                .givenFunction("time.secondOf", Val.of(5), times(3))
                .when(AuthorizationSubscription.of("ROLE_DOCTOR", "read", "heartBeatData")).expectNextPermit(3)
                .verify(); // three times mock of function -> three times called
    }

}
