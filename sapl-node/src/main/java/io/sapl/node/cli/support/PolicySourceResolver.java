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

import io.sapl.functions.libraries.crypto.CryptoException;
import io.sapl.functions.libraries.crypto.PemUtils;
import io.sapl.node.cli.commands.BundleCommand;
import io.sapl.node.cli.options.BundleVerificationOptions;
import io.sapl.node.cli.options.PolicySourceOptions;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;

import static io.sapl.pdp.configuration.source.PdpIdValidator.resolveHomeFolderIfPresent;

@UtilityClass
public class PolicySourceResolver {

    private static final String ALGORITHM_ED25519 = "Ed25519";

    private static final String ERROR_BOTH_POLICY_TYPES_FOUND      = "Error: Found both .sapl and .saplbundle files in %s. Use --dir or --bundle explicitly.";
    private static final String ERROR_BUNDLE_VERIFICATION_REQUIRED = "Error: Bundle signature verification required. Provide --public-key <file>, place public-key.pem in ~/.sapl/, or use --no-verify.";
    private static final String ERROR_DIRECTORY_NOT_FOUND          = "Error: Policy directory not found: %s.";
    private static final String ERROR_NO_POLICIES_FOUND            = "Error: No policies found. Use --dir, --bundle, or create ~/.sapl/ with policy files.";
    private static final String ERROR_PUBLIC_KEY_INVALID           = "Error: Public key file is not a valid Ed25519 key: %s.";
    private static final String ERROR_PUBLIC_KEY_NOT_FOUND         = "Error: Public key file not found: %s.";
    private static final String ERROR_SAPL_HOME_LISTING_FAILED     = "Error: Failed to list ~/.sapl/ directory contents: %s.";
    private static final String ERROR_SAPL_HOME_NOT_FOUND          = "Error: ~/.sapl/ directory not found. Use --dir or --bundle to specify policy location.";

    /**
     * How the policies are sourced for a local PDP. The path is a policy
     * directory for {@link #DIRECTORY}, a single {@code .saplbundle} file for
     * {@link #SINGLE_BUNDLE}, and a directory of bundle files (one tenant per
     * file) for {@link #BUNDLE_DIRECTORY}.
     */
    public enum SourceKind {
        DIRECTORY,
        SINGLE_BUNDLE,
        BUNDLE_DIRECTORY
    }

    public record ResolvedPolicy(SourceKind kind, Path path, @Nullable PublicKey publicKey, boolean allowUnsigned) {}

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
        return new ResolvedPolicy(SourceKind.DIRECTORY, policySource.dir.toAbsolutePath(), null, false);
    }

    private static ResolvedPolicy resolveBundleSource(PolicySourceOptions policySource,
            BundleVerificationOptions bundleVerification, Path saplHomeOverride, PrintWriter err) {
        if (!Files.isRegularFile(policySource.bundle)) {
            err.println(BundleCommand.ERROR_BUNDLE_NOT_FOUND.formatted(policySource.bundle));
            return null;
        }
        return resolveBundleVerification(SourceKind.SINGLE_BUNDLE, policySource.bundle.toAbsolutePath(),
                bundleVerification, saplHomeOverride, err);
    }

    private static ResolvedPolicy autoDetectPolicySource(BundleVerificationOptions bundleVerification,
            Path saplHomeOverride, PrintWriter err) {
        val saplHome = resolveSaplHome(saplHomeOverride);

        if (!Files.isDirectory(saplHome)) {
            err.println(ERROR_SAPL_HOME_NOT_FOUND);
            return null;
        }

        try (val stream = Files.list(saplHome)) {
            boolean hasSaplFiles   = false;
            boolean hasBundleFiles = false;
            for (val path : (Iterable<Path>) stream::iterator) {
                val fileName = path.getFileName();
                if (fileName == null) {
                    continue;
                }
                val name = fileName.toString();
                if (name.endsWith(".sapl")) {
                    hasSaplFiles = true;
                } else if (name.endsWith(".saplbundle")) {
                    hasBundleFiles = true;
                }
                if (hasSaplFiles && hasBundleFiles) {
                    break;
                }
            }

            if (hasSaplFiles && hasBundleFiles) {
                err.println(ERROR_BOTH_POLICY_TYPES_FOUND.formatted(saplHome));
                return null;
            }

            if (!hasSaplFiles && !hasBundleFiles) {
                err.println(ERROR_NO_POLICIES_FOUND);
                return null;
            }

            if (hasSaplFiles) {
                return new ResolvedPolicy(SourceKind.DIRECTORY, saplHome.toAbsolutePath(), null, false);
            }

            return resolveBundleVerification(SourceKind.BUNDLE_DIRECTORY, saplHome.toAbsolutePath(), bundleVerification,
                    saplHomeOverride, err);
        } catch (IOException e) {
            err.println(ERROR_SAPL_HOME_LISTING_FAILED.formatted(e.getMessage()));
            return null;
        }
    }

    private static ResolvedPolicy resolveBundleVerification(SourceKind kind, Path path,
            BundleVerificationOptions bundleVerification, Path saplHomeOverride, PrintWriter err) {
        if (bundleVerification != null && bundleVerification.publicKey != null) {
            return resolveWithKey(kind, path, bundleVerification.publicKey, err);
        }

        if (bundleVerification != null && bundleVerification.noVerify) {
            return new ResolvedPolicy(kind, path, null, true);
        }

        val saplHome = resolveSaplHome(saplHomeOverride);
        if (Files.isDirectory(saplHome)) {
            val defaultKey = saplHome.resolve("public-key.pem");
            if (Files.isRegularFile(defaultKey)) {
                return resolveWithKey(kind, path, defaultKey, err);
            }
        }

        err.println(ERROR_BUNDLE_VERIFICATION_REQUIRED);
        return null;
    }

    private static ResolvedPolicy resolveWithKey(SourceKind kind, Path path, Path keyFile, PrintWriter err) {
        if (!Files.isRegularFile(keyFile)) {
            err.println(ERROR_PUBLIC_KEY_NOT_FOUND.formatted(keyFile));
            return null;
        }
        try {
            return new ResolvedPolicy(kind, path, loadEd25519PublicKey(keyFile), false);
        } catch (IOException | GeneralSecurityException | CryptoException e) {
            err.println(ERROR_PUBLIC_KEY_INVALID.formatted(keyFile));
            return null;
        }
    }

    private static PublicKey loadEd25519PublicKey(Path keyFile) throws IOException, GeneralSecurityException {
        val keyBytes = PemUtils.decodePemFromFile(keyFile);
        return KeyFactory.getInstance(ALGORITHM_ED25519).generatePublic(new X509EncodedKeySpec(keyBytes));
    }

    private static Path resolveSaplHome(Path saplHomeOverride) {
        if (saplHomeOverride != null) {
            return saplHomeOverride;
        }
        return resolveHomeFolderIfPresent("~/.sapl");
    }

}
