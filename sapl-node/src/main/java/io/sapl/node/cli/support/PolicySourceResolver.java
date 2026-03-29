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
package io.sapl.node.cli.support;

import io.sapl.node.cli.commands.BundleCommand;
import io.sapl.node.cli.options.BundleVerificationOptions;
import io.sapl.node.cli.options.PolicySourceOptions;
import io.sapl.spring.pdp.embedded.EmbeddedPDPProperties.PDPDataSource;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static io.sapl.pdp.configuration.source.PdpIdValidator.resolveHomeFolderIfPresent;
import static io.sapl.spring.pdp.embedded.EmbeddedPDPProperties.PDPDataSource.BUNDLES;
import static io.sapl.spring.pdp.embedded.EmbeddedPDPProperties.PDPDataSource.DIRECTORY;

@UtilityClass
public class PolicySourceResolver {

    private static final String ERROR_BOTH_POLICY_TYPES_FOUND      = "Error: Found both .sapl and .saplbundle files in %s. Use --dir or --bundle explicitly.";
    private static final String ERROR_BUNDLE_VERIFICATION_REQUIRED = "Error: Bundle signature verification required. Provide --public-key <file>, place public-key.pem in ~/.sapl/, or use --no-verify.";
    private static final String ERROR_DIRECTORY_NOT_FOUND          = "Error: Policy directory not found: %s.";
    private static final String ERROR_NO_POLICIES_FOUND            = "Error: No policies found. Use --dir, --bundle, or create ~/.sapl/ with policy files.";
    private static final String ERROR_PUBLIC_KEY_NOT_FOUND         = "Error: Public key file not found: %s.";
    private static final String ERROR_SAPL_HOME_NOT_FOUND          = "Error: ~/.sapl/ directory not found. Use --dir or --bundle to specify policy location.";

    public record ResolvedPolicy(PDPDataSource configType, String path, String publicKeyPath, boolean allowUnsigned) {}

    public static ResolvedPolicy resolve(PolicySourceOptions policySource, BundleVerificationOptions bundleVerification,
            Path saplHomeOverride, PrintWriter err) {
        if (policySource != null && policySource.dir != null) {
            return resolveDirectorySource(policySource, err);
        }
        if (policySource != null && policySource.bundle != null) {
            return resolveBundleSource(policySource, bundleVerification, saplHomeOverride, err);
        }
        return autoDetectPolicySource(bundleVerification, saplHomeOverride, err);
    }

    private static ResolvedPolicy resolveDirectorySource(PolicySourceOptions policySource, PrintWriter err) {
        if (!Files.isDirectory(policySource.dir)) {
            err.println(ERROR_DIRECTORY_NOT_FOUND.formatted(policySource.dir));
            return null;
        }
        val path = policySource.dir.toAbsolutePath().toString();
        return new ResolvedPolicy(DIRECTORY, path, null, false);
    }

    private static ResolvedPolicy resolveBundleSource(PolicySourceOptions policySource,
            BundleVerificationOptions bundleVerification, Path saplHomeOverride, PrintWriter err) {
        if (!Files.isRegularFile(policySource.bundle)) {
            err.println(BundleCommand.ERROR_BUNDLE_NOT_FOUND.formatted(policySource.bundle));
            return null;
        }
        val path = Objects
                .requireNonNull(policySource.bundle.toAbsolutePath().getParent(), "Bundle file has no parent directory")
                .toString();
        return resolveBundleVerification(path, bundleVerification, saplHomeOverride, err);
    }

    private static ResolvedPolicy autoDetectPolicySource(BundleVerificationOptions bundleVerification,
            Path saplHomeOverride, PrintWriter err) {
        val saplHome = resolveSaplHome(saplHomeOverride);

        if (!Files.isDirectory(saplHome)) {
            err.println(ERROR_SAPL_HOME_NOT_FOUND);
            return null;
        }

        try (val stream = Files.list(saplHome)) {
            val files          = stream.toList();
            val hasSaplFiles   = files.stream().anyMatch(p -> p.getFileName().toString().endsWith(".sapl"));
            val hasBundleFiles = files.stream().anyMatch(p -> p.getFileName().toString().endsWith(".saplbundle"));

            if (hasSaplFiles && hasBundleFiles) {
                err.println(ERROR_BOTH_POLICY_TYPES_FOUND.formatted(saplHome));
                return null;
            }

            if (!hasSaplFiles && !hasBundleFiles) {
                err.println(ERROR_NO_POLICIES_FOUND);
                return null;
            }

            val path = saplHome.toAbsolutePath().toString();

            if (hasSaplFiles) {
                return new ResolvedPolicy(DIRECTORY, path, null, false);
            }

            return resolveBundleVerification(path, bundleVerification, saplHomeOverride, err);
        } catch (IOException e) {
            err.println(ERROR_SAPL_HOME_NOT_FOUND);
            return null;
        }
    }

    private static ResolvedPolicy resolveBundleVerification(String path, BundleVerificationOptions bundleVerification,
            Path saplHomeOverride, PrintWriter err) {
        if (bundleVerification != null && bundleVerification.publicKey != null) {
            if (!Files.isRegularFile(bundleVerification.publicKey)) {
                err.println(ERROR_PUBLIC_KEY_NOT_FOUND.formatted(bundleVerification.publicKey));
                return null;
            }
            return new ResolvedPolicy(BUNDLES, path, bundleVerification.publicKey.toAbsolutePath().toString(), false);
        }

        if (bundleVerification != null && bundleVerification.noVerify) {
            return new ResolvedPolicy(BUNDLES, path, null, true);
        }

        val saplHome = resolveSaplHome(saplHomeOverride);
        if (Files.isDirectory(saplHome)) {
            val defaultKey = saplHome.resolve("public-key.pem");
            if (Files.isRegularFile(defaultKey)) {
                return new ResolvedPolicy(BUNDLES, path, defaultKey.toAbsolutePath().toString(), false);
            }
        }

        err.println(ERROR_BUNDLE_VERIFICATION_REQUIRED);
        return null;
    }

    private static Path resolveSaplHome(Path saplHomeOverride) {
        if (saplHomeOverride != null) {
            return saplHomeOverride;
        }
        return resolveHomeFolderIfPresent("~/.sapl");
    }

}
