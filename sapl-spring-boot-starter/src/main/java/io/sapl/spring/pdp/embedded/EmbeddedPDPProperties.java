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
package io.sapl.spring.pdp.embedded;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for the embedded PDP.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "io.sapl.pdp.embedded")
public class EmbeddedPDPProperties {

    @NotNull
    private Boolean enabled = true;

    /**
     * Selects the source of configuration and policies:
     * <p>
     * The options are:
     * <p>
     * - RESOURCES: Loads a fixed set of documents and pdp.json from the bundled
     * resources. These will be loaded once and cannot be updated at runtime.
     * <p>
     * - DIRECTORY: Monitors a single directory for documents and configuration.
     * Will automatically update any changes made at runtime. Changes will directly
     * be reflected in decisions made in already existing subscriptions.
     * <p>
     * - MULTI_DIRECTORY: Monitors subdirectories within a base directory, where
     * each subdirectory name becomes the pdpId for multi-tenant routing.
     * <p>
     * - BUNDLES: Monitors a directory for .saplbundle files, where each bundle
     * filename (without extension) becomes the pdpId for multi-tenant routing.
     */
    @NotNull
    private PDPDataSource pdpConfigType = PDPDataSource.RESOURCES;

    /**
     * Selects the indexing algorithm used by the PDP.
     * <p>
     * The options are:
     * <p>
     * - NAIVE : A simple implementation for systems with small numbers of
     * documents.
     * <p>
     * - CANONICAL : An improved index for systems with large numbers of documents.
     * Takes more time to update and initialize but significantly reduces retrieval
     * time.
     */
    @NotNull
    private IndexType index = IndexType.NAIVE;

    /**
     * This property sets the path to the folder where the pdp.json configuration
     * file is located.
     * <p>
     * If the pdpConfigType is set to RESOURCES, / is the root of the context path.
     * For DIRECTORY, MULTI_DIRECTORY, or BUNDLES, it must be a valid path on the
     * system's file system.
     */
    @NotEmpty
    private String configPath = "/policies";

    /**
     * This property sets the path to the folder where the *.sapl documents are
     * located.
     * <p>
     * If the pdpConfigType is set to RESOURCES, / is the root of the context path.
     * For DIRECTORY, MULTI_DIRECTORY, or BUNDLES, it must be a valid path on the
     * system's file system.
     */
    @NotEmpty
    private String policiesPath = "/policies";

    /**
     * Security configuration for bundle signature verification.
     * Only used when pdpConfigType is BUNDLES.
     */
    private BundleSecurityProperties bundleSecurity = new BundleSecurityProperties();

    /**
     * Indicate the source type for loading policies.
     */
    public enum PDPDataSource {

        /**
         * Loads a fixed set of documents and pdp.json from the bundled
         * resources. These will be loaded once and cannot be updated at runtime.
         */
        RESOURCES,
        /**
         * Monitors a single directory for documents and configuration.
         * Will automatically update on changes at runtime.
         */
        DIRECTORY,
        /**
         * Monitors subdirectories within a base directory, where each
         * subdirectory name becomes the pdpId for multi-tenant routing.
         */
        MULTI_DIRECTORY,
        /**
         * Monitors a directory for .saplbundle files, where each bundle
         * filename (without extension) becomes the pdpId for multi-tenant routing.
         */
        BUNDLES

    }

    /**
     * Selects the indexing algorithm.
     */
    public enum IndexType {

        /**
         * Simple default index.
         */
        NAIVE,
        /**
         * High-performance policy index for large collections of policies.
         */
        CANONICAL

    }

    /**
     * Security properties for bundle signature verification.
     * <p>
     * Behavior:
     * <ul>
     * <li>If publicKeyPath or publicKey is set: signature verification enabled</li>
     * <li>If no key and allowUnsigned=true AND acceptRisks=true: unsigned bundles
     * accepted</li>
     * <li>Otherwise: startup fails with clear error</li>
     * </ul>
     */
    @Data
    public static class BundleSecurityProperties {

        /**
         * Path to the Ed25519 public key file for bundle signature verification.
         * If set, signature verification is enabled.
         */
        private String publicKeyPath;

        /**
         * Base64-encoded Ed25519 public key for bundle signature verification.
         * Alternative to publicKeyPath for containerized deployments where
         * the key is injected via environment variable.
         */
        private String publicKey;

        /**
         * Whether to check signature expiration timestamps.
         * Only applies when signature verification is enabled.
         */
        private boolean checkExpiration = false;

        /**
         * Disable signature verification for bundles.
         * <p>
         * WARNING: Requires acceptRisks to also be true.
         */
        private boolean allowUnsigned = false;

        /**
         * Explicit acceptance of security risks when loading unsigned bundles.
         * <p>
         * Setting this to true acknowledges:
         * <ul>
         * <li>Bundles may originate from untrusted sources</li>
         * <li>Bundles may have been tampered with</li>
         * <li>Malicious policies could bypass access control</li>
         * </ul>
         * Both allowUnsigned AND acceptRisks must be true to disable verification.
         */
        private boolean acceptRisks = false;

    }

    /**
     * If this property is set to true, JSON in logged traces and reports is pretty
     * printed.
     */
    private boolean prettyPrintReports = false;

    /**
     * If this property is set to true, the full JSON evaluation trace is logged on
     * each decision.
     */
    private boolean printTrace = false;

    /**
     * If this property is set to true, the JSON evaluation report is logged on each
     * decision.
     */
    private boolean printJsonReport = false;

    /**
     * If this property is set to true, the textual decision report is logged on
     * each decision.
     */
    private boolean printTextReport = false;

}
