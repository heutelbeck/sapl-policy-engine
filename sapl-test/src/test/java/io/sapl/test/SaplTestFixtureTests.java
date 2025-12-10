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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.pdp.configuration.bundle.BundleSecurityPolicy;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static io.sapl.test.Matchers.any;
import static io.sapl.test.Matchers.args;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for SaplTestFixture API validation and configuration methods.
 * <p>
 * Note: These tests focus on API validation rather than actual policy
 * evaluation
 * due to dependencies required for the SAPL compiler in test contexts.
 */
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

    @Test
    void whenSettingCombiningAlgorithmInSingleMode_thenThrowsException() {
        var fixture = SaplTestFixture.createSingleTest();

        assertThatThrownBy(() -> fixture.withCombiningAlgorithm(CombiningAlgorithm.DENY_OVERRIDES))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not allowed in single test mode");
    }

    @Test
    void whenUsingConfigurationFromDirectoryInSingleMode_thenThrowsException() {
        var fixture = SaplTestFixture.createSingleTest();

        assertThatThrownBy(() -> fixture.withConfigurationFromDirectory("/some/path"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not allowed in single test mode");
    }

    @Test
    void whenUsingConfigFileInSingleMode_thenThrowsException() {
        var fixture = SaplTestFixture.createSingleTest();

        assertThatThrownBy(() -> fixture.withConfigFile("/some/pdp.json")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not allowed in single test mode");
    }

    @Test
    void whenUsingConfigFileFromResourceInSingleMode_thenThrowsException() {
        var fixture = SaplTestFixture.createSingleTest();

        assertThatThrownBy(() -> fixture.withConfigFileFromResource("/some/pdp.json"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not allowed in single test mode");
    }

    @Test
    void whenUsingBundleInSingleMode_thenThrowsException() {
        var fixture = SaplTestFixture.createSingleTest();

        assertThatThrownBy(() -> fixture.withBundle("/some/bundle.saplbundle"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not allowed in single test mode");
    }

    @Test
    void whenUsingBundleFromResourceInSingleMode_thenThrowsException() {
        var fixture = SaplTestFixture.createSingleTest();

        assertThatThrownBy(() -> fixture.withBundleFromResource("/some/bundle.saplbundle"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not allowed in single test mode");
    }

    @Test
    void whenUsingVerifiedBundleInSingleMode_thenThrowsException() {
        var fixture        = SaplTestFixture.createSingleTest();
        var securityPolicy = BundleSecurityPolicy.builder().disableSignatureVerification().acceptUnsignedBundleRisks()
                .build();

        assertThatThrownBy(() -> fixture.withVerifiedBundle("/some/bundle.saplbundle", securityPolicy))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not allowed in single test mode");
    }

    @Test
    void whenAddingMultiplePoliciesInIntegrationMode_thenSucceeds() {
        var fixture = SaplTestFixture.createIntegrationTest().withPolicy(PERMIT_ALL_POLICY).withPolicy(DENY_ALL_POLICY)
                .withCombiningAlgorithm(CombiningAlgorithm.DENY_OVERRIDES);

        assertThat(fixture).isNotNull();
    }

    @Test
    void whenAddingDuplicateVariable_thenThrowsException() {
        var fixture = SaplTestFixture.createSingleTest().givenVariable("testVar", Value.of("value1"));

        assertThatThrownBy(() -> fixture.givenVariable("testVar", Value.of("value2")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("already registered");
    }

    @Test
    void whenAddingMultipleVariables_thenSucceeds() {
        var fixture = SaplTestFixture.createSingleTest().givenVariable("var1", Value.of("value1"))
                .givenVariable("var2", Value.of("value2")).withPolicy(PERMIT_ALL_POLICY);

        assertThat(fixture).isNotNull();
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
                .withCombiningAlgorithm(CombiningAlgorithm.PERMIT_OVERRIDES).givenVariable("maxRetries", Value.of(5))
                .givenFunction("time.dayOfWeek", args(), Value.of("MONDAY"))
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
    void whenUsingConfigurationFromResourcesInSingleMode_thenThrowsException() {
        var fixture = SaplTestFixture.createSingleTest();

        assertThatThrownBy(() -> fixture.withConfigurationFromResources("/policies"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not allowed in single test mode");
    }

    @Test
    void whenSettingObjectMapper_thenReturnsFixtureForChaining() {
        var objectMapper = new ObjectMapper();

        var fixture = SaplTestFixture.createSingleTest().withObjectMapper(objectMapper).withPolicy(PERMIT_ALL_POLICY);

        assertThat(fixture).isNotNull();
    }

    @Test
    void whenSettingClock_thenReturnsFixtureForChaining() {
        var fixedClock = Clock.fixed(Instant.parse("2025-01-06T10:00:00Z"), ZoneOffset.UTC);

        var fixture = SaplTestFixture.createSingleTest().withClock(fixedClock).withPolicy(PERMIT_ALL_POLICY);

        assertThat(fixture).isNotNull();
    }

    @Test
    void whenSettingObjectMapperAndClock_thenBothCanBeChained() {
        var objectMapper = new ObjectMapper();
        var fixedClock   = Clock.fixed(Instant.parse("2025-01-06T10:00:00Z"), ZoneOffset.UTC);

        var fixture = SaplTestFixture.createSingleTest().withObjectMapper(objectMapper).withClock(fixedClock)
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
        var objectMapper = new ObjectMapper();
        var fixedClock   = Clock.fixed(Instant.parse("2025-01-06T10:00:00Z"), ZoneOffset.UTC);

        var fixture = SaplTestFixture.createSingleTest().withObjectMapper(objectMapper).withClock(fixedClock)
                .withFunctionLibrary(Object.class).withFunctionLibraryInstance(new Object())
                .withPolicyInformationPoint(new Object()).withPolicy(PERMIT_ALL_POLICY);

        assertThat(fixture).isNotNull();
    }
}
