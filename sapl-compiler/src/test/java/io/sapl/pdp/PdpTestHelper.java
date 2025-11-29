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
package io.sapl.pdp;

import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PDPConfiguration;
import io.sapl.api.pdp.PolicyDecisionPoint;
import lombok.experimental.UtilityClass;
import lombok.val;
import reactor.test.StepVerifier;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test utility for PDP tests providing common subscription creation, decision
 * verification, and configuration helpers.
 */
@UtilityClass
public class PdpTestHelper {

    /**
     * Creates an authorization subscription with string values for subject, action,
     * and resource.
     *
     * @param subject
     * the subject string
     * @param action
     * the action string
     * @param resource
     * the resource string
     *
     * @return the authorization subscription
     */
    public static AuthorizationSubscription subscription(String subject, String action, String resource) {
        return new AuthorizationSubscription(Value.of(subject), Value.of(action), Value.of(resource), Value.UNDEFINED);
    }

    /**
     * Creates an authorization subscription with Value objects.
     *
     * @param subject
     * the subject Value
     * @param action
     * the action Value
     * @param resource
     * the resource Value
     * @param environment
     * the environment Value
     *
     * @return the authorization subscription
     */
    public static AuthorizationSubscription subscription(Value subject, Value action, Value resource,
            Value environment) {
        return new AuthorizationSubscription(subject, action, resource, environment);
    }

    /**
     * Asserts that the PDP returns the expected decision for the given
     * subscription.
     *
     * @param pdp
     * the policy decision point
     * @param subscription
     * the authorization subscription
     * @param expectedDecision
     * the expected decision
     */
    public static void assertDecision(PolicyDecisionPoint pdp, AuthorizationSubscription subscription,
            Decision expectedDecision) {
        StepVerifier.create(pdp.decide(subscription).take(1))
                .assertNext(decision -> assertThat(decision.decision()).isEqualTo(expectedDecision)).verifyComplete();
    }

    /**
     * Creates a PDPConfiguration with the given policies and algorithm.
     *
     * @param algorithm
     * the combining algorithm
     * @param policies
     * the SAPL policy documents
     *
     * @return the PDP configuration
     */
    public static PDPConfiguration configuration(CombiningAlgorithm algorithm, String... policies) {
        return new PDPConfiguration("default", "test-config-" + System.currentTimeMillis(), algorithm,
                List.of(policies), Map.of());
    }

    /**
     * Creates a PDPConfiguration with DENY_OVERRIDES algorithm.
     *
     * @param policies
     * the SAPL policy documents
     *
     * @return the PDP configuration
     */
    public static PDPConfiguration configuration(String... policies) {
        return configuration(CombiningAlgorithm.DENY_OVERRIDES, policies);
    }

    /**
     * Creates a SAPL bundle (zip file) containing the given policy content.
     * The bundle includes a pdp.json with a generated configurationId.
     *
     * @param policyContent
     * the policy document text
     *
     * @return the bundle bytes
     *
     * @throws IOException
     * if bundle creation fails
     */
    public static byte[] createBundle(String policyContent) throws IOException {
        return createBundle(new String[] { policyContent });
    }

    /**
     * Creates a SAPL bundle containing multiple policies.
     * The bundle includes a pdp.json with a generated configurationId.
     *
     * @param policies
     * the policy documents
     *
     * @return the bundle bytes
     *
     * @throws IOException
     * if bundle creation fails
     */
    public static byte[] createBundle(String... policies) throws IOException {
        return createBundleWithConfigurationId("test-bundle-" + System.currentTimeMillis(), policies);
    }

    /**
     * Creates a SAPL bundle with a specific configurationId.
     *
     * @param configurationId
     * the configuration identifier
     * @param policies
     * the policy documents
     *
     * @return the bundle bytes
     *
     * @throws IOException
     * if bundle creation fails
     */
    public static byte[] createBundleWithConfigurationId(String configurationId, String... policies)
            throws IOException {
        val baos = new ByteArrayOutputStream();
        try (val zos = new ZipOutputStream(baos)) {
            // Add pdp.json with configurationId (required for bundles)
            val pdpJson = "{\"configurationId\":\"%s\",\"algorithm\":\"DENY_OVERRIDES\"}".formatted(configurationId);
            zos.putNextEntry(new ZipEntry("pdp.json"));
            zos.write(pdpJson.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // Add policy files
            for (int i = 0; i < policies.length; i++) {
                zos.putNextEntry(new ZipEntry("policy" + i + ".sapl"));
                zos.write(policies[i].getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }
}
