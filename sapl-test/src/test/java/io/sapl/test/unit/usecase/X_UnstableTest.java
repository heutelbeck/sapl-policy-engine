package io.sapl.test.unit.usecase;

import static io.sapl.hamcrest.Matchers.anyDecision;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.functions.TemporalFunctionLibrary;
import io.sapl.interpreter.InitializationException;
import io.sapl.test.SaplTestFixture;
import io.sapl.test.unit.SaplUnitTestFixture;

class X_UnstableTest {

    private SaplTestFixture fixture;

    @BeforeEach
    void setUp() throws InitializationException {
        fixture = new SaplUnitTestFixture("policyStreaming")
                // .registerPIP(null)
                .registerFunctionLibrary(new TemporalFunctionLibrary());
    }

    @Test
    void test_streamingPolicyWithVirtualTime() throws InitializationException {
        System.out.println("Start unstable test");
        fixture.registerPIP(new io.sapl.pip.ClockPolicyInformationPoint())
            .constructTestCaseWithMocks()
            .withVirtualTime()
            .when(AuthorizationSubscription.of("ROLE_DOCTOR", "read", "heartBeatData"))
            .expectNext(anyDecision())
            .thenAwait(Duration.ofSeconds(2))
            .expectNext(anyDecision())
            .thenAwait(Duration.ofSeconds(2))
            .expectNext(anyDecision())
            .thenAwait(Duration.ofSeconds(2))
            .expectNext(anyDecision())
            .thenAwait(Duration.ofSeconds(2))
            .expectNext(anyDecision())
            .thenAwait(Duration.ofSeconds(2))
            .expectNext(anyDecision())
            .verify();
    }

    @Test
    void test_streamingPolicyWithVirtualTime_5() throws InitializationException {
        System.out.println("\ntest_streamingPolicyWithVirtualTime_5");
        fixture.registerPIP(new io.sapl.pip.ClockPolicyInformationPoint())
            .constructTestCaseWithMocks()
            .withVirtualTime()
            .when(AuthorizationSubscription.of("ROLE_DOCTOR", "read", "heartBeatData"))
            .expectNext(anyDecision())
            .thenAwait(Duration.ofSeconds(2))
            .expectNext(anyDecision())
            .thenAwait(Duration.ofSeconds(2))
            .expectNext(anyDecision())
            .thenAwait(Duration.ofSeconds(2))
            .expectNext(anyDecision())
            .thenAwait(Duration.ofSeconds(2))
            .expectNext(anyDecision())
            .verify();
    }

    @Test
    void test_3s() throws InitializationException {
        System.out.println("\ntest_3s");
        fixture.registerPIP(new io.sapl.pip.ClockPolicyInformationPoint())
            .constructTestCaseWithMocks()
            .withVirtualTime()
            .when(AuthorizationSubscription.of("ROLE_DOCTOR", "read", "heartBeatData"))
            .expectNext(anyDecision())
            .thenAwait(Duration.ofSeconds(3))
            .expectNext(anyDecision())
            .thenAwait(Duration.ofSeconds(3))
            .expectNext(anyDecision())
            .thenAwait(Duration.ofSeconds(3))
            .expectNext(anyDecision())
            .thenAwait(Duration.ofSeconds(3))
            .expectNext(anyDecision())
            .thenAwait(Duration.ofSeconds(3))
            .expectNext(anyDecision())
            .verify();
    }

    @Test
    void test_expectSubscription() throws InitializationException {
        System.out.println("\ntest_expectSubscription");
        fixture.registerPIP(new io.sapl.pip.ClockPolicyInformationPoint())
            .constructTestCaseWithMocks2()
            .withVirtualTime()
            .when(AuthorizationSubscription.of("ROLE_DOCTOR", "read", "heartBeatData"))
            .expectNext(anyDecision())
            .thenAwait(Duration.ofSeconds(2))
            .expectNext(anyDecision())
            .thenAwait(Duration.ofSeconds(2))
            .expectNext(anyDecision())
            .thenAwait(Duration.ofSeconds(2))
            .expectNext(anyDecision())
            .thenAwait(Duration.ofSeconds(2))
            .expectNext(anyDecision())
            .thenAwait(Duration.ofSeconds(2))
            .expectNext(anyDecision())
            .verify();
    }

    @Test
    @Disabled
    void test_withoutTimeout() throws InitializationException {
        System.out.println("\ntest_withoutTimeout");
        fixture.registerPIP(new io.sapl.pip.ClockPolicyInformationPoint())
            .constructTestCaseWithMocks3()
            .withVirtualTime()
            .when(AuthorizationSubscription.of("ROLE_DOCTOR", "read", "heartBeatData"))
            .expectNext(anyDecision())
            .thenAwait(Duration.ofSeconds(2))
            .expectNext(anyDecision())
            .thenAwait(Duration.ofSeconds(2))
            .expectNext(anyDecision())
            .thenAwait(Duration.ofSeconds(2))
            .expectNext(anyDecision())
            .thenAwait(Duration.ofSeconds(2))
            .expectNext(anyDecision())
            .thenAwait(Duration.ofSeconds(2))
            .expectNext(anyDecision())
            .verify();
    }

    @Test
    void test_withoutConcat() throws InitializationException {
        System.out.println("\ntest_withoutConcat");
        fixture.registerPIP(new ClockPolicyInformationPoint2())
            .constructTestCaseWithMocks()
            .withVirtualTime()
            .when(AuthorizationSubscription.of("ROLE_DOCTOR", "read", "heartBeatData"))
            .thenAwait(Duration.ofSeconds(2))
            .expectNext(anyDecision())
            .thenAwait(Duration.ofSeconds(2))
            .expectNext(anyDecision())
            .thenAwait(Duration.ofSeconds(2))
            .expectNext(anyDecision())
            .thenAwait(Duration.ofSeconds(2))
            .expectNext(anyDecision())
            .thenAwait(Duration.ofSeconds(2))
            .expectNext(anyDecision())
            .verify();
    }

    @Test
    void test_withoutConcat_4() throws InitializationException {
        System.out.println("\ntest_withoutConcat_4");
        fixture.registerPIP(new ClockPolicyInformationPoint2())
                .constructTestCaseWithMocks()
                .withVirtualTime()
                .when(AuthorizationSubscription.of("ROLE_DOCTOR", "read", "heartBeatData"))
                .thenAwait(Duration.ofSeconds(2))
                .expectNext(anyDecision())
                .thenAwait(Duration.ofSeconds(2))
                .expectNext(anyDecision())
                .thenAwait(Duration.ofSeconds(2))
                .expectNext(anyDecision())
                .thenAwait(Duration.ofSeconds(2))
                .expectNext(anyDecision())
                .verify();
    }

    @Test
    void test_simpleClockPIP() throws InitializationException {
        System.out.println("\ntest_simpleClockPIP");
        fixture.registerPIP(new ClockPolicyInformationPoint3())
            .constructTestCaseWithMocks()
            .withVirtualTime()
            .when(AuthorizationSubscription.of("ROLE_DOCTOR", "read", "heartBeatData"))
            .thenAwait(Duration.ofSeconds(2))
            .expectNext(anyDecision())
            .thenAwait(Duration.ofSeconds(2))
            .expectNext(anyDecision())
            .thenAwait(Duration.ofSeconds(2))
            .expectNext(anyDecision())
            .thenAwait(Duration.ofSeconds(2))
            .expectNext(anyDecision())
            .thenAwait(Duration.ofSeconds(2))
            .expectNext(anyDecision())
            .verify();
    }

    @Test
    void test_simpleClockPIP_4() throws InitializationException {
        System.out.println("\ntest_simpleClockPIP_4");
        fixture.registerPIP(new ClockPolicyInformationPoint3())
                .constructTestCaseWithMocks()
                .withVirtualTime()
                .when(AuthorizationSubscription.of("ROLE_DOCTOR", "read", "heartBeatData"))
                .thenAwait(Duration.ofSeconds(2))
                .expectNext(anyDecision())
                .thenAwait(Duration.ofSeconds(2))
                .expectNext(anyDecision())
                .thenAwait(Duration.ofSeconds(2))
                .expectNext(anyDecision())
                .thenAwait(Duration.ofSeconds(2))
                .expectNext(anyDecision())
                .verify();
    }

    @Test
    void test_realtime_6() throws InitializationException {
        System.out.println("\ntest_realtime_6");
        fixture.registerPIP(new ClockPolicyInformationPoint())
            .constructTestCase()
            .when(AuthorizationSubscription.of("ROLE_DOCTOR", "read", "heartBeatData")).expectNext((dec) -> {
                System.out.println("Got Dec: " + Instant.now());
                return true;
            }).expectNext((dec) -> {
                System.out.println("Got Dec: " + Instant.now());
                return true;
            }).expectNext((dec) -> {
                System.out.println("Got Dec: " + Instant.now());
                return true;
            }).expectNext((dec) -> {
                System.out.println("Got Dec: " + Instant.now());
                return true;
            }).expectNext((dec) -> {
                System.out.println("Got Dec: " + Instant.now());
                return true;
            }).expectNext((dec) -> {
                System.out.println("Got Dec: " + Instant.now());
                return true;
            }).verify();
    }

    @Test
    void test_realtime_withoutVerifyTimeout() throws InitializationException {
        System.out.println("\ntest_realtime_withoutVerifyTimeout");
        fixture.registerPIP(new ClockPolicyInformationPoint())
            .constructTestCase3()
            .when(AuthorizationSubscription.of("ROLE_DOCTOR", "read", "heartBeatData")).expectNext((dec) -> {
                System.out.println("Got Dec: " + Instant.now());
                return true;
            }).expectNext((dec) -> {
                System.out.println("Got Dec: " + Instant.now());
                return true;
            }).expectNext((dec) -> {
                System.out.println("Got Dec: " + Instant.now());
                return true;
            }).expectNext((dec) -> {
                System.out.println("Got Dec: " + Instant.now());
                return true;
            }).expectNext((dec) -> {
                System.out.println("Got Dec: " + Instant.now());
                return true;
            }).expectNext((dec) -> {
                System.out.println("Got Dec: " + Instant.now());
                return true;
            }).verify();
    }

}
