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

import io.sapl.api.model.Value;
import static io.sapl.api.pdp.CombiningAlgorithm.DefaultDecision.ABSTAIN;
import static io.sapl.api.pdp.CombiningAlgorithm.ErrorHandling.PROPAGATE;
import static io.sapl.api.pdp.CombiningAlgorithm.VotingMode.PRIORITY_DENY;
import static io.sapl.api.pdp.CombiningAlgorithm.VotingMode.PRIORITY_PERMIT;
import static io.sapl.api.pdp.CombiningAlgorithm.VotingMode.UNIQUE;

import io.sapl.api.pdp.CombiningAlgorithm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TestConfiguration tests")
class TestConfigurationTests {

    @Test
    @DisplayName("builder creates configuration with defaults")
    void whenBuildingWithDefaults_thenConfigurationHasDefaults() {
        var config = TestConfiguration.builder().build();

        assertThat(config.saplDocuments()).isEmpty();
        assertThat(config.saplTestDocuments()).isEmpty();
        assertThat(config.defaultAlgorithm()).isEqualTo(new CombiningAlgorithm(PRIORITY_DENY, ABSTAIN, PROPAGATE));
        assertThat(config.pdpVariables()).isEmpty();
        assertThat(config.functionLibraries()).isEmpty();
        assertThat(config.policyInformationPoints()).isEmpty();
        assertThat(config.failFast()).isFalse();
        assertThat(config.verificationTimeout()).isEqualTo(TestConfiguration.DEFAULT_VERIFICATION_TIMEOUT);
    }

    @Test
    @DisplayName("builder with single SAPL document")
    void whenAddingSingleSaplDocument_thenConfigurationContainsIt() {
        var document = new SaplDocument("test", "test", "policy \"test\" permit", null);

        var config = TestConfiguration.builder().withSaplDocument(document).build();

        assertThat(config.saplDocuments()).containsExactly(document);
    }

    @Test
    @DisplayName("builder with multiple SAPL documents via list")
    void whenAddingMultipleSaplDocuments_thenConfigurationContainsAll() {
        var doc1      = new SaplDocument("test1", "test1", "policy \"test1\" permit", null);
        var doc2      = new SaplDocument("test2", "test2", "policy \"test2\" deny", null);
        var documents = List.of(doc1, doc2);

        var config = TestConfiguration.builder().withSaplDocuments(documents).build();

        assertThat(config.saplDocuments()).containsExactly(doc1, doc2);
    }

    @Test
    @DisplayName("builder with single test document")
    void whenAddingSingleTestDocument_thenConfigurationContainsIt() {
        var testDoc = new SaplTestDocument("test", "test", "test content");

        var config = TestConfiguration.builder().withSaplTestDocument(testDoc).build();

        assertThat(config.saplTestDocuments()).containsExactly(testDoc);
    }

    @Test
    @DisplayName("builder with multiple test documents via list")
    void whenAddingMultipleTestDocuments_thenConfigurationContainsAll() {
        var testDoc1 = new SaplTestDocument("test1", "test1", "content1");
        var testDoc2 = new SaplTestDocument("test2", "test2", "content2");
        var testDocs = List.of(testDoc1, testDoc2);

        var config = TestConfiguration.builder().withSaplTestDocuments(testDocs).build();

        assertThat(config.saplTestDocuments()).containsExactly(testDoc1, testDoc2);
    }

    @Test
    @DisplayName("builder with custom combining algorithm")
    void whenSettingCombiningAlgorithm_thenConfigurationUsesIt() {
        var config = TestConfiguration.builder()
                .withDefaultAlgorithm(new CombiningAlgorithm(PRIORITY_PERMIT, ABSTAIN, PROPAGATE)).build();

        assertThat(config.defaultAlgorithm()).isEqualTo(new CombiningAlgorithm(PRIORITY_PERMIT, ABSTAIN, PROPAGATE));
    }

    @Test
    @DisplayName("builder with single variable")
    void whenAddingSingleVariable_thenConfigurationContainsIt() {
        var config = TestConfiguration.builder().withVariable("maxRetries", Value.of(5)).build();

        assertThat(config.pdpVariables()).containsEntry("maxRetries", Value.of(5));
    }

    @Test
    @DisplayName("builder with multiple variables via map")
    void whenAddingMultipleVariables_thenConfigurationContainsAll() {
        var variables = new HashMap<String, Value>();
        variables.put("var1", Value.of("value1"));
        variables.put("var2", Value.of(42));

        var config = TestConfiguration.builder().withVariables(variables).build();

        assertThat(config.pdpVariables()).containsAllEntriesOf(variables);
    }

    @Test
    @DisplayName("builder with single function library")
    void whenAddingSingleFunctionLibrary_thenConfigurationContainsIt() {
        var config = TestConfiguration.builder().withFunctionLibrary(Object.class).build();

        assertThat(config.functionLibraries()).containsExactly(Object.class);
    }

    @Test
    @DisplayName("builder with multiple function libraries via list")
    void whenAddingMultipleFunctionLibraries_thenConfigurationContainsAll() {
        var libraries = List.<Class<?>>of(Object.class, String.class);

        var config = TestConfiguration.builder().withFunctionLibraries(libraries).build();

        assertThat(config.functionLibraries()).containsExactly(Object.class, String.class);
    }

    @Test
    @DisplayName("builder with single PIP")
    void whenAddingSinglePip_thenConfigurationContainsIt() {
        var pip = new Object();

        var config = TestConfiguration.builder().withPolicyInformationPoint(pip).build();

        assertThat(config.policyInformationPoints()).containsExactly(pip);
    }

    @Test
    @DisplayName("builder with multiple PIPs via list")
    void whenAddingMultiplePips_thenConfigurationContainsAll() {
        var pip1 = new Object();
        var pip2 = new Object();
        var pips = List.of(pip1, pip2);

        var config = TestConfiguration.builder().withPolicyInformationPoints(pips).build();

        assertThat(config.policyInformationPoints()).containsExactly(pip1, pip2);
    }

    @Test
    @DisplayName("builder with fail fast enabled")
    void whenEnablingFailFast_thenConfigurationUsesIt() {
        var config = TestConfiguration.builder().withFailFast(true).build();

        assertThat(config.failFast()).isTrue();
    }

    @Test
    @DisplayName("builder with custom verification timeout")
    void whenSettingVerificationTimeout_thenConfigurationUsesIt() {
        var timeout = Duration.ofSeconds(10);

        var config = TestConfiguration.builder().withVerificationTimeout(timeout).build();

        assertThat(config.verificationTimeout()).isEqualTo(timeout);
    }

    @Test
    @DisplayName("builder chains all configuration options")
    void whenChainingAllOptions_thenConfigurationContainsAll() {
        var document = new SaplDocument("policy", "policy", "policy \"test\" permit", null);
        var testDoc  = new SaplTestDocument("test", "test", "test content");
        var pip      = new Object();
        var timeout  = Duration.ofSeconds(5);

        var config = TestConfiguration.builder().withSaplDocument(document).withSaplTestDocument(testDoc)
                .withDefaultAlgorithm(new CombiningAlgorithm(UNIQUE, ABSTAIN, PROPAGATE))
                .withVariable("key", Value.of("value")).withFunctionLibrary(Object.class)
                .withPolicyInformationPoint(pip).withFailFast(true).withVerificationTimeout(timeout).build();

        assertThat(config.saplDocuments()).containsExactly(document);
        assertThat(config.saplTestDocuments()).containsExactly(testDoc);
        assertThat(config.defaultAlgorithm()).isEqualTo(new CombiningAlgorithm(UNIQUE, ABSTAIN, PROPAGATE));
        assertThat(config.pdpVariables()).containsEntry("key", Value.of("value"));
        assertThat(config.functionLibraries()).containsExactly(Object.class);
        assertThat(config.policyInformationPoints()).containsExactly(pip);
        assertThat(config.failFast()).isTrue();
        assertThat(config.verificationTimeout()).isEqualTo(timeout);
    }

    @Test
    @DisplayName("default verification timeout constant is 1 second")
    void whenAccessingDefaultTimeout_thenIsOneSecond() {
        assertThat(TestConfiguration.DEFAULT_VERIFICATION_TIMEOUT).isEqualTo(Duration.ofSeconds(1));
    }
}
