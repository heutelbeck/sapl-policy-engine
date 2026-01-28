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
package io.sapl.test;

import tools.jackson.databind.json.JsonMapper;
import io.sapl.api.model.Value;
import static io.sapl.api.pdp.CombiningAlgorithm.DefaultDecision.ABSTAIN;
import static io.sapl.api.pdp.CombiningAlgorithm.ErrorHandling.PROPAGATE;
import static io.sapl.api.pdp.CombiningAlgorithm.VotingMode.PRIORITY_DENY;
import static io.sapl.api.pdp.CombiningAlgorithm.VotingMode.PRIORITY_PERMIT;

import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.pdp.configuration.bundle.BundleSecurityPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static io.sapl.test.Matchers.any;
import static io.sapl.test.Matchers.args;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Tests for SaplTestFixture API validation and configuration methods.
 * <p>
 * Note: These tests focus on API validation rather than actual policy
 * evaluation
 * due to dependencies required for the SAPL compiler in test contexts.
 */
@DisplayName("SaplTestFixture tests")
class SaplTestFixtureTests {

    private static final String PERMIT_ALL_POLICY = "policy \"permit-all\" permit";
    private static final String DENY_ALL_POLICY   = "policy \"deny-all\" deny";

    @Test
    void whenCreateSingleTest_thenFixtureIsNotNull() {
        var fixture = SaplTestFixture.createSingleTest();
        assertThat(fixture).isNotNull();
    }

    @Test
    void whenCreateIntegrationTest_thenFixtureIsNotNull() {
        var fixture = SaplTestFixture.createIntegrationTest();
        assertThat(fixture).isNotNull();
    }

    @Test
    void whenCreateSingleTest_thenBrokersAreInitialized() {
        var fixture = SaplTestFixture.createSingleTest();

        assertThat(fixture.getMockingFunctionBroker()).isNotNull();
        assertThat(fixture.getMockingAttributeBroker()).isNotNull();
    }

    @Test
    void whenAddingSecondPolicyInSingleMode_thenThrowsException() {
        var fixture = SaplTestFixture.createSingleTest().withPolicy(PERMIT_ALL_POLICY);

        assertThatThrownBy(() -> fixture.withPolicy(DENY_ALL_POLICY)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Single test mode only allows one policy");
    }

    @ParameterizedTest(name = "{0} not allowed in single test mode")
    @MethodSource("singleModeDisallowedOperations")
    void whenUsingIntegrationOnlyMethodInSingleMode_thenThrowsException(String methodName,
            Consumer<SaplTestFixture> operation) {
        var fixture = SaplTestFixture.createSingleTest();

        assertThatThrownBy(() -> operation.accept(fixture)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not allowed in single test mode");
    }

    static Stream<Arguments> singleModeDisallowedOperations() {
        var securityPolicy = BundleSecurityPolicy.builder().disableSignatureVerification().acceptUnsignedBundleRisks()
                .build();
        return Stream.of(
                arguments("withCombiningAlgorithm",
                        (Consumer<SaplTestFixture>) f -> f
                                .withCombiningAlgorithm(new CombiningAlgorithm(PRIORITY_DENY, ABSTAIN, PROPAGATE))),
                arguments("withConfigurationFromDirectory",
                        (Consumer<SaplTestFixture>) f -> f.withConfigurationFromDirectory("/some/path")),
                arguments("withConfigFile", (Consumer<SaplTestFixture>) f -> f.withConfigFile("/some/pdp.json")),
                arguments("withConfigFileFromResource",
                        (Consumer<SaplTestFixture>) f -> f.withConfigFileFromResource("/some/pdp.json")),
                arguments("withBundle", (Consumer<SaplTestFixture>) f -> f.withBundle("/some/bundle.saplbundle")),
                arguments("withBundleFromResource",
                        (Consumer<SaplTestFixture>) f -> f.withBundleFromResource("/some/bundle.saplbundle")),
                arguments("withVerifiedBundle",
                        (Consumer<SaplTestFixture>) f -> f.withVerifiedBundle("/some/bundle.saplbundle",
                                securityPolicy)),
                arguments("withConfigurationFromResources",
                        (Consumer<SaplTestFixture>) f -> f.withConfigurationFromResources("/policies")));
    }

    @Test
    void whenAddingMultiplePoliciesInIntegrationMode_thenSucceeds() {
        var fixture = SaplTestFixture.createIntegrationTest().withPolicy(PERMIT_ALL_POLICY).withPolicy(DENY_ALL_POLICY)
                .withCombiningAlgorithm(new CombiningAlgorithm(PRIORITY_DENY, ABSTAIN, PROPAGATE));

        assertThat(fixture).isNotNull();
    }

    @Test
    void whenAddingDuplicateVariable_thenOverrideSucceeds() {
        var fixture = SaplTestFixture.createSingleTest().givenVariable("testVar", Value.of("value1"))
                .givenVariable("testVar", Value.of("value2")).withPolicy(PERMIT_ALL_POLICY);

        assertThat(fixture).isNotNull();
    }

    @Test
    void whenAddingMultipleVariables_thenSucceeds() {
        var fixture = SaplTestFixture.createSingleTest().givenVariable("var1", Value.of("value1"))
                .givenVariable("var2", Value.of("value2")).withPolicy(PERMIT_ALL_POLICY);

        assertThat(fixture).isNotNull();
    }

    @ParameterizedTest(name = "reserved variable name ''{0}'' throws exception")
    @ValueSource(strings = { "subject", "action", "resource", "environment" })
    void whenAddingReservedVariableName_thenThrowsException(String reservedName) {
        var fixture = SaplTestFixture.createSingleTest();
        var value   = Value.of("test");

        assertThatThrownBy(() -> fixture.givenVariable(reservedName, value))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("reserved");
    }

    @Test
    void whenMockingFunction_thenMockIsRegistered() {
        var fixture = SaplTestFixture.createSingleTest().givenFunction("time.dayOfWeek", args(), Value.of("MONDAY"));

        assertThat(fixture.getMockingFunctionBroker().hasMock("time.dayOfWeek")).isTrue();
    }

    @Test
    void whenMockingFunctionWithSequence_thenMockIsRegistered() {
        var fixture = SaplTestFixture.createSingleTest().givenFunction("counter.next", args(), Value.of(1), Value.of(2),
                Value.of(3));

        assertThat(fixture.getMockingFunctionBroker().hasMock("counter.next")).isTrue();
    }

    @Test
    void whenMockingEnvironmentAttributeWithInitialValue_thenMockIsRegistered() {
        var fixture = SaplTestFixture.createSingleTest().givenEnvironmentAttribute("timeMock", "time.now", args(),
                Value.of("2025-01-06T10:00:00Z"));

        assertThat(fixture.getMockingAttributeBroker().hasMock("timeMock")).isTrue();
    }

    @Test
    void whenMockingEnvironmentAttributeWithoutInitialValue_thenMockIsRegistered() {
        var fixture = SaplTestFixture.createSingleTest().givenEnvironmentAttribute("timeMock", "time.now", args());

        assertThat(fixture.getMockingAttributeBroker().hasMock("timeMock")).isTrue();
    }

    @Test
    void whenMockingAttributeWithInitialValue_thenMockIsRegistered() {
        var fixture = SaplTestFixture.createSingleTest().givenAttribute("userRoleMock", "user.role", any(), args(),
                Value.of("admin"));

        assertThat(fixture.getMockingAttributeBroker().hasMock("userRoleMock")).isTrue();
    }

    @Test
    void whenMockingAttributeWithoutInitialValue_thenMockIsRegistered() {
        var fixture = SaplTestFixture.createSingleTest().givenAttribute("userRoleMock", "user.role", any(), args());

        assertThat(fixture.getMockingAttributeBroker().hasMock("userRoleMock")).isTrue();
    }

    @Test
    void whenChainingConfigurationMethods_thenAllAreApplied() {
        var fixture = SaplTestFixture.createIntegrationTest().withPolicy(PERMIT_ALL_POLICY).withPolicy(DENY_ALL_POLICY)
                .withCombiningAlgorithm(new CombiningAlgorithm(PRIORITY_PERMIT, ABSTAIN, PROPAGATE))
                .givenVariable("maxRetries", Value.of(5)).givenFunction("time.dayOfWeek", args(), Value.of("MONDAY"))
                .givenEnvironmentAttribute("currentTemp", "env.temperature", args(), Value.of(25));

        assertThat(fixture.getMockingFunctionBroker().hasMock("time.dayOfWeek")).isTrue();
        assertThat(fixture.getMockingAttributeBroker().hasMock("currentTemp")).isTrue();
    }

    @Test
    void whenReadingPolicyFromNonExistentFile_thenThrowsException() {
        var fixture = SaplTestFixture.createSingleTest();

        assertThatThrownBy(() -> fixture.withPolicyFromFile("/non/existent/path.sapl"))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("Failed to read policy file");
    }

    @Test
    void whenReadingPolicyFromNonExistentResource_thenThrowsException() {
        var fixture = SaplTestFixture.createSingleTest();

        assertThatThrownBy(() -> fixture.withPolicyFromResource("/non/existent/resource.sapl"))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("Resource not found");
    }

    @Test
    void whenReadingBundleFromNonExistentResource_thenThrowsException() {
        var fixture = SaplTestFixture.createIntegrationTest();

        assertThatThrownBy(() -> fixture.withBundleFromResource("/non/existent/bundle.saplbundle"))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("Bundle resource not found");
    }

    @Test
    void whenSettingJsonMapper_thenReturnsFixtureForChaining() {
        var jsonMapper = JsonMapper.builder().build();

        var fixture = SaplTestFixture.createSingleTest().withJsonMapper(jsonMapper).withPolicy(PERMIT_ALL_POLICY);

        assertThat(fixture).isNotNull();
    }

    @Test
    void whenSettingClock_thenReturnsFixtureForChaining() {
        var fixedClock = Clock.fixed(Instant.parse("2025-01-06T10:00:00Z"), ZoneOffset.UTC);

        var fixture = SaplTestFixture.createSingleTest().withClock(fixedClock).withPolicy(PERMIT_ALL_POLICY);

        assertThat(fixture).isNotNull();
    }

    @Test
    void whenSettingJsonMapperAndClock_thenBothCanBeChained() {
        var jsonMapper = JsonMapper.builder().build();
        var fixedClock = Clock.fixed(Instant.parse("2025-01-06T10:00:00Z"), ZoneOffset.UTC);

        var fixture = SaplTestFixture.createSingleTest().withJsonMapper(jsonMapper).withClock(fixedClock)
                .withPolicy(PERMIT_ALL_POLICY);

        assertThat(fixture).isNotNull();
    }

    @Test
    void whenAddingFunctionLibrary_thenReturnsFixtureForChaining() {
        var fixture = SaplTestFixture.createSingleTest().withFunctionLibrary(Object.class)
                .withPolicy(PERMIT_ALL_POLICY);

        assertThat(fixture).isNotNull();
    }

    @Test
    void whenAddingFunctionLibraryInstance_thenReturnsFixtureForChaining() {
        var fixture = SaplTestFixture.createSingleTest().withFunctionLibraryInstance(new Object())
                .withPolicy(PERMIT_ALL_POLICY);

        assertThat(fixture).isNotNull();
    }

    @Test
    void whenAddingPolicyInformationPoint_thenReturnsFixtureForChaining() {
        var fixture = SaplTestFixture.createSingleTest().withPolicyInformationPoint(new Object())
                .withPolicy(PERMIT_ALL_POLICY);

        assertThat(fixture).isNotNull();
    }

    @Test
    void whenChainingAllConfigurationOptions_thenSucceeds() {
        var jsonMapper = JsonMapper.builder().build();
        var fixedClock = Clock.fixed(Instant.parse("2025-01-06T10:00:00Z"), ZoneOffset.UTC);

        var fixture = SaplTestFixture.createSingleTest().withJsonMapper(jsonMapper).withClock(fixedClock)
                .withFunctionLibrary(Object.class).withFunctionLibraryInstance(new Object())
                .withPolicyInformationPoint(new Object()).withPolicy(PERMIT_ALL_POLICY);

        assertThat(fixture).isNotNull();
    }

    @Test
    void whenSettingTestIdentifier_thenReturnsFixtureForChaining() {
        var fixture = SaplTestFixture.createSingleTest().withTestIdentifier("my-test-id").withPolicy(PERMIT_ALL_POLICY);

        assertThat(fixture).isNotNull();
    }

    @Test
    void whenSettingCoverageOutput_thenReturnsFixtureForChaining() {
        var fixture = SaplTestFixture.createSingleTest().withCoverageOutput(Path.of("target/coverage"))
                .withPolicy(PERMIT_ALL_POLICY);

        assertThat(fixture).isNotNull();
    }

    @Test
    void whenDisablingCoverageFileWrite_thenReturnsFixtureForChaining() {
        var fixture = SaplTestFixture.createSingleTest().withCoverageFileWriteDisabled().withPolicy(PERMIT_ALL_POLICY);
        assertThat(fixture).isNotNull();
    }

    @Test
    void whenAddingDefaultFunctionLibraries_thenReturnsFixtureForChaining() {
        var fixture = SaplTestFixture.createSingleTest().withDefaultFunctionLibraries().withPolicy(PERMIT_ALL_POLICY);

        assertThat(fixture).isNotNull();
    }

    @Test
    void whenSettingCustomBrokers_thenReturnsFixtureForChaining() {
        var fixture = SaplTestFixture.createSingleTest().withFunctionBroker(new MockingFunctionBroker())
                .withAttributeBroker(new MockingAttributeBroker()).withPolicy(PERMIT_ALL_POLICY);

        assertThat(fixture).isNotNull();
    }
}
