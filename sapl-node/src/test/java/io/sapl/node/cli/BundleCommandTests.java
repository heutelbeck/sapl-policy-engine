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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Key;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import lombok.val;
import picocli.CommandLine;

@DisplayName("Bundle Commands")
class BundleCommandTests {

    private static final String TEST_POLICY = """
            policy "test-policy"
            permit
                action == "read";
            """;

    private static final String TEST_PDP_JSON = """
            {
              "algorithm": {
                "votingMode": "DENY_UNLESS_PERMIT",
                "defaultDecision": "DENY",
                "errorHandling": "ABSTAIN"
              }
            }
            """;

    @TempDir
    Path tempDir;

    private StringWriter out;
    private StringWriter err;
    private CommandLine  cmd;

    @BeforeEach
    void setUp() {
        out = new StringWriter();
        err = new StringWriter();
        cmd = new CommandLine(new SaplNodeCli());
        cmd.setOut(new PrintWriter(out));
        cmd.setErr(new PrintWriter(err));
    }

    @Nested
    @DisplayName("bundle create")
    class CreateTests {

        @Test
        @DisplayName("creates bundle from directory with policies")
        void whenDirectoryWithPolicies_thenCreatesBundleSuccessfully() throws Exception {
            val inputDir   = createPolicyInputDir();
            val outputFile = tempDir.resolve("test.saplbundle");

            val exitCode = cmd.execute("bundle", "create", "-i", inputDir.toString(), "-o", outputFile.toString());

            assertThat(exitCode).isZero();
            assertThat(out.toString()).contains("Created bundle").contains("1 policies");
            assertThat(Files.exists(outputFile)).isTrue();
            assertThat(Files.size(outputFile)).isGreaterThan(0);
        }

        @Test
        @DisplayName("creates bundle with multiple policies")
        void whenMultiplePolicies_thenCountsAllPolicies() throws Exception {
            val inputDir   = tempDir.resolve("policies");
            val outputFile = tempDir.resolve("test.saplbundle");

            Files.createDirectory(inputDir);
            Files.writeString(inputDir.resolve("policy1.sapl"), TEST_POLICY);
            Files.writeString(inputDir.resolve("policy2.sapl"), TEST_POLICY);
            Files.writeString(inputDir.resolve("policy3.sapl"), TEST_POLICY);

            val exitCode = cmd.execute("bundle", "create", "-i", inputDir.toString(), "-o", outputFile.toString());

            assertThat(exitCode).isZero();
            assertThat(out.toString()).contains("3 policies");
        }

        @Test
        @DisplayName("fails when input directory does not exist")
        void whenInputNotExists_thenFails() {
            val inputDir   = tempDir.resolve("nonexistent");
            val outputFile = tempDir.resolve("test.saplbundle");

            val exitCode = cmd.execute("bundle", "create", "-i", inputDir.toString(), "-o", outputFile.toString());

            assertThat(exitCode).isEqualTo(1);
            assertThat(err.toString()).contains("Error").contains("not a directory");
        }

        @Test
        @DisplayName("fails when no policies found")
        void whenNoPolicies_thenFails() throws Exception {
            val inputDir   = tempDir.resolve("empty");
            val outputFile = tempDir.resolve("test.saplbundle");

            Files.createDirectory(inputDir);
            Files.writeString(inputDir.resolve("readme.txt"), "Not a policy");

            val exitCode = cmd.execute("bundle", "create", "-i", inputDir.toString(), "-o", outputFile.toString());

            assertThat(exitCode).isEqualTo(1);
            assertThat(err.toString()).contains("No .sapl files found");
        }

        @Test
        @DisplayName("creates signed bundle when key provided")
        void whenKeyProvided_thenCreatesSignedBundle() throws Exception {
            val inputDir    = createPolicyInputDir();
            val outputFile  = tempDir.resolve("signed.saplbundle");
            val keyPair     = generateEd25519KeyPair();
            val privateKeyF = tempDir.resolve("private.pem");

            Files.writeString(privateKeyF, toPem(keyPair.privateKey(), "PRIVATE KEY"));

            val exitCode = cmd.execute("bundle", "create", "-i", inputDir.toString(), "-o", outputFile.toString(), "-k",
                    privateKeyF.toString(), "--key-id", "my-key");

            assertThat(exitCode).isZero();
            assertThat(out.toString()).contains("Created signed bundle").contains("my-key");
            assertThat(Files.exists(outputFile)).isTrue();
        }

        @Test
        @DisplayName("signed bundle created with key passes verification")
        void whenCreatedWithKey_thenPassesVerification() throws Exception {
            val inputDir    = createPolicyInputDir();
            val outputFile  = tempDir.resolve("verified.saplbundle");
            val keyPair     = generateEd25519KeyPair();
            val privateKeyF = tempDir.resolve("private.pem");
            val publicKeyF  = tempDir.resolve("public.pem");

            Files.writeString(privateKeyF, toPem(keyPair.privateKey(), "PRIVATE KEY"));
            Files.writeString(publicKeyF, toPem(keyPair.publicKey(), "PUBLIC KEY"));

            cmd.execute("bundle", "create", "-i", inputDir.toString(), "-o", outputFile.toString(), "-k",
                    privateKeyF.toString());

            val verifyExitCode = cmd.execute("bundle", "verify", "-b", outputFile.toString(), "-k",
                    publicKeyF.toString());

            assertThat(verifyExitCode).isZero();
            assertThat(out.toString()).contains("Verification successful");
        }

        @Test
        @DisplayName("fails when key file not found")
        void whenKeyFileNotFound_thenFails() throws Exception {
            val inputDir   = createPolicyInputDir();
            val outputFile = tempDir.resolve("test.saplbundle");
            val keyFile    = tempDir.resolve("nonexistent.pem");

            val exitCode = cmd.execute("bundle", "create", "-i", inputDir.toString(), "-o", outputFile.toString(), "-k",
                    keyFile.toString());

            assertThat(exitCode).isEqualTo(1);
            assertThat(err.toString()).contains("Key file not found");
        }

    }

    @Nested
    @DisplayName("bundle inspect")
    class InspectTests {

        @Test
        @DisplayName("shows unsigned bundle contents")
        void whenUnsignedBundle_thenShowsContentsAndUnsignedStatus() throws Exception {
            val bundleFile = createTestBundle();

            val exitCode = cmd.execute("bundle", "inspect", "-b", bundleFile.toString());

            assertThat(exitCode).isZero();
            assertThat(out.toString()).satisfies(output -> {
                assertThat(output).contains("Bundle:");
                assertThat(output).contains("UNSIGNED");
                assertThat(output).contains("Configuration");
                assertThat(output).contains("Policies:");
                assertThat(output).contains("test.sapl");
            });
        }

        @Test
        @DisplayName("shows signed bundle metadata")
        void whenSignedBundle_thenShowsSignatureMetadata() throws Exception {
            val bundleFile = createSignedTestBundle();

            val exitCode = cmd.execute("bundle", "inspect", "-b", bundleFile.toString());

            assertThat(exitCode).isZero();
            assertThat(out.toString()).satisfies(output -> {
                assertThat(output).contains("SIGNED");
                assertThat(output).contains("Ed25519");
                assertThat(output).contains("Key ID:");
                assertThat(output).contains("Created:");
            });
        }

        @Test
        @DisplayName("fails when bundle file not found")
        void whenBundleNotFound_thenFails() {
            val bundleFile = tempDir.resolve("nonexistent.saplbundle");

            val exitCode = cmd.execute("bundle", "inspect", "-b", bundleFile.toString());

            assertThat(exitCode).isEqualTo(1);
            assertThat(err.toString()).contains("Bundle file not found");
        }

    }

    @Nested
    @DisplayName("bundle sign")
    class SignTests {

        @Test
        @DisplayName("signs unsigned bundle")
        void whenUnsignedBundle_thenSignsSuccessfully() throws Exception {
            val bundleFile = createTestBundle();
            val keyPair    = generateEd25519KeyPair();
            val privateKey = tempDir.resolve("private.pem");
            Files.writeString(privateKey, toPem(keyPair.privateKey(), "PRIVATE KEY"));

            val signedBundle = tempDir.resolve("signed.saplbundle");

            val exitCode = cmd.execute("bundle", "sign", "-b", bundleFile.toString(), "-k", privateKey.toString(), "-o",
                    signedBundle.toString(), "--key-id", "test-key");

            assertThat(exitCode).isZero();
            assertThat(out.toString()).contains("Signed bundle").contains("test-key");
            assertThat(Files.exists(signedBundle)).isTrue();
        }

        @Test
        @DisplayName("fails when key file not found")
        void whenKeyNotFound_thenFails() throws Exception {
            val bundleFile = createTestBundle();
            val keyFile    = tempDir.resolve("nonexistent.pem");

            val exitCode = cmd.execute("bundle", "sign", "-b", bundleFile.toString(), "-k", keyFile.toString());

            assertThat(exitCode).isEqualTo(1);
            assertThat(err.toString()).contains("Key file not found");
        }

    }

    @Nested
    @DisplayName("bundle keygen")
    class KeygenTests {

        @Test
        @DisplayName("generates Ed25519 keypair")
        void whenValidOutput_thenGeneratesKeypair() {
            val outputPrefix = tempDir.resolve("test-key");

            val exitCode = cmd.execute("bundle", "keygen", "-o", outputPrefix.toString());

            assertThat(exitCode).isZero();
            assertThat(out.toString()).satisfies(output -> {
                assertThat(output).contains("Generated Ed25519 keypair");
                assertThat(output).contains("Private key:");
                assertThat(output).contains("Public key:");
            });
            assertThat(tempDir.resolve("test-key.pem")).exists();
            assertThat(tempDir.resolve("test-key.pub")).exists();
        }

        @Test
        @DisplayName("generated keys have valid PEM format")
        void whenGenerated_thenKeysHaveValidPemFormat() throws Exception {
            val outputPrefix = tempDir.resolve("format-test");

            cmd.execute("bundle", "keygen", "-o", outputPrefix.toString());

            val privateKeyContent = Files.readString(tempDir.resolve("format-test.pem"));
            val publicKeyContent  = Files.readString(tempDir.resolve("format-test.pub"));

            assertThat(privateKeyContent).startsWith("-----BEGIN PRIVATE KEY-----")
                    .endsWith("-----END PRIVATE KEY-----\n");
            assertThat(publicKeyContent).startsWith("-----BEGIN PUBLIC KEY-----")
                    .endsWith("-----END PUBLIC KEY-----\n");
        }

        @Test
        @DisplayName("generated keys work with sign and verify")
        void whenGeneratedKeys_thenSignAndVerifyWorks() throws Exception {
            val keyPrefix  = tempDir.resolve("e2e-key");
            val bundleFile = createTestBundle();

            cmd.execute("bundle", "keygen", "-o", keyPrefix.toString());

            val signedBundle = tempDir.resolve("e2e-signed.saplbundle");
            val signExitCode = cmd.execute("bundle", "sign", "-b", bundleFile.toString(), "-k",
                    tempDir.resolve("e2e-key.pem").toString(), "-o", signedBundle.toString());
            assertThat(signExitCode).isZero();

            val verifyExitCode = cmd.execute("bundle", "verify", "-b", signedBundle.toString(), "-k",
                    tempDir.resolve("e2e-key.pub").toString());
            assertThat(verifyExitCode).isZero();
            assertThat(out.toString()).contains("Verification successful");
        }

        @Test
        @DisplayName("fails when file already exists")
        void whenFileExists_thenFailsWithoutForce() throws Exception {
            val outputPrefix = tempDir.resolve("existing");
            Files.writeString(tempDir.resolve("existing.pem"), "existing content");

            val exitCode = cmd.execute("bundle", "keygen", "-o", outputPrefix.toString());

            assertThat(exitCode).isEqualTo(1);
            assertThat(err.toString()).contains("File already exists").contains("--force");
        }

        @Test
        @DisplayName("overwrites existing files with --force")
        void whenForceFlag_thenOverwritesExistingFiles() throws Exception {
            val outputPrefix = tempDir.resolve("overwrite");
            Files.writeString(tempDir.resolve("overwrite.pem"), "old content");
            Files.writeString(tempDir.resolve("overwrite.pub"), "old content");

            val exitCode = cmd.execute("bundle", "keygen", "-o", outputPrefix.toString(), "--force");

            assertThat(exitCode).isZero();
            assertThat(Files.readString(tempDir.resolve("overwrite.pem"))).startsWith("-----BEGIN PRIVATE KEY-----");
            assertThat(Files.readString(tempDir.resolve("overwrite.pub"))).startsWith("-----BEGIN PUBLIC KEY-----");
        }

    }

    @Nested
    @DisplayName("bundle verify")
    class VerifyTests {

        @Test
        @DisplayName("verifies valid signed bundle")
        void whenValidSignature_thenVerifiesSuccessfully() throws Exception {
            val keyPair      = generateEd25519KeyPair();
            val bundleFile   = createSignedTestBundle(keyPair.privateKey());
            val publicKeyPem = tempDir.resolve("public.pem");
            Files.writeString(publicKeyPem, toPem(keyPair.publicKey(), "PUBLIC KEY"));

            val exitCode = cmd.execute("bundle", "verify", "-b", bundleFile.toString(), "-k", publicKeyPem.toString());

            assertThat(exitCode).isZero();
            assertThat(out.toString()).contains("Verification successful").contains("Files verified:");
        }

        @Test
        @DisplayName("fails with wrong public key")
        void whenWrongKey_thenVerificationFails() throws Exception {
            val signingKeyPair = generateEd25519KeyPair();
            val wrongKeyPair   = generateEd25519KeyPair();
            val bundleFile     = createSignedTestBundle(signingKeyPair.privateKey());
            val wrongPublicKey = tempDir.resolve("wrong.pem");
            Files.writeString(wrongPublicKey, toPem(wrongKeyPair.publicKey(), "PUBLIC KEY"));

            val exitCode = cmd.execute("bundle", "verify", "-b", bundleFile.toString(), "-k",
                    wrongPublicKey.toString());

            assertThat(exitCode).isEqualTo(1);
            assertThat(err.toString()).contains("Verification FAILED");
        }

        @Test
        @DisplayName("fails when bundle is not signed")
        void whenUnsignedBundle_thenFails() throws Exception {
            val bundleFile   = createTestBundle();
            val keyPair      = generateEd25519KeyPair();
            val publicKeyPem = tempDir.resolve("public.pem");
            Files.writeString(publicKeyPem, toPem(keyPair.publicKey(), "PUBLIC KEY"));

            val exitCode = cmd.execute("bundle", "verify", "-b", bundleFile.toString(), "-k", publicKeyPem.toString());

            assertThat(exitCode).isEqualTo(1);
            assertThat(err.toString()).contains("not signed");
        }

    }

    private Path createPolicyInputDir() throws Exception {
        val inputDir = tempDir.resolve("input-" + System.nanoTime());
        Files.createDirectory(inputDir);
        Files.writeString(inputDir.resolve("test.sapl"), TEST_POLICY);
        Files.writeString(inputDir.resolve("pdp.json"), TEST_PDP_JSON);
        return inputDir;
    }

    private Path createTestBundle() throws Exception {
        val inputDir   = createPolicyInputDir();
        val outputFile = tempDir.resolve("bundle-" + System.nanoTime() + ".saplbundle");

        val createOut = new StringWriter();
        val createCmd = new CommandLine(new SaplNodeCli());
        createCmd.setOut(new PrintWriter(createOut));
        createCmd.execute("bundle", "create", "-i", inputDir.toString(), "-o", outputFile.toString());

        return outputFile;
    }

    private Path createSignedTestBundle() throws Exception {
        return createSignedTestBundle(generateEd25519KeyPair().privateKey());
    }

    private Path createSignedTestBundle(PrivateKey privateKey) throws Exception {
        val unsignedBundle = createTestBundle();
        val signedBundle   = tempDir.resolve("signed-" + System.nanoTime() + ".saplbundle");
        val privateKeyPem  = tempDir.resolve("key-" + System.nanoTime() + ".pem");

        Files.writeString(privateKeyPem, toPem(privateKey, "PRIVATE KEY"));

        val signOut = new StringWriter();
        val signCmd = new CommandLine(new SaplNodeCli());
        signCmd.setOut(new PrintWriter(signOut));
        signCmd.execute("bundle", "sign", "-b", unsignedBundle.toString(), "-k", privateKeyPem.toString(), "-o",
                signedBundle.toString(), "--key-id", "test-key");

        return signedBundle;
    }

    private record KeyPairHolder(PrivateKey privateKey, PublicKey publicKey) {}

    private KeyPairHolder generateEd25519KeyPair() throws Exception {
        val keyGen  = KeyPairGenerator.getInstance("Ed25519");
        val keyPair = keyGen.generateKeyPair();
        return new KeyPairHolder(keyPair.getPrivate(), keyPair.getPublic());
    }

    private String toPem(Key key, String type) {
        val base64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(key.getEncoded());
        return "-----BEGIN " + type + "-----\n" + base64 + "\n-----END " + type + "-----\n";
    }

}
