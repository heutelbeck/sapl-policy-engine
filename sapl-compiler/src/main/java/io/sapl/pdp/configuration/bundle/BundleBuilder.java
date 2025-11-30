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
package io.sapl.pdp.configuration.bundle;

import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.pdp.configuration.BundlePDPConfigurationSource;
import io.sapl.pdp.configuration.PDPConfigurationException;
import lombok.val;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builder for creating SAPL bundle files (.saplbundle).
 * <p>
 * A SAPL bundle is a ZIP archive containing policy documents and an optional
 * pdp.json configuration file. This builder
 * provides a fluent API to construct bundles programmatically, serving as the
 * inverse of {@link BundleParser}.
 * </p>
 * <h2>Bundle Structure</h2>
 *
 * <pre>
 * my-policies.saplbundle (ZIP archive):
 * ├── pdp.json           (optional configuration)
 * ├── access-control.sapl
 * ├── audit.sapl
 * └── logging.sapl
 * </pre>
 *
 * <h2>Usage Examples</h2>
 *
 * <pre>{@code
 * // Create a bundle with explicit pdp.json content
 * byte[] bundle = BundleBuilder.create().withPdpJson("""
 *         { "algorithm": "DENY_OVERRIDES" }
 *         """).withPolicy("access-control.sapl", """
 *         policy "admin-access"
 *         permit subject.role == "admin"
 *         """).withPolicy("audit.sapl", """
 *         policy "audit-logging"
 *         permit true
 *         advice { "action": "log" }
 *         """).build();
 *
 * // Create a bundle using combining algorithm enum
 * byte[] bundle = BundleBuilder.create().withCombiningAlgorithm(CombiningAlgorithm.PERMIT_OVERRIDES)
 *         .withPolicy("permissive.sapl", "policy \"allow-all\" permit true").build();
 *
 * // Write bundle directly to a file
 * BundleBuilder.create().withCombiningAlgorithm(CombiningAlgorithm.DENY_UNLESS_PERMIT)
 *         .withPolicy("strict.sapl", "policy \"deny-default\" deny true")
 *         .writeTo(Path.of("/policies/production.saplbundle"));
 *
 * // Stream bundle to an HTTP response
 * BundleBuilder.create().withCombiningAlgorithm(CombiningAlgorithm.ONLY_ONE_APPLICABLE)
 *         .withPolicy("api-access.sapl", "policy \"api\" permit resource.type == \"api\"")
 *         .writeTo(response.getOutputStream());
 *
 * // Create a signed bundle with Ed25519
 * PrivateKey signingKey = loadPrivateKey();
 * byte[] signedBundle = BundleBuilder.create().withCombiningAlgorithm(CombiningAlgorithm.DENY_OVERRIDES)
 *         .withPolicy("secure.sapl", "policy \"secure\" permit subject.verified == true")
 *         .signWith(signingKey, "production-key-2024").build();
 * }</pre>
 *
 * <h2>Deployment Scenarios</h2>
 * <p>
 * Bundle files are useful for:
 * </p>
 * <ul>
 * <li><b>Policy distribution:</b> Package policies for deployment to remote
 * PDPs.</li>
 * <li><b>Policy export:</b> Export policies from a policy administration
 * point.</li>
 * <li><b>Testing:</b> Create test fixtures for bundle parsing tests.</li>
 * <li><b>Backup:</b> Archive policy configurations for disaster recovery.</li>
 * </ul>
 *
 * @see BundleParser
 * @see BundlePDPConfigurationSource
 * @see BundleSigner
 */
public final class BundleBuilder {

    private static final String PDP_JSON       = "pdp.json";
    private static final String SAPL_EXTENSION = ".sapl";

    private String                    pdpJson;
    private final Map<String, String> policies = new LinkedHashMap<>();

    private PrivateKey signingKey;
    private String     signingKeyId;
    private Instant    signatureExpires;

    private BundleBuilder() {
        // Use create() factory method
    }

    /**
     * Creates a new bundle builder instance.
     *
     * @return a new builder
     */
    public static BundleBuilder create() {
        return new BundleBuilder();
    }

    /**
     * Sets the raw pdp.json content for the bundle.
     * <p>
     * Use this method when you need full control over the pdp.json content,
     * including custom variables or other
     * configuration options.
     * </p>
     *
     * @param pdpJsonContent
     * the JSON content for pdp.json
     *
     * @return this builder for method chaining
     */
    public BundleBuilder withPdpJson(String pdpJsonContent) {
        this.pdpJson = pdpJsonContent;
        return this;
    }

    /**
     * Sets the combining algorithm for the bundle with an auto-generated
     * configurationId.
     * <p>
     * This is a convenience method that generates a pdp.json with the algorithm and
     * an auto-generated configurationId.
     * If you need to include custom configurationId, variables, or other settings,
     * use {@link #withPdpJson(String)}
     * instead.
     * </p>
     *
     * @param algorithm
     * the combining algorithm
     *
     * @return this builder for method chaining
     */
    public BundleBuilder withCombiningAlgorithm(CombiningAlgorithm algorithm) {
        return withCombiningAlgorithm(algorithm, "bundle-" + System.currentTimeMillis());
    }

    /**
     * Sets the combining algorithm and configurationId for the bundle.
     *
     * @param algorithm
     * the combining algorithm
     * @param configurationId
     * the configuration identifier
     *
     * @return this builder for method chaining
     */
    public BundleBuilder withCombiningAlgorithm(CombiningAlgorithm algorithm, String configurationId) {
        this.pdpJson = """
                { "algorithm": "%s", "configurationId": "%s" }
                """.formatted(algorithm.name(), configurationId);
        return this;
    }

    /**
     * Sets the combining algorithm and variables for the bundle with an
     * auto-generated configurationId.
     * <p>
     * This method generates a pdp.json with algorithm, configurationId, and
     * variables settings. The variables map is
     * serialized as a JSON object.
     * </p>
     *
     * @param algorithm
     * the combining algorithm
     * @param variables
     * the policy variables as a map
     *
     * @return this builder for method chaining
     */
    public BundleBuilder withConfiguration(CombiningAlgorithm algorithm, Map<String, String> variables) {
        return withConfiguration(algorithm, "bundle-" + System.currentTimeMillis(), variables);
    }

    /**
     * Sets the combining algorithm, configurationId, and variables for the bundle.
     *
     * @param algorithm
     * the combining algorithm
     * @param configurationId
     * the configuration identifier
     * @param variables
     * the policy variables as a map
     *
     * @return this builder for method chaining
     */
    public BundleBuilder withConfiguration(CombiningAlgorithm algorithm, String configurationId,
            Map<String, String> variables) {
        val variablesJson = new StringBuilder();
        variablesJson.append("{ ");

        var first = true;
        for (val entry : variables.entrySet()) {
            if (!first) {
                variablesJson.append(", ");
            }
            variablesJson.append("\"%s\": %s".formatted(entry.getKey(), entry.getValue()));
            first = false;
        }

        variablesJson.append(" }");

        this.pdpJson = """
                { "algorithm": "%s", "configurationId": "%s", "variables": %s }
                """.formatted(algorithm.name(), configurationId, variablesJson);
        return this;
    }

    /**
     * Adds a policy document to the bundle.
     * <p>
     * The filename should end with ".sapl". If it does not, the extension will be
     * appended automatically.
     * </p>
     *
     * @param filename
     * the policy filename (e.g., "access-control.sapl")
     * @param content
     * the SAPL policy content
     *
     * @return this builder for method chaining
     *
     * @throws IllegalArgumentException
     * if filename is null or empty
     */
    public BundleBuilder withPolicy(String filename, String content) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Policy filename must not be null or empty.");
        }

        val normalizedFilename = filename.endsWith(SAPL_EXTENSION) ? filename : filename + SAPL_EXTENSION;

        policies.put(normalizedFilename, content != null ? content : "");
        return this;
    }

    /**
     * Adds multiple policy documents to the bundle.
     *
     * @param policyMap
     * a map of filename to policy content
     *
     * @return this builder for method chaining
     */
    public BundleBuilder withPolicies(Map<String, String> policyMap) {
        for (val entry : policyMap.entrySet()) {
            withPolicy(entry.getKey(), entry.getValue());
        }
        return this;
    }

    /**
     * Configures the bundle to be signed with an Ed25519 private key.
     * <p>
     * When signing is enabled, the bundle will include a
     * {@code .sapl-manifest.json} file containing SHA-256 hashes of
     * all files and an Ed25519 signature. This allows consumers to verify bundle
     * integrity and authenticity.
     * </p>
     *
     * @param privateKey
     * Ed25519 private key for signing
     * @param keyId
     * identifier for the signing key (for key rotation support)
     *
     * @return this builder for method chaining
     *
     * @throws IllegalArgumentException
     * if privateKey is null or not Ed25519
     */
    public BundleBuilder signWith(PrivateKey privateKey, String keyId) {
        if (privateKey == null) {
            throw new IllegalArgumentException("Private key must not be null.");
        }
        if (!Set.of("Ed25519", "EdDSA").contains(privateKey.getAlgorithm())) {
            throw new IllegalArgumentException("Private key must be Ed25519, got: " + privateKey.getAlgorithm() + ".");
        }
        this.signingKey   = privateKey;
        this.signingKeyId = keyId != null ? keyId : "default";
        return this;
    }

    /**
     * Configures the bundle signature to expire at the specified time.
     * <p>
     * Expired signatures will be rejected during verification if the verifier
     * checks expiration. This is useful for
     * enforcing policy refresh intervals.
     * </p>
     *
     * @param expires
     * expiration timestamp
     *
     * @return this builder for method chaining
     */
    public BundleBuilder expiresAt(Instant expires) {
        this.signatureExpires = expires;
        return this;
    }

    /**
     * Builds the bundle and returns it as a byte array.
     *
     * @return the bundle as a byte array
     *
     * @throws PDPConfigurationException
     * if bundle creation fails
     */
    public byte[] build() {
        val outputStream = new ByteArrayOutputStream();
        writeTo(outputStream);
        return outputStream.toByteArray();
    }

    /**
     * Builds the bundle and writes it to the specified path.
     *
     * @param path
     * the target file path
     *
     * @throws PDPConfigurationException
     * if bundle creation or writing fails
     */
    public void writeTo(Path path) {
        try (val outputStream = Files.newOutputStream(path)) {
            writeTo(outputStream);
        } catch (IOException e) {
            throw new PDPConfigurationException("Failed to write bundle to path: %s.".formatted(path), e);
        }
    }

    /**
     * Builds the bundle and writes it to the specified output stream.
     * <p>
     * The output stream is not closed by this method; the caller is responsible for
     * closing it.
     * </p>
     *
     * @param outputStream
     * the target output stream
     *
     * @throws PDPConfigurationException
     * if bundle creation fails
     */
    public void writeTo(OutputStream outputStream) {
        try (val zipStream = new ZipOutputStream(outputStream)) {
            // Collect all files for potential signing
            val allFiles = new TreeMap<String, String>();

            if (pdpJson != null) {
                allFiles.put(PDP_JSON, pdpJson);
            }

            for (val entry : policies.entrySet()) {
                allFiles.put(entry.getKey(), entry.getValue());
            }

            // Write all content files
            for (val entry : allFiles.entrySet()) {
                writeEntry(zipStream, entry.getKey(), entry.getValue());
            }

            // Add manifest if signing is enabled
            if (signingKey != null) {
                val manifest = BundleSigner.sign(allFiles, signingKey, signingKeyId, signatureExpires);
                writeEntry(zipStream, BundleManifest.MANIFEST_FILENAME, manifest.toJson());
            }
        } catch (IOException e) {
            throw new PDPConfigurationException("Failed to create bundle.", e);
        }
    }

    /**
     * Checks if this builder is configured to sign the bundle.
     *
     * @return true if signing is enabled
     */
    public boolean isSigned() {
        return signingKey != null;
    }

    private void writeEntry(ZipOutputStream zipStream, String name, String content) throws IOException {
        val entry = new ZipEntry(name);
        zipStream.putNextEntry(entry);
        zipStream.write(content.getBytes(StandardCharsets.UTF_8));
        zipStream.closeEntry();
    }

}
