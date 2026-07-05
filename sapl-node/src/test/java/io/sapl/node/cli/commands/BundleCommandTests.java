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
package io.sapl.node.cli.commands;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
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

import io.sapl.node.cli.SaplNodeCli;
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
              "configurationId": "test-v1",
              "algorithm": {
                "votingMode": "PRIORITY_DENY",
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
        void whenDirectoryWithPoliciesThenCreatesBundleSuccessfully() throws Exception {
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
        void whenMultiplePoliciesThenCountsAllPolicies() throws Exception {
            val inputDir   = tempDir.resolve("policies");
            val outputFile = tempDir.resolve("test.saplbundle");

            Files.createDirectory(inputDir);
            Files.writeString(inputDir.resolve("pdp.json"), TEST_PDP_JSON);
            Files.writeString(inputDir.resolve("policy1.sapl"), TEST_POLICY);
            Files.writeString(inputDir.resolve("policy2.sapl"), TEST_POLICY);
            Files.writeString(inputDir.resolve("policy3.sapl"), TEST_POLICY);

            val exitCode = cmd.execute("bundle", "create", "-i", inputDir.toString(), "-o", outputFile.toString());

            assertThat(exitCode).isZero();
            assertThat(out.toString()).contains("3 policies");
        }

        @Test
        @DisplayName("fails when input directory does not exist")
        void whenInputNotExistsThenFails() {
            val inputDir   = tempDir.resolve("nonexistent");
            val outputFile = tempDir.resolve("test.saplbundle");

            val exitCode = cmd.execute("bundle", "create", "-i", inputDir.toString(), "-o", outputFile.toString());

            assertThat(exitCode).isEqualTo(1);
            assertThat(err.toString()).contains("Error").contains("not a directory");
        }

        @Test
        @DisplayName("fails when no policies found")
        void whenNoPoliciesThenFails() throws Exception {
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
        void whenKeyProvidedThenCreatesSignedBundle() throws Exception {
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
        void whenCreatedWithKeyThenPassesVerification() throws Exception {
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
        void whenKeyFileNotFoundThenFails() throws Exception {
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
        void whenUnsignedBundleThenShowsContentsAndUnsignedStatus() throws Exception {
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
        @DisplayName("shows signed bundle metadata and an explicit not-checked integrity state")
        void whenSignedBundleThenShowsSignatureMetadata() throws Exception {
            val bundleFile = createSignedTestBundle();

            val exitCode = cmd.execute("bundle", "inspect", "-b", bundleFile.toString());

            assertThat(exitCode).isZero();
            assertThat(out.toString()).satisfies(output -> {
                assertThat(output).contains("SIGNED");
                assertThat(output).contains("Ed25519");
                assertThat(output).contains("Key ID:");
                assertThat(output).contains("Created:");
                assertThat(output).contains("Integrity: NOT CHECKED (no key provided)");
            });
        }

        @Test
        @DisplayName("verifies integrity when the matching public key is provided")
        void whenKeyProvidedAndSignatureValidThenIntegrityVerified() throws Exception {
            val keyPair    = generateEd25519KeyPair();
            val bundleFile = createSignedTestBundle(keyPair.privateKey());
            val publicKeyF = tempDir.resolve("inspect-public.pem");
            Files.writeString(publicKeyF, toPem(keyPair.publicKey(), "PUBLIC KEY"));

            val exitCode = cmd.execute("bundle", "inspect", "-b", bundleFile.toString(), "-k", publicKeyF.toString());

            assertThat(exitCode).isZero();
            assertThat(out.toString()).contains("Integrity: VERIFIED");
        }

        @Test
        @DisplayName("reports failed integrity and exit code 1 with the wrong public key")
        void whenKeyProvidedAndSignatureInvalidThenIntegrityFailed() throws Exception {
            val bundleFile = createSignedTestBundle();
            val wrongKey   = generateEd25519KeyPair();
            val publicKeyF = tempDir.resolve("inspect-wrong.pem");
            Files.writeString(publicKeyF, toPem(wrongKey.publicKey(), "PUBLIC KEY"));

            val exitCode = cmd.execute("bundle", "inspect", "-b", bundleFile.toString(), "-k", publicKeyF.toString());

            assertThat(exitCode).isEqualTo(1);
            assertThat(out.toString()).contains("Integrity: FAILED");
        }

        @Test
        @DisplayName("reports failed integrity when a key is provided but the bundle is unsigned")
        void whenKeyProvidedAndBundleUnsignedThenIntegrityFailed() throws Exception {
            val bundleFile = createTestBundle();
            val keyPair    = generateEd25519KeyPair();
            val publicKeyF = tempDir.resolve("inspect-unsigned.pem");
            Files.writeString(publicKeyF, toPem(keyPair.publicKey(), "PUBLIC KEY"));

            val exitCode = cmd.execute("bundle", "inspect", "-b", bundleFile.toString(), "-k", publicKeyF.toString());

            assertThat(exitCode).isEqualTo(1);
            assertThat(out.toString()).contains("Integrity: FAILED (bundle is not signed)");
        }

        @Test
        @DisplayName("shows secrets, extensions, and the critical set")
        void whenBundleHasSecretsAndExtensionsThenInspectShowsThem() throws Exception {
            cmd.execute("bundle", "keygen-secrets", "-o", tempDir.resolve("recipient").toString());
            val inputDir = tempDir.resolve("full-inspect");
            Files.createDirectories(inputDir);
            Files.writeString(inputDir.resolve("test.sapl"), TEST_POLICY);
            Files.writeString(inputDir.resolve("pdp.json"), TEST_PDP_JSON);
            Files.writeString(inputDir.resolve("secrets.json"), """
                    { "apiKey": "TOP-SECRET-VALUE" }""");
            Files.writeString(inputDir.resolve("ext-upstreams.json"), """
                    { "servers": [] }""");
            Files.writeString(inputDir.resolve("ext-upstreams-secrets.json"), """
                    { "token": "T" }""");
            Files.writeString(inputDir.resolve("critical-extensions.json"), """
                    ["upstreams"]""");
            val bundleFile = tempDir.resolve("full-inspect.saplbundle");
            cmd.execute("bundle", "create", "-i", inputDir.toString(), "-o", bundleFile.toString(), "--seal-to",
                    tempDir.resolve("recipient.pub.jwk").toString());

            val exitCode = cmd.execute("bundle", "inspect", "-b", bundleFile.toString());

            assertThat(exitCode).isZero();
            assertThat(out.toString()).satisfies(output -> {
                assertThat(output).contains("Secrets:").contains("secrets.sealed.json (sealed");
                assertThat(output).contains("Extensions:").contains("upstreams [critical]").contains("config (")
                        .contains("sealed secrets (");
                assertThat(output).doesNotContain("TOP-SECRET-VALUE");
            });
        }

        @Test
        @DisplayName("shows none markers when a bundle has no secrets or extensions")
        void whenBundleHasNoSecretsOrExtensionsThenInspectShowsNone() throws Exception {
            val bundleFile = createTestBundle();

            val exitCode = cmd.execute("bundle", "inspect", "-b", bundleFile.toString());

            assertThat(exitCode).isZero();
            assertThat(out.toString()).satisfies(output -> {
                assertThat(output).contains("Secrets:").contains("Extensions:").contains("(none)");
            });
        }

        @Test
        @DisplayName("fails when bundle file not found")
        void whenBundleNotFoundThenFails() {
            val bundleFile = tempDir.resolve("nonexistent.saplbundle");

            val exitCode = cmd.execute("bundle", "inspect", "-b", bundleFile.toString());

            assertThat(exitCode).isEqualTo(1);
            assertThat(err.toString()).contains("Bundle file not found");
        }

        @Test
        @DisplayName("fails when the file exists but is not a valid bundle")
        void whenFileIsNotABundleThenFails() throws Exception {
            val notABundle = tempDir.resolve("notabundle.json");
            Files.writeString(notABundle, "{\"not\":\"a bundle\"}");

            val exitCode = cmd.execute("bundle", "inspect", "-b", notABundle.toString());

            assertThat(exitCode).isEqualTo(1);
            assertThat(err.toString()).contains("Not a valid SAPL bundle");
        }

    }

    @Nested
    @DisplayName("bundle sign")
    class SignTests {

        @Test
        @DisplayName("signs unsigned bundle")
        void whenUnsignedBundleThenSignsSuccessfully() throws Exception {
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
        void whenKeyNotFoundThenFails() throws Exception {
            val bundleFile = createTestBundle();
            val keyFile    = tempDir.resolve("nonexistent.pem");

            val exitCode = cmd.execute("bundle", "sign", "-b", bundleFile.toString(), "-k", keyFile.toString());

            assertThat(exitCode).isEqualTo(1);
            assertThat(err.toString()).contains("Key file not found");
        }

    }

    @Nested
    @DisplayName("bundle secrets sealing")
    class SecretsSealingTests {

        @Test
        @DisplayName("keygen-secrets creates an X25519 JWK keypair")
        void whenKeygenSecretsThenCreatesJwkFiles() {
            val prefix   = tempDir.resolve("recipient");
            val exitCode = cmd.execute("bundle", "keygen-secrets", "-o", prefix.toString());
            assertThat(exitCode).isZero();
            assertThat(tempDir.resolve("recipient.jwk")).exists();
            assertThat(tempDir.resolve("recipient.pub.jwk")).exists();
        }

        @Test
        @DisplayName("create --seal-to seals the pdp.json secrets")
        void whenSealToThenSecretsAreSealed() throws Exception {
            cmd.execute("bundle", "keygen-secrets", "-o", tempDir.resolve("recipient").toString());
            val inputDir   = policyDirWithSecrets();
            val outputFile = tempDir.resolve("sealed.saplbundle");

            val exitCode = cmd.execute("bundle", "create", "-i", inputDir.toString(), "-o", outputFile.toString(),
                    "--seal-to", tempDir.resolve("recipient.pub.jwk").toString());

            assertThat(exitCode).isZero();
            assertThat(bundleEntry(outputFile, "secrets.sealed.json")).contains("ENC[")
                    .doesNotContain("TOP-SECRET-VALUE");
            assertThat(bundleEntry(outputFile, "pdp.json")).doesNotContain("ENC[", "TOP-SECRET-VALUE");
        }

        @Test
        @DisplayName("one create command both seals and signs, and the result verifies")
        void whenSealAndSignInOneCommandThenBundleVerifies() throws Exception {
            cmd.execute("bundle", "keygen-secrets", "-o", tempDir.resolve("recipient").toString());
            cmd.execute("bundle", "keygen", "-o", tempDir.resolve("signing").toString());
            val inputDir   = policyDirWithSecrets();
            val outputFile = tempDir.resolve("sealed-signed.saplbundle");

            val createExit = cmd.execute("bundle", "create", "-i", inputDir.toString(), "-o", outputFile.toString(),
                    "-k", tempDir.resolve("signing.pem").toString(), "--seal-to",
                    tempDir.resolve("recipient.pub.jwk").toString());
            val verifyExit = cmd.execute("bundle", "verify", "-b", outputFile.toString(), "-k",
                    tempDir.resolve("signing.pub").toString());

            assertThat(createExit).isZero();
            assertThat(verifyExit).isZero();
        }

        @Test
        @DisplayName("extension files in the input directory are packaged, secrets sealed")
        void whenExtensionFilesThenPackaged() throws Exception {
            cmd.execute("bundle", "keygen-secrets", "-o", tempDir.resolve("recipient").toString());
            val inputDir = policyDirWithSecrets();
            Files.writeString(inputDir.resolve("ext-paratron-gateway.json"), """
                    { "route": "/api" }""");
            Files.writeString(inputDir.resolve("ext-paratron-gateway-secrets.json"), """
                    { "apiKey": "K" }""");
            val outputFile = tempDir.resolve("with-ext.saplbundle");

            val exitCode = cmd.execute("bundle", "create", "-i", inputDir.toString(), "-o", outputFile.toString(),
                    "--seal-to", tempDir.resolve("recipient.pub.jwk").toString());

            assertThat(exitCode).isZero();
            assertThat(bundleEntry(outputFile, "ext-paratron-gateway.json")).contains("route");
            assertThat(bundleEntry(outputFile, "ext-paratron-gateway-secrets.sealed.json")).contains("ENC[");
        }

        @Test
        @DisplayName("a critical-extensions.json in the input directory is packaged")
        void whenCriticalExtensionsFileThenPackaged() throws Exception {
            val inputDir = createPolicyInputDir();
            Files.writeString(inputDir.resolve("ext-upstreams.json"), """
                    { "servers": [] }""");
            Files.writeString(inputDir.resolve("critical-extensions.json"), """
                    ["upstreams"]""");
            val outputFile = tempDir.resolve("with-critical.saplbundle");

            val exitCode = cmd.execute("bundle", "create", "-i", inputDir.toString(), "-o", outputFile.toString());

            assertThat(exitCode).isZero();
            assertThat(bundleEntry(outputFile, "critical-extensions.json")).contains("upstreams");
        }

        @Test
        @DisplayName("create rejects plaintext secrets without --seal-to")
        void whenPlaintextSecretsWithoutSealToThenError() throws Exception {
            val inputDir = policyDirWithSecrets();
            val exitCode = cmd.execute("bundle", "create", "-i", inputDir.toString(), "-o",
                    tempDir.resolve("x.saplbundle").toString());
            assertThat(exitCode).isEqualTo(1);
        }

        @Test
        @DisplayName("unpack extracts every file and drops the manifest, secrets stay sealed")
        void whenUnpackThenFilesExtractedSealed() throws Exception {
            val bundle = sealedAndSignedBundle();
            val outDir = tempDir.resolve("unpacked");

            val exitCode = cmd.execute("bundle", "unpack", "-b", bundle.toString(), "-o", outDir.toString());

            assertThat(exitCode).isZero();
            assertThat(outDir.resolve("test.sapl")).exists();
            assertThat(outDir.resolve(".sapl-manifest.json")).doesNotExist();
            assertThat(Files.readString(outDir.resolve("secrets.sealed.json"))).contains("ENC[")
                    .doesNotContain("TOP-SECRET-VALUE");
        }

        @Test
        @DisplayName("unpack --unseal-with restores plaintext secrets")
        void whenUnpackUnsealThenPlaintext() throws Exception {
            val bundle = sealedAndSignedBundle();
            val outDir = tempDir.resolve("unsealed");

            val exitCode = cmd.execute("bundle", "unpack", "-b", bundle.toString(), "-o", outDir.toString(),
                    "--unseal-with", tempDir.resolve("recipient.jwk").toString());

            assertThat(exitCode).isZero();
            assertThat(Files.readString(outDir.resolve("secrets.json"))).contains("TOP-SECRET-VALUE")
                    .doesNotContain("ENC[");
            assertThat(outDir.resolve("secrets.sealed.json")).doesNotExist();
        }

        @Test
        @DisplayName("a pre-sealed unpacked folder is re-bundled without the sealing key")
        void whenPresealedFolderThenRebundledWithoutKey() throws Exception {
            val presealedDir = tempDir.resolve("presealed");
            cmd.execute("bundle", "unpack", "-b", sealedAndSignedBundle().toString(), "-o", presealedDir.toString());
            val rebundled = tempDir.resolve("rebundled.saplbundle");

            val exitCode = cmd.execute("bundle", "create", "-i", presealedDir.toString(), "-o", rebundled.toString());

            assertThat(exitCode).isZero();
            assertThat(bundleEntry(rebundled, "secrets.sealed.json")).contains("ENC[")
                    .doesNotContain("TOP-SECRET-VALUE");
        }

        @Test
        @DisplayName("create --seal-to on an already-sealed folder is rejected")
        void whenSealToOnPresealedFolderThenError() throws Exception {
            val presealedDir = tempDir.resolve("presealed-reject");
            cmd.execute("bundle", "unpack", "-b", sealedAndSignedBundle().toString(), "-o", presealedDir.toString());

            val exitCode = cmd.execute("bundle", "create", "-i", presealedDir.toString(), "-o",
                    tempDir.resolve("x.saplbundle").toString(), "--seal-to",
                    tempDir.resolve("recipient.pub.jwk").toString());
            assertThat(exitCode).isEqualTo(1);
        }

        @Test
        @DisplayName("create rejects a folder that mixes sealed and plaintext secrets")
        void whenMixedSealingThenError() throws Exception {
            val dir = tempDir.resolve("mixed");
            cmd.execute("bundle", "unpack", "-b", sealedAndSignedBundle().toString(), "-o", dir.toString());
            Files.writeString(dir.resolve("ext-foo.json"), "{}");
            Files.writeString(dir.resolve("ext-foo-secrets.json"), """
                    { "k": "plaintext" }""");

            val exitCode = cmd.execute("bundle", "create", "-i", dir.toString(), "-o",
                    tempDir.resolve("x.saplbundle").toString());
            assertThat(exitCode).isEqualTo(1);
        }

        @Test
        @DisplayName("sign preserves extension files and the critical set, and the result verifies")
        void whenSignPreservesExtensionsAndCritical() throws Exception {
            cmd.execute("bundle", "keygen-secrets", "-o", tempDir.resolve("recipient").toString());
            cmd.execute("bundle", "keygen", "-o", tempDir.resolve("signing").toString());
            val inputDir = policyDirWithSecrets();
            Files.writeString(inputDir.resolve("ext-upstreams.json"), """
                    { "servers": [] }""");
            Files.writeString(inputDir.resolve("ext-upstreams-secrets.json"), """
                    { "apiKey": "K" }""");
            Files.writeString(inputDir.resolve("critical-extensions.json"), """
                    ["upstreams"]""");
            val bundle = tempDir.resolve("unsigned.saplbundle");
            cmd.execute("bundle", "create", "-i", inputDir.toString(), "-o", bundle.toString(), "--seal-to",
                    tempDir.resolve("recipient.pub.jwk").toString());

            val signExit   = cmd.execute("bundle", "sign", "-b", bundle.toString(), "-k",
                    tempDir.resolve("signing.pem").toString(), "--key-id", "prod");
            val verifyExit = cmd.execute("bundle", "verify", "-b", bundle.toString(), "-k",
                    tempDir.resolve("signing.pub").toString());

            assertThat(signExit).isZero();
            assertThat(verifyExit).isZero();
            assertThat(bundleEntry(bundle, "ext-upstreams.json")).contains("servers");
            assertThat(bundleEntry(bundle, "ext-upstreams-secrets.sealed.json")).contains("ENC[");
            assertThat(bundleEntry(bundle, "critical-extensions.json")).contains("upstreams");
        }

        @Test
        @DisplayName("seal converts plaintext secrets files to sealed files and deletes the plaintext")
        void whenSealCommandThenFolderSealed() throws Exception {
            cmd.execute("bundle", "keygen-secrets", "-o", tempDir.resolve("recipient").toString());
            val inputDir = policyDirWithSecrets();
            Files.writeString(inputDir.resolve("ext-upstreams.json"), "{}");
            Files.writeString(inputDir.resolve("ext-upstreams-secrets.json"), """
                    { "apiKey": "K" }""");

            val exitCode = cmd.execute("bundle", "seal", "-i", inputDir.toString(), "--to",
                    tempDir.resolve("recipient.pub.jwk").toString());

            assertThat(exitCode).isZero();
            assertThat(inputDir.resolve("secrets.json")).doesNotExist();
            assertThat(inputDir.resolve("ext-upstreams-secrets.json")).doesNotExist();
            assertThat(Files.readString(inputDir.resolve("secrets.sealed.json"))).contains("ENC[")
                    .doesNotContain("TOP-SECRET-VALUE");
            assertThat(Files.readString(inputDir.resolve("ext-upstreams-secrets.sealed.json"))).contains("ENC[")
                    .doesNotContain("\"K\"");
        }

        @Test
        @DisplayName("unseal restores the plaintext secrets files and deletes the sealed ones")
        void whenUnsealCommandThenFolderPlaintext() throws Exception {
            cmd.execute("bundle", "keygen-secrets", "-o", tempDir.resolve("recipient").toString());
            val inputDir = policyDirWithSecrets();
            cmd.execute("bundle", "seal", "-i", inputDir.toString(), "--to",
                    tempDir.resolve("recipient.pub.jwk").toString());

            val exitCode = cmd.execute("bundle", "unseal", "-i", inputDir.toString(), "--with",
                    tempDir.resolve("recipient.jwk").toString());

            assertThat(exitCode).isZero();
            assertThat(inputDir.resolve("secrets.sealed.json")).doesNotExist();
            assertThat(Files.readString(inputDir.resolve("secrets.json"))).contains("TOP-SECRET-VALUE")
                    .doesNotContain("ENC[");
        }

        @Test
        @DisplayName("the full maintenance loop works: unpack, unseal, edit, seal, create, verify")
        void whenFullMaintenanceLoopThenBundleVerifies() throws Exception {
            val workDir = tempDir.resolve("maintenance");
            cmd.execute("bundle", "unpack", "-b", sealedAndSignedBundle().toString(), "-o", workDir.toString(),
                    "--unseal-with", tempDir.resolve("recipient.jwk").toString());

            Files.writeString(workDir.resolve("secrets.json"), """
                    { "http": { "headers": { "X-API-Key": "ROTATED-VALUE" } } }
                    """);
            val sealExit   = cmd.execute("bundle", "seal", "-i", workDir.toString(), "--to",
                    tempDir.resolve("recipient.pub.jwk").toString());
            val rebuilt    = tempDir.resolve("maintained.saplbundle");
            val createExit = cmd.execute("bundle", "create", "-i", workDir.toString(), "-o", rebuilt.toString(), "-k",
                    tempDir.resolve("signing.pem").toString());
            val verifyExit = cmd.execute("bundle", "verify", "-b", rebuilt.toString(), "-k",
                    tempDir.resolve("signing.pub").toString());

            assertThat(sealExit).isZero();
            assertThat(createExit).isZero();
            assertThat(verifyExit).isZero();
            assertThat(bundleEntry(rebuilt, "secrets.sealed.json")).contains("ENC[").doesNotContain("ROTATED-VALUE");
        }

        private Path sealedAndSignedBundle() throws Exception {
            cmd.execute("bundle", "keygen-secrets", "-o", tempDir.resolve("recipient").toString());
            cmd.execute("bundle", "keygen", "-o", tempDir.resolve("signing").toString());
            val inputDir = policyDirWithSecrets();
            val bundle   = tempDir.resolve("sealed-signed.saplbundle");
            cmd.execute("bundle", "create", "-i", inputDir.toString(), "-o", bundle.toString(), "-k",
                    tempDir.resolve("signing.pem").toString(), "--seal-to",
                    tempDir.resolve("recipient.pub.jwk").toString());
            return bundle;
        }

        private Path policyDirWithSecrets() throws IOException {
            val inputDir = tempDir.resolve("policies-secrets");
            Files.createDirectories(inputDir);
            Files.writeString(inputDir.resolve("test.sapl"), TEST_POLICY);
            Files.writeString(inputDir.resolve("pdp.json"), TEST_PDP_JSON);
            Files.writeString(inputDir.resolve("secrets.json"), """
                    { "http": { "headers": { "X-API-Key": "TOP-SECRET-VALUE" } } }
                    """);
            return inputDir;
        }

        private String bundleEntry(Path bundle, String entryName) throws IOException {
            try (val zip = new ZipInputStream(Files.newInputStream(bundle))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (entry.getName().equals(entryName)) {
                        return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                    }
                }
            }
            return "";
        }
    }

    @Nested
    @DisplayName("bundle keygen")
    class KeygenTests {

        @Test
        @DisplayName("generates Ed25519 keypair")
        void whenValidOutputThenGeneratesKeypair() {
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
        void whenGeneratedThenKeysHaveValidPemFormat() throws Exception {
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
        void whenGeneratedKeysThenSignAndVerifyWorks() throws Exception {
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
        void whenFileExistsThenFailsWithoutForce() throws Exception {
            val outputPrefix = tempDir.resolve("existing");
            Files.writeString(tempDir.resolve("existing.pem"), "existing content");

            val exitCode = cmd.execute("bundle", "keygen", "-o", outputPrefix.toString());

            assertThat(exitCode).isEqualTo(1);
            assertThat(err.toString()).contains("File already exists").contains("--force");
        }

        @Test
        @DisplayName("overwrites existing files with --force")
        void whenForceFlagThenOverwritesExistingFiles() throws Exception {
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
        void whenValidSignatureThenVerifiesSuccessfully() throws Exception {
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
        void whenWrongKeyThenVerificationFails() throws Exception {
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
        void whenUnsignedBundleThenFails() throws Exception {
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
