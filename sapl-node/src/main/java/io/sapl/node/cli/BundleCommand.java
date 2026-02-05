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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

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
@Command(name = "bundle", description = "Policy bundle operations", subcommands = { BundleCommand.Create.class,
        BundleCommand.Sign.class, BundleCommand.Verify.class, BundleCommand.Inspect.class })
class BundleCommand {

    private static final String PDP_JSON       = "pdp.json";
    private static final String SAPL_EXTENSION = ".sapl";

    @Command(name = "create", description = "Create a policy bundle from a directory")
    static class Create implements Callable<Integer> {

        @Spec
        CommandSpec spec;

        @Option(names = { "-i", "--input" }, required = true, description = "Input directory containing policies")
        Path inputDir;

        @Option(names = { "-o", "--output" }, required = true, description = "Output bundle file path")
        Path outputFile;

        @Override
        public Integer call() {
            val out = spec.commandLine().getOut();
            val err = spec.commandLine().getErr();

            if (!Files.isDirectory(inputDir)) {
                err.println("Error: Input path is not a directory: " + inputDir);
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
                    err.println("Error: No .sapl files found in: " + inputDir);
                    return 1;
                }

                builder.writeTo(outputFile);
                out.printf("Created bundle: %s (%d policies)%n", outputFile, policies);
                return 0;

            } catch (IOException e) {
                err.println("Error creating bundle: " + e.getMessage());
                return 1;
            }
        }

    }

    @Command(name = "sign", description = "Sign a policy bundle")
    static class Sign implements Callable<Integer> {

        @Spec
        CommandSpec spec;

        @Option(names = { "-b", "--bundle" }, required = true, description = "Bundle file to sign")
        Path bundleFile;

        @Option(names = { "-k", "--key" }, required = true, description = "Ed25519 private key file (PEM format)")
        Path keyFile;

        @Option(names = { "--key-id" }, description = "Key identifier for rotation support", defaultValue = "default")
        String keyId;

        @Option(names = { "-o", "--output" }, description = "Output file (default: overwrites input)")
        Path outputFile;

        @Override
        public Integer call() {
            val out = spec.commandLine().getOut();
            val err = spec.commandLine().getErr();

            if (!Files.exists(bundleFile)) {
                err.println("Error: Bundle file not found: " + bundleFile);
                return 1;
            }

            if (!Files.exists(keyFile)) {
                err.println("Error: Key file not found: " + keyFile);
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

            } catch (Exception e) {
                err.println("Error signing bundle: " + e.getMessage());
                return 1;
            }
        }

    }

    @Command(name = "verify", description = "Verify a signed policy bundle")
    static class Verify implements Callable<Integer> {

        @Spec
        CommandSpec spec;

        @Option(names = { "-b", "--bundle" }, required = true, description = "Bundle file to verify")
        Path bundleFile;

        @Option(names = { "-k", "--key" }, required = true, description = "Ed25519 public key file (PEM format)")
        Path keyFile;

        @Option(names = { "--check-expiration" }, description = "Check if signature has expired")
        boolean checkExpiration;

        @Override
        public Integer call() {
            val out = spec.commandLine().getOut();
            val err = spec.commandLine().getErr();

            if (!Files.exists(bundleFile)) {
                err.println("Error: Bundle file not found: " + bundleFile);
                return 1;
            }

            if (!Files.exists(keyFile)) {
                err.println("Error: Key file not found: " + keyFile);
                return 1;
            }

            try {
                val publicKey = loadEd25519PublicKey(keyFile);
                val contents  = extractBundleContents(bundleFile);

                val manifestJson = contents.remove(BundleManifest.MANIFEST_FILENAME);
                if (manifestJson == null) {
                    err.println("Error: Bundle is not signed (no manifest found)");
                    return 1;
                }

                val manifest    = BundleManifest.fromJson(manifestJson);
                val currentTime = checkExpiration ? java.time.Instant.now() : null;

                contents.remove(BundleManifest.MANIFEST_FILENAME);
                BundleSigner.verify(manifest, contents, publicKey, currentTime);

                out.println("Verification successful");
                out.printf("  Key ID: %s%n", manifest.signature().keyId());
                out.printf("  Created: %s%n", manifest.created());
                if (manifest.expires() != null) {
                    out.printf("  Expires: %s%n", manifest.expires());
                }
                out.printf("  Files verified: %d%n", manifest.files().size());
                return 0;

            } catch (BundleSignatureException e) {
                err.println("Verification FAILED: " + e.getMessage());
                return 1;
            } catch (Exception e) {
                err.println("Error verifying bundle: " + e.getMessage());
                return 1;
            }
        }

    }

    @Command(name = "inspect", description = "Show bundle contents and metadata")
    static class Inspect implements Callable<Integer> {

        @Spec
        CommandSpec spec;

        @Option(names = { "-b", "--bundle" }, required = true, description = "Bundle file to inspect")
        Path bundleFile;

        @Override
        public Integer call() {
            val out = spec.commandLine().getOut();
            val err = spec.commandLine().getErr();

            if (!Files.exists(bundleFile)) {
                err.println("Error: Bundle file not found: " + bundleFile);
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
                    if (manifest.expires() != null) {
                        out.printf("  Expires: %s%n", manifest.expires());
                    }
                } else {
                    out.println("Signature:");
                    out.printf("  Status: UNSIGNED%n");
                }
                out.println();

                val pdpJson = contents.get(PDP_JSON);
                if (pdpJson != null) {
                    out.println("Configuration (pdp.json):");
                    out.println(indent(pdpJson.trim(), "  "));
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

            } catch (Exception e) {
                err.println("Error inspecting bundle: " + e.getMessage());
                return 1;
            }
        }

        private String indent(String text, String prefix) {
            return prefix + text.replace("\n", "\n" + prefix);
        }

    }

    private static java.util.Map<String, String> extractBundleContents(Path bundlePath) throws IOException {
        val contents = new java.util.HashMap<String, String>();
        try (val zipStream = new java.util.zip.ZipInputStream(Files.newInputStream(bundlePath))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zipStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                val name    = entry.getName();
                val content = new String(zipStream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                contents.put(name, content);
            }
        }
        return contents;
    }

    private static java.security.PrivateKey loadEd25519PrivateKey(Path keyFile) throws Exception {
        val pemContent = Files.readString(keyFile).trim();
        val base64Key  = pemContent.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        val keyBytes   = java.util.Base64.getDecoder().decode(base64Key);
        val keySpec    = new java.security.spec.PKCS8EncodedKeySpec(keyBytes);
        val keyFactory = java.security.KeyFactory.getInstance("Ed25519");
        return keyFactory.generatePrivate(keySpec);
    }

    private static java.security.PublicKey loadEd25519PublicKey(Path keyFile) throws Exception {
        val pemContent = Files.readString(keyFile).trim();
        val base64Key  = pemContent.replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        val keyBytes   = java.util.Base64.getDecoder().decode(base64Key);
        val keySpec    = new java.security.spec.X509EncodedKeySpec(keyBytes);
        val keyFactory = java.security.KeyFactory.getInstance("Ed25519");
        return keyFactory.generatePublic(keySpec);
    }

}
