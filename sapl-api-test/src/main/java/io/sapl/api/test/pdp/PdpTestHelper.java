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
package io.sapl.api.test.pdp;

import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.api.pdp.configuration.PdpData;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
        return new AuthorizationSubscription(Value.of(subject), Value.of(action), Value.of(resource),
                Value.EMPTY_OBJECT, Value.EMPTY_OBJECT);
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
        return new AuthorizationSubscription(subject, action, resource, environment, Value.EMPTY_OBJECT);
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
        return new PDPConfiguration("default", "test-security-" + System.currentTimeMillis(), algorithm,
                List.of(policies), new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT));
    }

    /**
     * Creates a PDPConfiguration with DEFAULT algorithm.
     *
     * @param policies
     * the SAPL policy documents
     *
     * @return the PDP configuration
     */
    public static PDPConfiguration configuration(String... policies) {
        return configuration(CombiningAlgorithm.DEFAULT, policies);
    }

    /**
     * Creates a SAPL bundle (zip file) containing the given policy content. The
     * bundle includes a pdp.json and an unsigned manifest carrying a generated
     * configurationId.
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
     * Creates a SAPL bundle containing multiple policies. The bundle includes a
     * pdp.json and an unsigned manifest carrying a generated configurationId.
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
     * Creates a SAPL bundle with a specific configurationId. The configurationId
     * is recorded in an unsigned {@code .sapl-manifest.json} entry; pdp.json
     * carries only the combining algorithm.
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
        val pdpJson = """
                {"algorithm":{"votingMode":"PRIORITY_DENY","defaultDecision":"DENY","errorHandling":"PROPAGATE"}}
                """;
        val files   = new LinkedHashMap<String, String>();
        files.put("pdp.json", pdpJson);
        for (int i = 0; i < policies.length; i++) {
            files.put("policy" + i + ".sapl", policies[i]);
        }

        val baos = new ByteArrayOutputStream();
        try (val zos = new ZipOutputStream(baos)) {
            for (val entry : files.entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                zos.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
            zos.putNextEntry(new ZipEntry(".sapl-manifest.json"));
            zos.write(manifestJson(configurationId, files).getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    private static String manifestJson(String configurationId, Map<String, String> files) {
        val fileEntries = new StringBuilder();
        var first       = true;
        for (val entry : files.entrySet()) {
            if (!first) {
                fileEntries.append(',');
            }
            fileEntries.append("\"%s\":\"%s\"".formatted(entry.getKey(), sha256Hash(entry.getValue())));
            first = false;
        }
        return """
                {"version":"test","hashAlgorithm":"SHA-256","created":"%s","configurationId":"%s",\
                "attribution":"sapl-api-test","files":{%s}}""".formatted(Instant.now(), configurationId, fileEntries);
    }

    private static String sha256Hash(String content) {
        try {
            val digest = MessageDigest.getInstance("SHA-256");
            return "sha256:"
                    + Base64.getEncoder().encodeToString(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available.", e);
        }
    }
}
