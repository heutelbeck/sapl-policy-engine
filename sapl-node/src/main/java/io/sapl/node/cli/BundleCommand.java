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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static io.sapl.functions.libraries.crypto.CryptoConstants.ALGORITHM_ED25519;

import io.sapl.functions.libraries.crypto.PemUtils;
import io.sapl.pdp.configuration.bundle.BundleBuilder;
import io.sapl.pdp.configuration.bundle.BundleManifest;
import io.sapl.pdp.configuration.bundle.BundleSignatureException;
import io.sapl.pdp.configuration.bundle.BundleSigner;
import lombok.val;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * Bundle operations for policy management.
 * <p>
 * These commands run without starting Spring Boot for fast execution.
 */
// @formatter:off
@Command(
    name = "bundle",
    mixinStandardHelpOptions = true,
    header = "Manage policy bundles for deployment.",
    description = { """
        Bundles package SAPL policies and PDP configuration into a single
        .saplbundle file. They can be cryptographically signed with
        Ed25519 keys for integrity verification at load time.
        """ },
    subcommands = {
        BundleCommand.Create.class, BundleCommand.Sign.class,
        BundleCommand.Verify.class, BundleCommand.Inspect.class,
        BundleCommand.Keygen.class
    }
)
// @formatter:on
class BundleCommand {

    private static final String PDP_JSON       = "pdp.json";
    private static final String SAPL_EXTENSION = ".sapl";

    static final String         ERROR_BUNDLE_NOT_FOUND    = "Error: Bundle file not found: %s.";
    private static final String ERROR_BUNDLE_NOT_SIGNED   = "Error: Bundle is not signed (no manifest found).";
    private static final String ERROR_CREATING_BUNDLE     = "Error creating bundle: %s.";
    private static final String ERROR_FILE_ALREADY_EXISTS = "Error: File already exists: %s.";
    private static final String ERROR_GENERATING_KEYPAIR  = "Error generating keypair: %s.";
    private static final String ERROR_INSPECTING_BUNDLE   = "Error inspecting bundle: %s.";
    private static final String ERROR_KEY_NOT_FOUND       = "Error: Key file not found: %s.";
    private static final String ERROR_NO_POLICIES_FOUND   = "Error: No .sapl files found in: %s.";
    private static final String ERROR_NOT_A_DIRECTORY     = "Error: Input path is not a directory: %s.";
    private static final String ERROR_SIGNING_BUNDLE      = "Error signing bundle: %s.";
    private static final String ERROR_VERIFICATION_FAILED = "Verification FAILED: %s.";
    private static final String ERROR_VERIFYING_BUNDLE    = "Error verifying bundle: %s.";
    private static final String HINT_USE_FORCE            = "Use --force to overwrite.";

    // @formatter:off
    @Command(
        name = "create",
        mixinStandardHelpOptions = true,
        header = "Create a policy bundle from a directory.",
        description = { """
            Packages all .sapl policy files and pdp.json from the input
            directory into a .saplbundle file. Policies are validated for
            correct SAPL syntax during creation.

            Optionally signs the bundle when a private key is provided.
            This is equivalent to creating then running 'sapl bundle sign'.
            """ },
        exitCodeListHeading = "%nExit Codes:%n",
        exitCodeList = {
            " 0:Bundle created successfully",
            " 1:Error (invalid input, no policies found, or I/O error)"
        },
        footerHeading = "%nExamples:%n",
        footer = { """
              # Create an unsigned bundle
              sapl bundle create -i ./policies -o policies.saplbundle

              # Create and sign in one step
              sapl bundle create -i ./policies -o policies.saplbundle -k signing.pem --key-id prod-2026

            See Also: sapl-bundle-sign(1), sapl-bundle-keygen(1)
            """ }
    )
    // @formatter:on
    static class Create implements Callable<Integer> {

        @Spec
        private CommandSpec spec;

        @Option(names = { "-i", "--input" }, required = true, description = "Input directory containing policies")
        private Path inputDir;

        @Option(names = { "-k", "--key" }, description = "Ed25519 private key file (PEM format) for signing")
        private Path keyFile;

        @Option(names = { "--key-id" }, description = "Key identifier for rotation support", defaultValue = "default")
        private String keyId;

        @Option(names = { "-o", "--output" }, required = true, description = "Output bundle file path")
        private Path outputFile;

        @Override
        public Integer call() {
            val out = spec.commandLine().getOut();
            val err = spec.commandLine().getErr();

            if (!Files.isDirectory(inputDir)) {
                err.println(ERROR_NOT_A_DIRECTORY.formatted(inputDir));
                return 1;
            }

            if (keyFile != null && !Files.exists(keyFile)) {
                err.println(ERROR_KEY_NOT_FOUND.formatted(keyFile));
                return 1;
            }

            try {
                val builder  = BundleBuilder.create();
                val pdpJson  = inputDir.resolve(PDP_JSON);
                var policies = 0;

                if (Files.exists(pdpJson)) {
                    builder.withPdpJson(Files.readString(pdpJson));
                }

                try (val stream = Files.list(inputDir)) {
                    for (val file : stream.filter(p -> p.toString().endsWith(SAPL_EXTENSION)).toList()) {
                        val filename = file.getFileName().toString();
                        val content  = Files.readString(file);
                        builder.withPolicy(filename, content);
                        policies++;
                    }
                }

                if (policies == 0) {
                    err.println(ERROR_NO_POLICIES_FOUND.formatted(inputDir));
                    return 1;
                }

                if (keyFile != null) {
                    val privateKey = loadEd25519PrivateKey(keyFile);
                    builder.signWith(privateKey, keyId);
                }

                builder.writeTo(outputFile);

                if (keyFile != null) {
                    out.printf("Created signed bundle: %s (%d policies, key-id: %s)%n", outputFile, policies, keyId);
                } else {
                    out.printf("Created bundle: %s (%d policies)%n", outputFile, policies);
                }
                return 0;

            } catch (IOException | GeneralSecurityException e) {
                err.println(ERROR_CREATING_BUNDLE.formatted(e.getMessage()));
                return 1;
            }
        }

    }

    // @formatter:off
    @Command(
        name = "sign",
        mixinStandardHelpOptions = true,
        header = "Sign a policy bundle with an Ed25519 private key.",
        description = { """
            Creates a manifest containing SHA-256 hashes of all files in
            the bundle and signs it with the provided Ed25519 private key.
            The signature enables the PDP server to verify bundle integrity
            and authenticity at load time.

            By default, the input bundle is overwritten with the signed
            version. Use -o to write to a different file.
            """ },
        exitCodeListHeading = "%nExit Codes:%n",
        exitCodeList = {
            " 0:Bundle signed successfully",
            " 1:Error (bundle or key not found, or signing failed)"
        },
        footerHeading = "%nExamples:%n",
        footer = { """
              # Sign a bundle (overwrites the original)
              sapl bundle sign -b policies.saplbundle -k signing.pem

              # Sign and write to a new file
              sapl bundle sign -b policies.saplbundle -k signing.pem -o signed.saplbundle --key-id prod-2026

            See Also: sapl-bundle-keygen(1), sapl-bundle-verify(1)
            """ }
    )
    // @formatter:on
    static class Sign implements Callable<Integer> {

        @Spec
        private CommandSpec spec;

        @Option(names = { "-b", "--bundle" }, required = true, description = "Bundle file to sign")
        private Path bundleFile;

        @Option(names = { "-k", "--key" }, required = true, description = "Ed25519 private key file (PEM format)")
        private Path keyFile;

        @Option(names = { "--key-id" }, description = "Key identifier for rotation support", defaultValue = "default")
        private String keyId;

        @Option(names = { "-o", "--output" }, description = "Output file (default: overwrites input)")
        private Path outputFile;

        @Override
        public Integer call() {
            val out = spec.commandLine().getOut();
            val err = spec.commandLine().getErr();

            if (!Files.exists(bundleFile)) {
                err.println(ERROR_BUNDLE_NOT_FOUND.formatted(bundleFile));
                return 1;
            }

            if (!Files.exists(keyFile)) {
                err.println(ERROR_KEY_NOT_FOUND.formatted(keyFile));
                return 1;
            }

            try {
                val privateKey = loadEd25519PrivateKey(keyFile);
                val contents   = extractBundleContents(bundleFile);
                val builder    = BundleBuilder.create();

                val pdpJson = contents.remove(PDP_JSON);
                if (pdpJson != null) {
                    builder.withPdpJson(pdpJson);
                }

                contents.remove(BundleManifest.MANIFEST_FILENAME);

                for (val entry : contents.entrySet()) {
                    if (entry.getKey().endsWith(SAPL_EXTENSION)) {
                        builder.withPolicy(entry.getKey(), entry.getValue());
                    }
                }

                builder.signWith(privateKey, keyId);

                val target = outputFile != null ? outputFile : bundleFile;
                builder.writeTo(target);

                out.printf("Signed bundle: %s (key-id: %s)%n", target, keyId);
                return 0;

            } catch (IOException | GeneralSecurityException e) {
                err.println(ERROR_SIGNING_BUNDLE.formatted(e.getMessage()));
                return 1;
            }
        }

    }

    // @formatter:off
    @Command(
        name = "verify",
        mixinStandardHelpOptions = true,
        header = "Verify a signed policy bundle against an Ed25519 public key.",
        description = { """
            Validates the bundle's Ed25519 signature and checks SHA-256
            hashes of all files against the manifest. Reports the key ID,
            creation timestamp, and number of verified files on success.
            """ },
        exitCodeListHeading = "%nExit Codes:%n",
        exitCodeList = {
            " 0:Verification successful",
            " 1:Verification failed, bundle not signed, or error"
        },
        footerHeading = "%nExamples:%n",
        footer = { """
              # Verify a signed bundle
              sapl bundle verify -b policies.saplbundle -k signing.pub

            See Also: sapl-bundle-sign(1), sapl-bundle-inspect(1)
            """ }
    )
    // @formatter:on
    static class Verify implements Callable<Integer> {

        @Spec
        private CommandSpec spec;

        @Option(names = { "-b", "--bundle" }, required = true, description = "Bundle file to verify")
        private Path bundleFile;

        @Option(names = { "-k", "--key" }, required = true, description = "Ed25519 public key file (PEM format)")
        private Path keyFile;

        @Override
        public Integer call() {
            val out = spec.commandLine().getOut();
            val err = spec.commandLine().getErr();

            if (!Files.exists(bundleFile)) {
                err.println(ERROR_BUNDLE_NOT_FOUND.formatted(bundleFile));
                return 1;
            }

            if (!Files.exists(keyFile)) {
                err.println(ERROR_KEY_NOT_FOUND.formatted(keyFile));
                return 1;
            }

            try {
                val publicKey = loadEd25519PublicKey(keyFile);
                val contents  = extractBundleContents(bundleFile);

                val manifestJson = contents.remove(BundleManifest.MANIFEST_FILENAME);
                if (manifestJson == null) {
                    err.println(ERROR_BUNDLE_NOT_SIGNED);
                    return 1;
                }

                val manifest = BundleManifest.fromJson(manifestJson);

                BundleSigner.verify(manifest, contents, publicKey);

                out.println("Verification successful");
                out.printf("  Key ID: %s%n", manifest.signature().keyId());
                out.printf("  Created: %s%n", manifest.created());
                out.printf("  Files verified: %d%n", manifest.files().size());
                return 0;

            } catch (BundleSignatureException e) {
                err.println(ERROR_VERIFICATION_FAILED.formatted(e.getMessage()));
                return 1;
            } catch (IOException | GeneralSecurityException e) {
                err.println(ERROR_VERIFYING_BUNDLE.formatted(e.getMessage()));
                return 1;
            }
        }

    }

    // @formatter:off
    @Command(
        name = "inspect",
        mixinStandardHelpOptions = true,
        header = "Show bundle contents and metadata.",
        description = { """
            Displays the signature status, PDP configuration (pdp.json),
            and a list of all policies with their sizes. Useful for
            auditing bundles before deployment.
            """ },
        exitCodeListHeading = "%nExit Codes:%n",
        exitCodeList = {
            " 0:Inspection completed",
            " 1:Error reading bundle"
        },
        footerHeading = "%nExamples:%n",
        footer = { """
              # Show bundle contents and signature status
              sapl bundle inspect -b policies.saplbundle

            See Also: sapl-bundle-verify(1)
            """ }
    )
    // @formatter:on
    static class Inspect implements Callable<Integer> {

        @Spec
        private CommandSpec spec;

        @Option(names = { "-b", "--bundle" }, required = true, description = "Bundle file to inspect")
        private Path bundleFile;

        @Override
        public Integer call() {
            val out = spec.commandLine().getOut();
            val err = spec.commandLine().getErr();

            if (!Files.exists(bundleFile)) {
                err.println(ERROR_BUNDLE_NOT_FOUND.formatted(bundleFile));
                return 1;
            }

            try {
                val contents = extractBundleContents(bundleFile);

                out.printf("Bundle: %s%n", bundleFile.getFileName());
                out.println();

                val manifestJson = contents.get(BundleManifest.MANIFEST_FILENAME);
                if (manifestJson != null) {
                    val manifest = BundleManifest.fromJson(manifestJson);
                    out.println("Signature:");
                    out.printf("  Status: SIGNED%n");
                    out.printf("  Algorithm: %s%n", manifest.signature().algorithm());
                    out.printf("  Key ID: %s%n", manifest.signature().keyId());
                    out.printf("  Created: %s%n", manifest.created());
                } else {
                    out.println("Signature:");
                    out.printf("  Status: UNSIGNED%n");
                }
                out.println();

                val pdpJson = contents.get(PDP_JSON);
                if (pdpJson != null) {
                    out.println("Configuration (pdp.json):");
                    out.print(pdpJson.trim().indent(2));
                } else {
                    out.println("Configuration: (none)");
                }
                out.println();

                out.println("Policies:");
                var policyCount = 0;
                for (val entry : contents.entrySet()) {
                    if (entry.getKey().endsWith(SAPL_EXTENSION)) {
                        out.printf("  - %s (%d bytes)%n", entry.getKey(), entry.getValue().length());
                        policyCount++;
                    }
                }
                if (policyCount == 0) {
                    out.println("  (none)");
                }

                return 0;

            } catch (IOException e) {
                err.println(ERROR_INSPECTING_BUNDLE.formatted(e.getMessage()));
                return 1;
            }
        }

    }

    // @formatter:off
    @Command(
        name = "keygen",
        mixinStandardHelpOptions = true,
        header = "Generate an Ed25519 keypair for bundle signing.",
        description = { """
            Creates a PKCS#8 PEM-encoded private key (<prefix>.pem) and
            an X.509 PEM-encoded public key (<prefix>.pub). The private
            key is used with 'sapl bundle sign' or 'sapl bundle create
            -k'. The public key is configured on the PDP server to verify
            bundle signatures.
            """ },
        exitCodeListHeading = "%nExit Codes:%n",
        exitCodeList = {
            " 0:Keypair generated",
            " 1:Error (file exists without --force, or generation failed)"
        },
        footerHeading = "%nExamples:%n",
        footer = { """
              # Generate a new signing keypair
              sapl bundle keygen -o signing-key

              # Overwrite existing key files
              sapl bundle keygen -o signing-key --force

            See Also: sapl-bundle-sign(1), sapl-bundle-create(1)
            """ }
    )
    // @formatter:on
    static class Keygen implements Callable<Integer> {

        @Spec
        private CommandSpec spec;

        @Option(names = { "-o",
                "--output" }, required = true, description = "Output file prefix (creates <prefix>.pem and <prefix>.pub)")
        private Path outputPrefix;

        @Option(names = { "--force" }, description = "Overwrite existing files")
        private boolean force;

        @Override
        public Integer call() {
            val out        = spec.commandLine().getOut();
            val err        = spec.commandLine().getErr();
            val privateKey = outputPrefix.resolveSibling(outputPrefix.getFileName() + ".pem");
            val publicKey  = outputPrefix.resolveSibling(outputPrefix.getFileName() + ".pub");

            if (!force && Files.exists(privateKey)) {
                err.println(ERROR_FILE_ALREADY_EXISTS.formatted(privateKey));
                err.println(HINT_USE_FORCE);
                return 1;
            }

            if (!force && Files.exists(publicKey)) {
                err.println(ERROR_FILE_ALREADY_EXISTS.formatted(publicKey));
                err.println(HINT_USE_FORCE);
                return 1;
            }

            try {
                val keyPairGenerator = KeyPairGenerator.getInstance(ALGORITHM_ED25519);
                val keyPair          = keyPairGenerator.generateKeyPair();

                PemUtils.writeKeyToFile(privateKey, keyPair.getPrivate());
                PemUtils.writeKeyToFile(publicKey, keyPair.getPublic());

                out.println("Generated Ed25519 keypair:");
                out.printf("  Private key: %s%n", privateKey);
                out.printf("  Public key:  %s%n", publicKey);
                return 0;

            } catch (IOException | GeneralSecurityException e) {
                err.println(ERROR_GENERATING_KEYPAIR.formatted(e.getMessage()));
                return 1;
            }
        }

    }

    private static Map<String, String> extractBundleContents(Path bundlePath) throws IOException {
        val contents = new HashMap<String, String>();
        try (val zipStream = new ZipInputStream(Files.newInputStream(bundlePath))) {
            ZipEntry entry;
            while ((entry = zipStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                val name    = entry.getName();
                val content = new String(zipStream.readAllBytes(), StandardCharsets.UTF_8);
                contents.put(name, content);
            }
        }
        return contents;
    }

    private static PrivateKey loadEd25519PrivateKey(Path keyFile) throws IOException, GeneralSecurityException {
        val keyBytes = PemUtils.decodePemFromFile(keyFile);
        return KeyFactory.getInstance(ALGORITHM_ED25519).generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
    }

    private static PublicKey loadEd25519PublicKey(Path keyFile) throws IOException, GeneralSecurityException {
        val keyBytes = PemUtils.decodePemFromFile(keyFile);
        return KeyFactory.getInstance(ALGORITHM_ED25519).generatePublic(new X509EncodedKeySpec(keyBytes));
    }

}
