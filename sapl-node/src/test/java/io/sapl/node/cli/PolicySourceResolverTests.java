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
package io.sapl.node.cli;

import static io.sapl.spring.pdp.embedded.EmbeddedPDPProperties.PDPDataSource.BUNDLES;
import static io.sapl.spring.pdp.embedded.EmbeddedPDPProperties.PDPDataSource.DIRECTORY;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import lombok.val;

@DisplayName("policy source resolver")
class PolicySourceResolverTests {

    @Nested
    @DisplayName("directory source")
    class DirectorySourceTests {

        @Test
        @DisplayName("existing directory resolves to DIRECTORY type with absolute path")
        void whenExistingDirectory_thenResolvesToDirectory(@TempDir Path tempDir) {
            val result = PolicySourceResolver.resolve(policySourceWithDir(tempDir), null, null, discardErr());
            assertThat(result).satisfies(r -> {
                assertThat(r.configType()).isEqualTo(DIRECTORY);
                assertThat(r.path()).isEqualTo(tempDir.toAbsolutePath().toString());
                assertThat(r.publicKeyPath()).isNull();
                assertThat(r.allowUnsigned()).isFalse();
            });
        }

        @Test
        @DisplayName("non-existent directory returns null with error message")
        void whenNonExistentDirectory_thenReturnsNullWithError() {
            val err    = new StringWriter();
            val result = PolicySourceResolver.resolve(policySourceWithDir(Path.of("/nonexistent/path")), null, null,
                    new PrintWriter(err));
            assertThat(result).isNull();
            assertThat(err.toString()).contains("Policy directory not found");
        }

    }

    @Nested
    @DisplayName("bundle source")
    class BundleSourceTests {

        @Test
        @DisplayName("non-existent bundle file returns null with error message")
        void whenNonExistentBundle_thenReturnsNullWithError() {
            val err    = new StringWriter();
            val result = PolicySourceResolver.resolve(policySourceWithBundle(Path.of("/nonexistent/file.saplbundle")),
                    null, null, new PrintWriter(err));
            assertThat(result).isNull();
            assertThat(err.toString()).contains("Bundle file not found");
        }

        @Test
        @DisplayName("bundle with --no-verify resolves to BUNDLES type unsigned")
        void whenBundleWithNoVerify_thenResolvesUnsigned(@TempDir Path tempDir) throws IOException {
            val bundle = createFile(tempDir, "test.saplbundle");
            val result = PolicySourceResolver.resolve(policySourceWithBundle(bundle), bundleVerificationNoVerify(),
                    null, discardErr());
            assertThat(result).satisfies(r -> {
                assertThat(r.configType()).isEqualTo(BUNDLES);
                assertThat(r.path()).isEqualTo(tempDir.toAbsolutePath().toString());
                assertThat(r.publicKeyPath()).isNull();
                assertThat(r.allowUnsigned()).isTrue();
            });
        }

        @Test
        @DisplayName("bundle with existing public key resolves with key path")
        void whenBundleWithPublicKey_thenResolvesWithKeyPath(@TempDir Path tempDir) throws IOException {
            val bundle = createFile(tempDir, "test.saplbundle");
            val key    = createFile(tempDir, "key.pem");
            val result = PolicySourceResolver.resolve(policySourceWithBundle(bundle), bundleVerificationWithKey(key),
                    null, discardErr());
            assertThat(result).satisfies(r -> {
                assertThat(r.configType()).isEqualTo(BUNDLES);
                assertThat(r.publicKeyPath()).isEqualTo(key.toAbsolutePath().toString());
                assertThat(r.allowUnsigned()).isFalse();
            });
        }

        @Test
        @DisplayName("bundle with non-existent public key returns null with error")
        void whenBundleWithMissingPublicKey_thenReturnsNullWithError(@TempDir Path tempDir) throws IOException {
            val err    = new StringWriter();
            val bundle = createFile(tempDir, "test.saplbundle");
            val result = PolicySourceResolver.resolve(policySourceWithBundle(bundle),
                    bundleVerificationWithKey(Path.of("/nonexistent/key.pem")), null, new PrintWriter(err));
            assertThat(result).isNull();
            assertThat(err.toString()).contains("Public key file not found");
        }

        @Test
        @DisplayName("bundle with default key in sapl home resolves with default key")
        void whenBundleWithDefaultKey_thenResolvesWithDefaultKey(@TempDir Path tempDir) throws IOException {
            val bundle     = createFile(tempDir, "test.saplbundle");
            val defaultKey = createFile(tempDir, "public-key.pem");
            val result     = PolicySourceResolver.resolve(policySourceWithBundle(bundle), null, tempDir, discardErr());
            assertThat(result).satisfies(r -> {
                assertThat(r.configType()).isEqualTo(BUNDLES);
                assertThat(r.publicKeyPath()).isEqualTo(defaultKey.toAbsolutePath().toString());
                assertThat(r.allowUnsigned()).isFalse();
            });
        }

        @Test
        @DisplayName("bundle without verification and no default key returns null with error")
        void whenBundleWithoutVerification_thenReturnsNullWithError(@TempDir Path tempDir) throws IOException {
            val err    = new StringWriter();
            val bundle = createFile(tempDir, "test.saplbundle");
            val result = PolicySourceResolver.resolve(policySourceWithBundle(bundle), null, tempDir,
                    new PrintWriter(err));
            assertThat(result).isNull();
            assertThat(err.toString()).contains("Bundle signature verification required");
        }

        @Test
        @DisplayName("bundle with empty verification options and non-existent sapl home returns null with error")
        void whenBundleWithEmptyVerificationAndNoSaplHome_thenReturnsNullWithError(@TempDir Path tempDir)
                throws IOException {
            val err          = new StringWriter();
            val bundle       = createFile(tempDir, "test.saplbundle");
            val verification = new BundleVerificationOptions();
            val result       = PolicySourceResolver.resolve(policySourceWithBundle(bundle), verification,
                    Path.of("/nonexistent/sapl-home"), new PrintWriter(err));
            assertThat(result).isNull();
            assertThat(err.toString()).contains("Bundle signature verification required");
        }

    }

    @Nested
    @DisplayName("auto-detection")
    class AutoDetectionTests {

        @Test
        @DisplayName("non-existent sapl home returns null with error")
        void whenSaplHomeNotFound_thenReturnsNullWithError() {
            val err    = new StringWriter();
            val result = PolicySourceResolver.resolve(null, null, Path.of("/nonexistent/sapl-home"),
                    new PrintWriter(err));
            assertThat(result).isNull();
            assertThat(err.toString()).contains("~/.sapl/ directory not found");
        }

        @Test
        @DisplayName("sapl home with .sapl files resolves to DIRECTORY type")
        void whenSaplHomeWithSaplFiles_thenResolvesToDirectory(@TempDir Path saplHome) throws IOException {
            createFile(saplHome, "policy.sapl");
            val result = PolicySourceResolver.resolve(null, null, saplHome, discardErr());
            assertThat(result).satisfies(r -> {
                assertThat(r.configType()).isEqualTo(DIRECTORY);
                assertThat(r.path()).isEqualTo(saplHome.toAbsolutePath().toString());
            });
        }

        @Test
        @DisplayName("sapl home with .saplbundle and --no-verify resolves to BUNDLES type")
        void whenSaplHomeWithBundleAndNoVerify_thenResolvesToBundles(@TempDir Path saplHome) throws IOException {
            createFile(saplHome, "policies.saplbundle");
            val result = PolicySourceResolver.resolve(null, bundleVerificationNoVerify(), saplHome, discardErr());
            assertThat(result).satisfies(r -> {
                assertThat(r.configType()).isEqualTo(BUNDLES);
                assertThat(r.allowUnsigned()).isTrue();
            });
        }

        @Test
        @DisplayName("sapl home with .saplbundle and default key resolves with key")
        void whenSaplHomeWithBundleAndDefaultKey_thenResolvesWithKey(@TempDir Path saplHome) throws IOException {
            createFile(saplHome, "policies.saplbundle");
            val defaultKey = createFile(saplHome, "public-key.pem");
            val result     = PolicySourceResolver.resolve(null, null, saplHome, discardErr());
            assertThat(result).satisfies(r -> {
                assertThat(r.configType()).isEqualTo(BUNDLES);
                assertThat(r.publicKeyPath()).isEqualTo(defaultKey.toAbsolutePath().toString());
            });
        }

        @Test
        @DisplayName("sapl home with both .sapl and .saplbundle files returns null with error")
        void whenSaplHomeWithBothTypes_thenReturnsNullWithError(@TempDir Path saplHome) throws IOException {
            createFile(saplHome, "policy.sapl");
            createFile(saplHome, "policies.saplbundle");
            val err    = new StringWriter();
            val result = PolicySourceResolver.resolve(null, null, saplHome, new PrintWriter(err));
            assertThat(result).isNull();
            assertThat(err.toString()).contains("Found both .sapl and .saplbundle");
        }

        @Test
        @DisplayName("empty sapl home returns null with error")
        void whenSaplHomeEmpty_thenReturnsNullWithError(@TempDir Path saplHome) {
            val err    = new StringWriter();
            val result = PolicySourceResolver.resolve(null, null, saplHome, new PrintWriter(err));
            assertThat(result).isNull();
            assertThat(err.toString()).contains("No policies found");
        }

        @Test
        @DisplayName("policy source with neither dir nor bundle falls through to auto-detection")
        void whenPolicySourceWithoutDirOrBundle_thenAutoDetects(@TempDir Path saplHome) throws IOException {
            createFile(saplHome, "policy.sapl");
            val result = PolicySourceResolver.resolve(new PolicySourceOptions(), null, saplHome, discardErr());
            assertThat(result).satisfies(r -> {
                assertThat(r.configType()).isEqualTo(DIRECTORY);
                assertThat(r.path()).isEqualTo(saplHome.toAbsolutePath().toString());
            });
        }

    }

    private static PolicySourceOptions policySourceWithDir(Path dir) {
        val options = new PolicySourceOptions();
        options.dir = dir;
        return options;
    }

    private static PolicySourceOptions policySourceWithBundle(Path bundle) {
        val options = new PolicySourceOptions();
        options.bundle = bundle;
        return options;
    }

    private static BundleVerificationOptions bundleVerificationNoVerify() {
        val options = new BundleVerificationOptions();
        options.noVerify = true;
        return options;
    }

    private static BundleVerificationOptions bundleVerificationWithKey(Path key) {
        val options = new BundleVerificationOptions();
        options.publicKey = key;
        return options;
    }

    private static Path createFile(Path dir, String name) throws IOException {
        val file = dir.resolve(name);
        Files.writeString(file, "dummy");
        return file;
    }

    private static PrintWriter discardErr() {
        return new PrintWriter(new StringWriter());
    }

}
