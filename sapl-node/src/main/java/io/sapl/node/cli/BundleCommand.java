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
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
@Command(name = "bundle", description = "Policy bundle operations", mixinStandardHelpOptions = true, subcommands = {
        BundleCommand.Create.class, BundleCommand.Sign.class, BundleCommand.Verify.class, BundleCommand.Inspect.class,
        BundleCommand.Keygen.class })
class BundleCommand {

    private static final String PDP_JSON       = "pdp.json";
    private static final String SAPL_EXTENSION = ".sapl";

    private static final String PEM_PRIVATE_KEY_BEGIN = "-----BEGIN PRIVATE KEY-----";
    private static final String PEM_PRIVATE_KEY_END   = "-----END PRIVATE KEY-----";
    private static final String PEM_PUBLIC_KEY_BEGIN  = "-----BEGIN PUBLIC KEY-----";
    private static final String PEM_PUBLIC_KEY_END    = "-----END PUBLIC KEY-----";

    private static final String ERROR_BUNDLE_NOT_FOUND    = "Error: Bundle file not found: ";
    private static final String ERROR_BUNDLE_NOT_SIGNED   = "Error: Bundle is not signed (no manifest found)";
    private static final String ERROR_CREATING_BUNDLE     = "Error creating bundle: ";
    private static final String ERROR_FILE_ALREADY_EXISTS = "Error: File already exists: ";
    private static final String ERROR_GENERATING_KEYPAIR  = "Error generating keypair: ";
    private static final String ERROR_INSPECTING_BUNDLE   = "Error inspecting bundle: ";
    private static final String ERROR_KEY_NOT_FOUND       = "Error: Key file not found: ";
    private static final String ERROR_NO_POLICIES_FOUND   = "Error: No .sapl files found in: ";
    private static final String ERROR_NOT_A_DIRECTORY     = "Error: Input path is not a directory: ";
    private static final String ERROR_SIGNING_BUNDLE      = "Error signing bundle: ";
    private static final String ERROR_VERIFICATION_FAILED = "Verification FAILED: ";
    private static final String ERROR_VERIFYING_BUNDLE    = "Error verifying bundle: ";
    private static final String HINT_USE_FORCE            = "Use --force to overwrite";

    @Command(name = "create", description = "Create a policy bundle from a directory", mixinStandardHelpOptions = true)
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
                err.println(ERROR_NOT_A_DIRECTORY + inputDir);
                return 1;
            }

            if (keyFile != null && !Files.exists(keyFile)) {
                err.println(ERROR_KEY_NOT_FOUND + keyFile);
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
                    err.println(ERROR_NO_POLICIES_FOUND + inputDir);
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
                err.println(ERROR_CREATING_BUNDLE + e.getMessage());
                return 1;
            }
        }

    }

    @Command(name = "sign", description = "Sign a policy bundle", mixinStandardHelpOptions = true)
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
                err.println(ERROR_BUNDLE_NOT_FOUND + bundleFile);
                return 1;
            }

            if (!Files.exists(keyFile)) {
                err.println(ERROR_KEY_NOT_FOUND + keyFile);
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
                err.println(ERROR_SIGNING_BUNDLE + e.getMessage());
                return 1;
            }
        }

    }

    @Command(name = "verify", description = "Verify a signed policy bundle", mixinStandardHelpOptions = true)
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
                err.println(ERROR_BUNDLE_NOT_FOUND + bundleFile);
                return 1;
            }

            if (!Files.exists(keyFile)) {
                err.println(ERROR_KEY_NOT_FOUND + keyFile);
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
                err.println(ERROR_VERIFICATION_FAILED + e.getMessage());
                return 1;
            } catch (IOException | GeneralSecurityException e) {
                err.println(ERROR_VERIFYING_BUNDLE + e.getMessage());
                return 1;
            }
        }

    }

    @Command(name = "inspect", description = "Show bundle contents and metadata", mixinStandardHelpOptions = true)
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
                err.println(ERROR_BUNDLE_NOT_FOUND + bundleFile);
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
                err.println(ERROR_INSPECTING_BUNDLE + e.getMessage());
                return 1;
            }
        }

    }

    @Command(name = "keygen", description = "Generate Ed25519 keypair for bundle signing", mixinStandardHelpOptions = true)
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
                err.println(ERROR_FILE_ALREADY_EXISTS + privateKey);
                err.println(HINT_USE_FORCE);
                return 1;
            }

            if (!force && Files.exists(publicKey)) {
                err.println(ERROR_FILE_ALREADY_EXISTS + publicKey);
                err.println(HINT_USE_FORCE);
                return 1;
            }

            try {
                val keyPairGenerator = KeyPairGenerator.getInstance("Ed25519");
                val keyPair          = keyPairGenerator.generateKeyPair();

                val privatePem = encodePem(keyPair.getPrivate().getEncoded(), PEM_PRIVATE_KEY_BEGIN,
                        PEM_PRIVATE_KEY_END);
                val publicPem  = encodePem(keyPair.getPublic().getEncoded(), PEM_PUBLIC_KEY_BEGIN, PEM_PUBLIC_KEY_END);

                Files.writeString(privateKey, privatePem);
                Files.writeString(publicKey, publicPem);

                out.println("Generated Ed25519 keypair:");
                out.printf("  Private key: %s%n", privateKey);
                out.printf("  Public key:  %s%n", publicKey);
                return 0;

            } catch (IOException | GeneralSecurityException e) {
                err.println(ERROR_GENERATING_KEYPAIR + e.getMessage());
                return 1;
            }
        }

    }

    private static String encodePem(byte[] keyBytes, String beginMarker, String endMarker) {
        val encoded = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(keyBytes);
        return beginMarker + "\n" + encoded + "\n" + endMarker + "\n";
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
        val keyBytes = loadPemKeyBytes(keyFile, PEM_PRIVATE_KEY_BEGIN, PEM_PRIVATE_KEY_END);
        return (PrivateKey) loadKey(new PKCS8EncodedKeySpec(keyBytes), true);
    }

    private static PublicKey loadEd25519PublicKey(Path keyFile) throws IOException, GeneralSecurityException {
        val keyBytes = loadPemKeyBytes(keyFile, PEM_PUBLIC_KEY_BEGIN, PEM_PUBLIC_KEY_END);
        return (PublicKey) loadKey(new X509EncodedKeySpec(keyBytes), false);
    }

    private static byte[] loadPemKeyBytes(Path keyFile, String beginMarker, String endMarker) throws IOException {
        val pemContent = Files.readString(keyFile).trim();
        val base64Key  = pemContent.replace(beginMarker, "").replace(endMarker, "").replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64Key);
    }

    private static Key loadKey(KeySpec keySpec, boolean isPrivate) throws GeneralSecurityException {
        val keyFactory = KeyFactory.getInstance("Ed25519");
        return isPrivate ? keyFactory.generatePrivate(keySpec) : keyFactory.generatePublic(keySpec);
    }

}
