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

import com.nimbusds.jose.jwk.OctetKeyPair;
import io.sapl.api.SaplVersion;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.functions.libraries.crypto.PemUtils;
import io.sapl.secrets.SecretSealing;
import io.sapl.secrets.ValueSealer;
import io.sapl.pdp.configuration.ExtensionFiles;
import io.sapl.pdp.configuration.PDPConfigurationException;
import io.sapl.pdp.configuration.bundle.BundleBuilder;
import io.sapl.pdp.configuration.bundle.BundleManifest;
import io.sapl.pdp.configuration.bundle.BundleSignatureException;
import io.sapl.pdp.configuration.bundle.BundleSigner;
import lombok.val;
import org.jspecify.annotations.Nullable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.nio.file.attribute.PosixFilePermissions;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.function.UnaryOperator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static io.sapl.functions.libraries.crypto.CryptoConstants.ALGORITHM_ED25519;
import static java.util.Objects.requireNonNull;

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
        Bundles package SAPL policies, PDP configuration, secrets, and
        extension data into a single .saplbundle file. They can be
        cryptographically signed with Ed25519 keys for integrity
        verification at load time, and their secrets can be sealed to an
        X25519 recipient so no cleartext credentials travel with the
        bundle.

        A secrets file carries its sealing state in its name: secrets.json
        and ext-<name>-secrets.json are cleartext, secrets.sealed.json and
        ext-<name>-secrets.sealed.json are sealed. A bundle or directory
        never mixes both states.

        The commands compose into a full maintenance loop:
        keygen / keygen-secrets, then seal, create, verify, inspect, and
        for editing an existing bundle: unpack, unseal, edit, seal,
        create, sign.

        Key option convention: -k always takes an Ed25519 signing or
        verification key (PEM). Sealing keys are X25519 JWKs and always
        use --seal-to (public key) or --unseal-with (private key).
        """ },
    subcommands = {
        BundleCommand.Create.class, BundleCommand.Unpack.class,
        BundleCommand.Seal.class, BundleCommand.Unseal.class, BundleCommand.Sign.class,
        BundleCommand.Verify.class, BundleCommand.Inspect.class,
        BundleCommand.Keygen.class, BundleCommand.SecretsKeygen.class
    }
)
// @formatter:on
public class BundleCommand {

    private static final String CONFIGURATION_ID_DETAIL_LINE = "  Configuration ID: %s%n";
    private static final String CONFIGURATION_ID_LINE        = "Configuration ID: %s%n";
    private static final String PDP_JSON                     = "pdp.json";
    private static final String SAPL_EXTENSION               = ".sapl";

    private static final String ERROR_ALREADY_SEALED             = "Error: The input folder is already sealed. Omit --seal-to to bundle it verbatim.";
    public static final String  ERROR_BUNDLE_NOT_FOUND           = "Error: Bundle file not found: %s.";
    private static final String ERROR_BUNDLE_NOT_SIGNED          = "Error: Bundle is not signed (no manifest found).";
    private static final String ERROR_CREATING_BUNDLE            = "Error creating bundle: %s.";
    private static final String ERROR_FILE_ALREADY_EXISTS        = "Error: File already exists: %s.";
    private static final String ERROR_GENERATING_KEYPAIR         = "Error generating keypair: %s.";
    private static final String ERROR_INSPECTING_BUNDLE          = "Error inspecting bundle: %s.";
    private static final String ERROR_KEY_NOT_FOUND              = "Error: Key file not found: %s.";
    private static final String ERROR_MIXED_SEALING              = "Error: The input folder mixes sealed and plaintext secrets. Seal all secrets or none.";
    private static final String ERROR_NOT_A_BUNDLE               = "Error: Not a valid SAPL bundle: %s.";
    private static final String ERROR_NOT_A_DIRECTORY            = "Error: Input path is not a directory: %s.";
    private static final String ERROR_NO_POLICIES_FOUND          = "Error: No .sapl files found in: %s.";
    private static final String ERROR_PLAINTEXT_SECRETS_UNSEALED = "Error: The input folder has plaintext secrets. Provide --seal-to to seal them, or pre-seal the folder.";
    private static final String ERROR_SEALING_DIRECTORY          = "Error sealing directory: %s.";
    private static final String ERROR_SIGNING_BUNDLE             = "Error signing bundle: %s.";
    private static final String ERROR_SIGN_PLAINTEXT_SECRETS     = "Error: The bundle contains plaintext secrets. Unpack, seal the directory, and re-create before signing.";
    private static final String ERROR_TARGET_EXISTS              = "Target file already exists: %s.";
    private static final String ERROR_UNPACKING_BUNDLE           = "Error unpacking bundle: %s.";
    private static final String ERROR_UNSEALING_DIRECTORY        = "Error unsealing directory: %s.";
    private static final String ERROR_VERIFICATION_FAILED        = "Verification FAILED: %s.";
    private static final String ERROR_VERIFYING_BUNDLE           = "Error verifying bundle: %s.";
    private static final String HINT_USE_FORCE                   = "Use --force to overwrite.";

    // @formatter:off
    @Command(
        name = "create",
        mixinStandardHelpOptions = true,
        header = "Create a policy bundle from a directory.",
        description = { """
            Packages all .sapl policy files and pdp.json from the input
            directory into a .saplbundle file. Policies are validated for
            correct SAPL syntax during creation. Extension data
            (ext-<name>.json), extension secrets, PDP-level secrets, and
            critical-extensions.json are packaged too.

            Secrets are handled by file name. A plaintext folder
            (secrets.json, ext-<name>-secrets.json) requires --seal-to: the
            files are sealed to the given X25519 recipient public key and
            written as secrets.sealed.json and
            ext-<name>-secrets.sealed.json. A pre-sealed folder (only
            *.sealed.json secrets, for example from 'sapl bundle seal' or a
            verbatim unpack) is bundled as-is and needs no key, and
            --seal-to is rejected. A folder that mixes plaintext and sealed
            secrets is rejected. Plaintext secrets are never written into a
            bundle. Generate the recipient keypair with
            'sapl bundle keygen-secrets'.

            Every bundle carries a .sapl-manifest.json recording the
            configurationId of the publication. Set it explicitly with
            --configuration-id, or a content-derived id of the form
            bundle@<hash16> is recorded. The resulting configuration id is
            printed on success for CI or agent capture.

            Optionally signs the bundle when a private key is provided.
            This is equivalent to creating then running 'sapl bundle sign'.
            """ },
        exitCodeListHeading = "%nExit Codes:%n",
        exitCodeList = {
            " 0:Bundle created successfully",
            " 1:Error (invalid input, no policies found, mixed or unsealed secrets, or I/O error)"
        },
        footerHeading = "%nExamples:%n",
        footer = { """
              # Create an unsigned bundle
              sapl bundle create -i ./policies -o policies.saplbundle

              # Create and sign in one step
              sapl bundle create -i ./policies -o policies.saplbundle -k signing.pem --key-id prod-2026

              # Create a bundle, sealing plaintext secrets to a recipient
              sapl bundle create -i ./policies -o policies.saplbundle --seal-to recipient.pub.jwk

              # Create from a pre-sealed folder (no key needed)
              sapl bundle create -i ./sealed-policies -o policies.saplbundle

            See Also: sapl-bundle-sign(1), sapl-bundle-seal(1), sapl-bundle-unpack(1), sapl-bundle-keygen-secrets(1)
            """ }
    )
    // @formatter:on
    static class Create implements Callable<Integer> {

        @Spec
        private CommandSpec spec;

        @Option(names = { "-i", "--input" }, required = true, description = "Input directory containing policies")
        private Path inputDir;

        @Option(names = {
                "--configuration-id" }, description = "Configuration id recorded in the bundle manifest (defaults to a content-derived id)")
        private String configurationId;

        @Option(names = { "-k", "--key" }, description = "Ed25519 private key file (PEM format) for signing")
        private Path keyFile;

        @Option(names = { "--key-id" }, description = "Key identifier for rotation support", defaultValue = "default")
        private String keyId;

        @Option(names = { "-o", "--output" }, required = true, description = "Output bundle file path")
        private Path outputFile;

        @Option(names = {
                "--seal-to" }, description = "X25519 recipient public key (JWK file) that plaintext secrets are sealed to")
        private Path sealToFile;

        @Option(names = { "--force" }, description = "Overwrite an existing output file")
        private boolean force;

        @Override
        public Integer call() {
            val out = spec.commandLine().getOut();
            val err = spec.commandLine().getErr();
            try {
                requireDirectory(inputDir);
                requireKeyFile(keyFile);
                requireOverwritable(force, outputFile);

                val builder = BundleBuilder.create();
                if (configurationId != null) {
                    builder.withConfigurationId(configurationId);
                }
                builder.withAttribution("sapl-node/" + SaplVersion.VERSION);
                addPdpConfiguration(builder);
                val policies = addPolicies(builder);
                addExtensionConfigs(builder);
                addCriticalExtensions(builder);
                addSecrets(builder, scanAndCheckSecrets());
                if (sealToFile != null) {
                    builder.sealSecretsWith(loadX25519PublicKey(sealToFile));
                }
                if (keyFile != null) {
                    builder.signWith(loadEd25519PrivateKey(keyFile), keyId);
                }
                val manifest = builder.writeTo(outputFile);

                printSuccess(out, policies);
                out.printf(CONFIGURATION_ID_LINE, manifest.configurationId());
                return 0;

            } catch (CommandFailedException e) {
                e.printTo(err);
                return 1;
            } catch (IOException | GeneralSecurityException | ParseException | PDPConfigurationException
                    | BundleSignatureException | IllegalArgumentException e) {
                err.println(ERROR_CREATING_BUNDLE.formatted(e.getMessage()));
                return 1;
            }
        }

        private void addPdpConfiguration(BundleBuilder builder) throws IOException {
            val pdpJson = inputDir.resolve(PDP_JSON);
            if (Files.exists(pdpJson)) {
                builder.withPdpJson(Files.readString(pdpJson));
            }
        }

        private int addPolicies(BundleBuilder builder) throws IOException, CommandFailedException {
            var policies = 0;
            try (val stream = Files.list(inputDir)) {
                for (val file : stream.filter(p -> p.toString().endsWith(SAPL_EXTENSION)).toList()) {
                    builder.withPolicy(fileNameOf(file), Files.readString(file));
                    policies++;
                }
            }
            if (policies == 0) {
                throw new CommandFailedException(ERROR_NO_POLICIES_FOUND.formatted(inputDir));
            }
            return policies;
        }

        private SecretsFiles scanAndCheckSecrets() throws IOException, CommandFailedException {
            val secretsFiles = SecretsFiles.scan(inputDir);
            if (secretsFiles.hasPlaintext() && secretsFiles.hasSealed()) {
                throw new CommandFailedException(ERROR_MIXED_SEALING);
            }
            if (secretsFiles.hasPlaintext() && sealToFile == null) {
                throw new CommandFailedException(ERROR_PLAINTEXT_SECRETS_UNSEALED);
            }
            if (secretsFiles.hasSealed() && sealToFile != null) {
                throw new CommandFailedException(ERROR_ALREADY_SEALED);
            }
            return secretsFiles;
        }

        private void printSuccess(PrintWriter out, int policies) {
            if (keyFile != null) {
                out.printf("Created signed bundle: %s (%d policies, key-id: %s)%n", outputFile, policies, keyId);
            } else {
                out.printf("Created bundle: %s (%d policies)%n", outputFile, policies);
            }
        }

        private void addExtensionConfigs(BundleBuilder builder) throws IOException {
            try (val stream = Files.list(inputDir)) {
                for (val file : stream.filter(Files::isRegularFile).toList()) {
                    val filename = fileNameOf(file);
                    if (ExtensionFiles.isExtensionFile(filename)) {
                        builder.withExtension(ExtensionFiles.extensionNameOf(filename), Files.readString(file));
                    }
                }
            }
        }

        private void addSecrets(BundleBuilder builder, SecretsFiles secretsFiles) throws IOException {
            if (secretsFiles.plainSecrets() != null) {
                builder.withSecrets(Files.readString(secretsFiles.plainSecrets()));
            }
            if (secretsFiles.sealedSecrets() != null) {
                builder.withSealedSecrets(Files.readString(secretsFiles.sealedSecrets()));
            }
            for (val file : secretsFiles.plainExtensionSecrets()) {
                builder.withExtensionSecrets(ExtensionFiles.extensionSecretsNameOf(fileNameOf(file)),
                        Files.readString(file));
            }
            for (val file : secretsFiles.sealedExtensionSecrets()) {
                builder.withSealedExtensionSecrets(ExtensionFiles.sealedExtensionSecretsNameOf(fileNameOf(file)),
                        Files.readString(file));
            }
        }

        private void addCriticalExtensions(BundleBuilder builder) throws IOException {
            val criticalFile = inputDir.resolve(ExtensionFiles.CRITICAL_EXTENSIONS_FILE);
            if (!Files.exists(criticalFile)) {
                return;
            }
            for (val name : ExtensionFiles.parseCriticalExtensions(Files.readString(criticalFile))) {
                builder.withCriticalExtension(name);
            }
        }

    }

    // @formatter:off
    @Command(
        name = "unpack",
        mixinStandardHelpOptions = true,
        header = "Unpack a policy bundle into a directory.",
        description = { """
            Extracts every file from a .saplbundle into the output directory:
            pdp.json, .sapl policies, secrets files, extension files, and
            critical-extensions.json. The manifest is not written, so the
            directory can be edited and repackaged with 'sapl bundle create'.
            The manifest's configuration id is printed; the unpacked sources
            carry none.

            With -k the signature is verified before unpacking and a mismatch
            aborts. With --unseal-with, secrets.sealed.json and every
            ext-<name>-secrets.sealed.json are unsealed with the X25519
            recipient private key and written under their plaintext names
            (secrets.json, ext-<name>-secrets.json), producing a valid
            plaintext directory. Without it, sealed files are written
            verbatim, which allows repackaging without the key.
            """ },
        exitCodeListHeading = "%nExit Codes:%n",
        exitCodeList = {
            " 0:Bundle unpacked successfully",
            " 1:Error (bundle or key not found, verification failed, or I/O error)"
        },
        footerHeading = "%nExamples:%n",
        footer = { """
              # Unpack verbatim (sealed secrets stay sealed)
              sapl bundle unpack -b policies.saplbundle -o ./policies

              # Verify then unpack
              sapl bundle unpack -b policies.saplbundle -o ./policies -k signing.pub

              # Unpack and unseal secrets to cleartext
              sapl bundle unpack -b policies.saplbundle -o ./policies --unseal-with recipient.jwk

            See Also: sapl-bundle-create(1), sapl-bundle-seal(1), sapl-bundle-unseal(1), sapl-bundle-verify(1)
            """ }
    )
    // @formatter:on
    static class Unpack implements Callable<Integer> {

        @Spec
        private CommandSpec spec;

        @Option(names = { "-b", "--bundle" }, required = true, description = "Bundle file to unpack")
        private Path bundleFile;

        @Option(names = { "-o", "--output" }, required = true, description = "Output directory")
        private Path outputDir;

        @Option(names = { "-k",
                "--key" }, description = "Ed25519 public key (PEM) to verify the signature before unpacking")
        private Path keyFile;

        @Option(names = {
                "--unseal-with" }, description = "X25519 recipient private key (JWK) to unseal secrets to cleartext")
        private Path unsealKeyFile;

        @Option(names = { "--force" }, description = "Overwrite existing files in the output directory")
        private boolean force;

        @Override
        public Integer call() {
            val out = spec.commandLine().getOut();
            val err = spec.commandLine().getErr();
            try {
                requireBundleFile(bundleFile);
                requireKeyFile(keyFile);
                requireKeyFile(unsealKeyFile);

                val contents     = extractBundleContents(bundleFile);
                val manifestJson = contents.remove(BundleManifest.MANIFEST_FILENAME);
                verifySignatureIfKeyGiven(manifestJson, contents);

                val unsealKey = unsealKeyFile != null ? loadX25519PrivateKey(unsealKeyFile) : null;
                val base      = outputDir.toAbsolutePath().normalize();
                Files.createDirectories(base);

                val unpacked = unsealAndRestoreNames(contents, unsealKey);
                requireTargetsOverwritable(base, unpacked.keySet());
                writeUnpackedFiles(base, unpacked);

                out.printf("Unpacked bundle: %s (%d files) to %s%n", bundleFile, unpacked.size(), outputDir);
                if (manifestJson != null) {
                    out.printf(CONFIGURATION_ID_LINE, manifestConfigurationId(manifestJson));
                }
                return 0;

            } catch (CommandFailedException e) {
                e.printTo(err);
                return 1;
            } catch (BundleSignatureException e) {
                err.println(ERROR_VERIFICATION_FAILED.formatted(e.getMessage()));
                return 1;
            } catch (IOException | GeneralSecurityException | ParseException | PDPConfigurationException e) {
                err.println(ERROR_UNPACKING_BUNDLE.formatted(e.getMessage()));
                return 1;
            }
        }

        private void verifySignatureIfKeyGiven(@Nullable String manifestJson, Map<String, String> contents)
                throws IOException, GeneralSecurityException, CommandFailedException {
            if (keyFile == null) {
                return;
            }
            if (manifestJson == null) {
                throw new CommandFailedException(ERROR_BUNDLE_NOT_SIGNED);
            }
            BundleSigner.verify(BundleManifest.fromJson(manifestJson), contents, loadEd25519PublicKey(keyFile));
        }

        // Unsealing restores the plaintext file names, so the output directory is a
        // valid plaintext folder.
        private static Map<String, UnpackedFile> unsealAndRestoreNames(Map<String, String> contents,
                @Nullable OctetKeyPair unsealKey) {
            val unpacked = new LinkedHashMap<String, UnpackedFile>();
            for (val entry : contents.entrySet()) {
                val name = entry.getKey();
                if (unsealKey != null && ExtensionFiles.SEALED_SECRETS_FILE.equals(name)) {
                    unpacked.put(ExtensionFiles.SECRETS_FILE,
                            new UnpackedFile(unsealDocument(entry.getValue(), unsealKey), true));
                } else if (unsealKey != null && ExtensionFiles.isSealedExtensionSecretsFile(name)) {
                    unpacked.put(plaintextExtensionSecretsName(name),
                            new UnpackedFile(unsealDocument(entry.getValue(), unsealKey), true));
                } else {
                    unpacked.put(name, new UnpackedFile(entry.getValue(), false));
                }
            }
            return unpacked;
        }

        private static String plaintextExtensionSecretsName(String sealedName) {
            return ExtensionFiles.EXTENSION_PREFIX + ExtensionFiles.sealedExtensionSecretsNameOf(sealedName)
                    + ExtensionFiles.EXTENSION_SECRETS_SUFFIX;
        }

        private void requireTargetsOverwritable(Path base, Set<String> names)
                throws IOException, CommandFailedException {
            if (force) {
                return;
            }
            for (val name : names) {
                if (Files.exists(safeResolve(base, name))) {
                    throw new CommandFailedException(ERROR_FILE_ALREADY_EXISTS.formatted(base.resolve(name)),
                            HINT_USE_FORCE);
                }
            }
        }

        private static void writeUnpackedFiles(Path base, Map<String, UnpackedFile> unpacked) throws IOException {
            for (val entry : unpacked.entrySet()) {
                val target = safeResolve(base, entry.getKey());
                Files.writeString(target, entry.getValue().content());
                if (entry.getValue().unsealed()) {
                    restrictToOwner(target);
                }
            }
        }

        private static Path safeResolve(Path base, String entryName) throws IOException {
            if (entryName.contains("/") || entryName.contains("\\") || entryName.contains("..")) {
                throw new IOException("Refusing to write unsafe bundle entry name: " + entryName);
            }
            val target = base.resolve(entryName).normalize();
            if (!target.startsWith(base) || !base.equals(target.getParent())) {
                throw new IOException("Refusing to write unsafe bundle entry name: " + entryName);
            }
            return target;
        }

        private record UnpackedFile(String content, boolean unsealed) {}
    }

    // @formatter:off
    @Command(
        name = "seal",
        mixinStandardHelpOptions = true,
        header = "Seal a policy directory's secrets to a recipient.",
        description = { """
            Seals every plaintext secrets file in the directory to the given
            X25519 recipient public key: secrets.json becomes
            secrets.sealed.json and each ext-<name>-secrets.json becomes
            ext-<name>-secrets.sealed.json. The plaintext files are deleted, so
            the directory holds no cleartext secrets afterwards.
            """ },
        exitCodeListHeading = "%nExit Codes:%n",
        exitCodeList = {
            " 0:Secrets sealed (or none found)",
            " 1:Error (directory or key not found, sealed target exists, or I/O error)"
        },
        footerHeading = "%nExamples:%n",
        footer = { """
              sapl bundle seal -i ./policies --seal-to recipient.pub.jwk

            See Also: sapl-bundle-unseal(1), sapl-bundle-keygen-secrets(1)
            """ }
    )
    // @formatter:on
    static class Seal implements Callable<Integer> {

        @Spec
        private CommandSpec spec;

        @Option(names = { "-i", "--input" }, required = true, description = "Directory whose secrets are sealed")
        private Path inputDir;

        @Option(names = { "--seal-to" }, required = true, description = "X25519 recipient public key (JWK file)")
        private Path recipientFile;

        @Override
        public Integer call() {
            val out = spec.commandLine().getOut();
            val err = spec.commandLine().getErr();
            try {
                requireDirectory(inputDir);
                requireKeyFile(recipientFile);
                val recipient    = loadX25519PublicKey(recipientFile);
                val secretsFiles = SecretsFiles.scan(inputDir);
                var sealed       = 0;
                if (secretsFiles.plainSecrets() != null) {
                    transform(secretsFiles.plainSecrets(), inputDir.resolve(ExtensionFiles.SEALED_SECRETS_FILE),
                            content -> sealDocument(content, recipient));
                    sealed++;
                }
                for (val file : secretsFiles.plainExtensionSecrets()) {
                    val name = ExtensionFiles.extensionSecretsNameOf(fileNameOf(file));
                    transform(file,
                            inputDir.resolve(ExtensionFiles.EXTENSION_PREFIX + name
                                    + ExtensionFiles.SEALED_EXTENSION_SECRETS_SUFFIX),
                            content -> sealDocument(content, recipient));
                    sealed++;
                }
                if (sealed == 0) {
                    out.println("No plaintext secrets found.");
                } else {
                    out.printf("Sealed %d secrets file(s) in %s%n", sealed, inputDir);
                }
                return 0;

            } catch (CommandFailedException e) {
                e.printTo(err);
                return 1;
            } catch (IOException | ParseException e) {
                err.println(ERROR_SEALING_DIRECTORY.formatted(e.getMessage()));
                return 1;
            }
        }

    }

    // @formatter:off
    @Command(
        name = "unseal",
        mixinStandardHelpOptions = true,
        header = "Unseal a policy directory's secrets with the recipient key.",
        description = { """
            Unseals every sealed secrets file in the directory with the given
            X25519 recipient private key: secrets.sealed.json becomes
            secrets.json and each ext-<name>-secrets.sealed.json becomes
            ext-<name>-secrets.json. The sealed files are deleted. The
            resulting plaintext directory can be edited and resealed with
            'sapl bundle seal'.
            """ },
        exitCodeListHeading = "%nExit Codes:%n",
        exitCodeList = {
            " 0:Secrets unsealed (or none found)",
            " 1:Error (directory or key not found, plaintext target exists, or I/O error)"
        },
        footerHeading = "%nExamples:%n",
        footer = { """
              sapl bundle unseal -i ./policies --unseal-with recipient.jwk

            See Also: sapl-bundle-seal(1), sapl-bundle-keygen-secrets(1)
            """ }
    )
    // @formatter:on
    static class Unseal implements Callable<Integer> {

        @Spec
        private CommandSpec spec;

        @Option(names = { "-i", "--input" }, required = true, description = "Directory whose secrets are unsealed")
        private Path inputDir;

        @Option(names = { "--unseal-with" }, required = true, description = "X25519 recipient private key (JWK file)")
        private Path recipientFile;

        @Override
        public Integer call() {
            val out = spec.commandLine().getOut();
            val err = spec.commandLine().getErr();
            try {
                requireDirectory(inputDir);
                requireKeyFile(recipientFile);
                val recipient    = loadX25519PrivateKey(recipientFile);
                val secretsFiles = SecretsFiles.scan(inputDir);
                var unsealed     = 0;
                if (secretsFiles.sealedSecrets() != null) {
                    val target = inputDir.resolve(ExtensionFiles.SECRETS_FILE);
                    transform(secretsFiles.sealedSecrets(), target, content -> unsealDocument(content, recipient));
                    restrictToOwner(target);
                    unsealed++;
                }
                for (val file : secretsFiles.sealedExtensionSecrets()) {
                    val name   = ExtensionFiles.sealedExtensionSecretsNameOf(fileNameOf(file));
                    val target = inputDir
                            .resolve(ExtensionFiles.EXTENSION_PREFIX + name + ExtensionFiles.EXTENSION_SECRETS_SUFFIX);
                    transform(file, target, content -> unsealDocument(content, recipient));
                    restrictToOwner(target);
                    unsealed++;
                }
                if (unsealed == 0) {
                    out.println("No sealed secrets found.");
                } else {
                    out.printf("Unsealed %d secrets file(s) in %s%n", unsealed, inputDir);
                }
                return 0;

            } catch (CommandFailedException e) {
                e.printTo(err);
                return 1;
            } catch (IOException | ParseException e) {
                err.println(ERROR_UNSEALING_DIRECTORY.formatted(e.getMessage()));
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
            All files are preserved: pdp.json, policies, sealed secrets,
            extension files, and critical-extensions.json. The signature
            enables the PDP server to verify bundle integrity and
            authenticity at load time.

            A bundle containing plaintext secrets is refused. Unpack it,
            seal the directory, and re-create it before signing.

            When the input bundle carries a manifest, its configurationId,
            attribution, and audience are carried over into the signed
            bundle; the creation timestamp and engine version are re-minted
            because re-signing is a build event.

            By default, the input bundle is overwritten with the signed
            version. Use -o to write to a different file.
            """ },
        exitCodeListHeading = "%nExit Codes:%n",
        exitCodeList = {
            " 0:Bundle signed successfully",
            " 1:Error (bundle or key not found, plaintext secrets, or signing failed)"
        },
        footerHeading = "%nExamples:%n",
        footer = { """
              # Sign a bundle (overwrites the original)
              sapl bundle sign -b policies.saplbundle -k signing.pem

              # Sign and write to a new file
              sapl bundle sign -b policies.saplbundle -k signing.pem -o signed.saplbundle --key-id prod-2026

            See Also: sapl-bundle-keygen(1), sapl-bundle-verify(1), sapl-bundle-seal(1)
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
            try {
                requireBundleFile(bundleFile);
                requireKeyFile(keyFile);

                val privateKey = loadEd25519PrivateKey(keyFile);
                val contents   = extractBundleContents(bundleFile);
                val builder    = BundleBuilder.create();

                carryOverManifestMetadata(builder, contents.remove(BundleManifest.MANIFEST_FILENAME));
                val pdpJson = contents.remove(PDP_JSON);
                if (pdpJson != null) {
                    builder.withPdpJson(pdpJson);
                }
                val criticalJson  = contents.remove(ExtensionFiles.CRITICAL_EXTENSIONS_FILE);
                val sealedSecrets = contents.remove(ExtensionFiles.SEALED_SECRETS_FILE);
                if (sealedSecrets != null) {
                    builder.withSealedSecrets(sealedSecrets);
                }
                if (contents.remove(ExtensionFiles.SECRETS_FILE) != null) {
                    throw new CommandFailedException(ERROR_SIGN_PLAINTEXT_SECRETS);
                }

                for (val entry : contents.entrySet()) {
                    addBundleEntry(builder, entry.getKey(), entry.getValue());
                }

                if (criticalJson != null) {
                    for (val critical : ExtensionFiles.parseCriticalExtensions(criticalJson)) {
                        builder.withCriticalExtension(critical);
                    }
                }

                builder.signWith(privateKey, keyId);

                val target   = outputFile != null ? outputFile : bundleFile;
                val manifest = builder.writeTo(target);

                out.printf("Signed bundle: %s (key-id: %s)%n", target, keyId);
                out.printf(CONFIGURATION_ID_LINE, manifest.configurationId());
                return 0;

            } catch (CommandFailedException e) {
                e.printTo(err);
                return 1;
            } catch (IOException | GeneralSecurityException | PDPConfigurationException | BundleSignatureException e) {
                err.println(ERROR_SIGNING_BUNDLE.formatted(e.getMessage()));
                return 1;
            }
        }

        // Re-signing is a build event: the configurationId, attribution, and
        // audience travel with the content, while created and version are re-minted.
        // A bundle without a manifest gets this tool's attribution, not the engine
        // library default.
        private static void carryOverManifestMetadata(BundleBuilder builder, @Nullable String manifestJson) {
            if (manifestJson == null) {
                builder.withAttribution("sapl-node/" + SaplVersion.VERSION);
                return;
            }
            val manifest = BundleManifest.fromJson(manifestJson);
            builder.withConfigurationId(manifest.configurationId());
            builder.withAttributionJson(manifest.attribution().toString());
            if (manifest.audience() != null) {
                builder.withSealingRecipient(manifest.audience().sealingRecipient());
            }
        }

        private static void addBundleEntry(BundleBuilder builder, String name, String content)
                throws CommandFailedException {
            if (name.endsWith(SAPL_EXTENSION)) {
                builder.withPolicy(name, content);
            } else if (ExtensionFiles.isSealedExtensionSecretsFile(name)) {
                builder.withSealedExtensionSecrets(ExtensionFiles.sealedExtensionSecretsNameOf(name), content);
            } else if (ExtensionFiles.isExtensionSecretsFile(name)) {
                throw new CommandFailedException(ERROR_SIGN_PLAINTEXT_SECRETS);
            } else if (ExtensionFiles.isExtensionFile(name)) {
                builder.withExtension(ExtensionFiles.extensionNameOf(name), content);
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
            try {
                requireBundleFile(bundleFile);
                requireKeyFile(keyFile);

                val publicKey = loadEd25519PublicKey(keyFile);
                val contents  = extractBundleContents(bundleFile);

                val manifestJson = contents.remove(BundleManifest.MANIFEST_FILENAME);
                if (manifestJson == null) {
                    throw new CommandFailedException(ERROR_BUNDLE_NOT_SIGNED);
                }

                val manifest = BundleManifest.fromJson(manifestJson);

                BundleSigner.verify(manifest, contents, publicKey);

                out.println("Verification successful");
                out.printf("  Key ID: %s%n", manifest.signature().keyId());
                out.printf("  Created: %s%n", manifest.created());
                out.printf(CONFIGURATION_ID_DETAIL_LINE, manifest.configurationId());
                out.printf("  Files verified: %d%n", manifest.files().size());
                return 0;

            } catch (CommandFailedException e) {
                e.printTo(err);
                return 1;
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
            Displays the signature status, the manifest metadata
            (configuration id and attribution), PDP configuration
            (pdp.json), all policies with their sizes, the secrets files
            with their sealing state, and the extensions with their
            payloads and critical markers. Secret values are never
            printed, only file names and sizes. Useful for auditing
            bundles before deployment.

            The Integrity line is always explicit. With -k the signature
            and all file hashes are checked and reported as VERIFIED or
            FAILED, and a failure also sets the exit code. Without -k the
            line reads NOT CHECKED, so an unverified bundle can never be
            mistaken for a verified one.
            """ },
        exitCodeListHeading = "%nExit Codes:%n",
        exitCodeList = {
            " 0:Inspection completed (and integrity verified, when -k was given)",
            " 1:Error reading bundle, or integrity check failed"
        },
        footerHeading = "%nExamples:%n",
        footer = { """
              # Show bundle contents and signature status
              sapl bundle inspect -b policies.saplbundle

              # Inspect and verify integrity in one step
              sapl bundle inspect -b policies.saplbundle -k signing.pub

            See Also: sapl-bundle-verify(1)
            """ }
    )
    // @formatter:on
    static class Inspect implements Callable<Integer> {

        private static final String NONE_LISTED = "  (none)";

        @Spec
        private CommandSpec spec;

        @Option(names = { "-b", "--bundle" }, required = true, description = "Bundle file to inspect")
        private Path bundleFile;

        @Option(names = { "-k",
                "--key" }, description = "Ed25519 public key (PEM) to verify the signature and file hashes")
        private Path keyFile;

        @Override
        public Integer call() {
            val out = spec.commandLine().getOut();
            val err = spec.commandLine().getErr();
            try {
                requireBundleFile(bundleFile);
                requireKeyFile(keyFile);

                val contents = extractBundleContents(bundleFile);
                if (contents.isEmpty()) {
                    throw new CommandFailedException(ERROR_NOT_A_BUNDLE.formatted(bundleFile));
                }

                out.printf("Bundle: %s%n", bundleFile.getFileName());
                out.println();

                val manifestJson = contents.get(BundleManifest.MANIFEST_FILENAME);
                val reading      = readManifestLeniently(manifestJson);
                printSignature(out, reading);
                val integrityOk = printIntegrity(out, contents, reading.manifest(), reading.unreadable());
                out.println();
                printManifestSection(out, reading, manifestJson);

                val pdpJson = contents.get(PDP_JSON);
                if (pdpJson != null) {
                    out.println("Configuration (pdp.json):");
                    out.print(pdpJson.trim().indent(2));
                } else {
                    out.println("Configuration: (none)");
                }
                out.println();

                printPolicies(out, contents);
                out.println();

                printSecrets(out, contents);
                out.println();
                printExtensions(out, contents);

                return integrityOk ? 0 : 1;

            } catch (CommandFailedException e) {
                e.printTo(err);
                return 1;
            } catch (IOException | BundleSignatureException e) {
                err.println(ERROR_INSPECTING_BUNDLE.formatted(e.getMessage()));
                return 1;
            }
        }

        private record ManifestReading(@Nullable BundleManifest manifest, boolean unreadable) {}

        /**
         * Inspection degrades on a legacy or malformed manifest instead of
         * aborting, so pre-4.2 bundles can still be audited before rebuilding.
         */
        private static ManifestReading readManifestLeniently(@Nullable String manifestJson) {
            if (manifestJson == null) {
                return new ManifestReading(null, false);
            }
            try {
                return new ManifestReading(BundleManifest.fromJson(manifestJson), false);
            } catch (BundleSignatureException e) {
                return new ManifestReading(null, true);
            }
        }

        private static void printSignature(PrintWriter out, ManifestReading reading) {
            out.println("Signature:");
            val manifest = reading.manifest();
            if (manifest != null && manifest.signature() != null) {
                out.printf("  Status: SIGNED%n");
                out.printf("  Algorithm: %s%n", manifest.signature().algorithm());
                out.printf("  Key ID: %s%n", manifest.signature().keyId());
                out.printf("  Created: %s%n", manifest.created());
            } else if (reading.unreadable()) {
                out.printf("  Status: UNKNOWN (manifest predates SAPL 4.2.0 or is malformed)%n");
            } else {
                out.printf("  Status: UNSIGNED%n");
            }
        }

        private static void printManifestSection(PrintWriter out, ManifestReading reading,
                @Nullable String manifestJson) {
            val manifest = reading.manifest();
            if (manifest != null) {
                out.println("Manifest:");
                out.printf(CONFIGURATION_ID_DETAIL_LINE, manifest.configurationId());
                out.printf("  Attribution: %s%n", manifest.attribution());
                out.println();
            } else if (reading.unreadable()) {
                out.println("Manifest:");
                out.printf(CONFIGURATION_ID_DETAIL_LINE, manifestConfigurationId(manifestJson));
                out.println();
            }
        }

        private static void printPolicies(PrintWriter out, Map<String, String> contents) {
            out.println("Policies:");
            var policyCount = 0;
            for (val entry : contents.entrySet()) {
                if (entry.getKey().endsWith(SAPL_EXTENSION)) {
                    out.printf("  - %s (%d bytes)%n", entry.getKey(), byteSize(entry.getValue()));
                    policyCount++;
                }
            }
            if (policyCount == 0) {
                out.println(NONE_LISTED);
            }
        }

        private boolean printIntegrity(PrintWriter out, Map<String, String> contents, @Nullable BundleManifest manifest,
                boolean manifestUnreadable) {
            val signed         = manifest != null && manifest.signature() != null;
            val unsignedReason = manifestUnreadable ? "manifest predates SAPL 4.2.0 or is malformed"
                    : "bundle is not signed";
            if (keyFile == null) {
                val reason = signed ? "no key provided" : unsignedReason;
                out.printf("  Integrity: NOT CHECKED (%s)%n", reason);
                return true;
            }
            if (!signed) {
                out.printf("  Integrity: FAILED (%s)%n", unsignedReason);
                return false;
            }
            try {
                val files = new HashMap<>(contents);
                files.remove(BundleManifest.MANIFEST_FILENAME);
                BundleSigner.verify(manifest, files, loadEd25519PublicKey(keyFile));
                out.println("  Integrity: VERIFIED");
                return true;
            } catch (BundleSignatureException | IOException | GeneralSecurityException e) {
                out.printf("  Integrity: FAILED (%s)%n", e.getMessage());
                return false;
            }
        }

        private static void printSecrets(PrintWriter out, Map<String, String> contents) {
            out.println("Secrets:");
            val sealedSecrets = contents.get(ExtensionFiles.SEALED_SECRETS_FILE);
            val plainSecrets  = contents.get(ExtensionFiles.SECRETS_FILE);
            if (sealedSecrets != null) {
                out.printf("  - %s (sealed, %d bytes)%n", ExtensionFiles.SEALED_SECRETS_FILE, byteSize(sealedSecrets));
            }
            if (plainSecrets != null) {
                out.printf("  - %s (PLAINTEXT, %d bytes)%n", ExtensionFiles.SECRETS_FILE, byteSize(plainSecrets));
            }
            if (sealedSecrets == null && plainSecrets == null) {
                out.println(NONE_LISTED);
            }
        }

        private static void printExtensions(PrintWriter out, Map<String, String> contents) {
            out.println("Extensions:");
            val names = new TreeMap<String, StringBuilder>();
            for (val entry : contents.entrySet()) {
                val name = entry.getKey();
                if (ExtensionFiles.isSealedExtensionSecretsFile(name)) {
                    detail(names, ExtensionFiles.sealedExtensionSecretsNameOf(name))
                            .append("sealed secrets (%d bytes)".formatted(byteSize(entry.getValue())));
                } else if (ExtensionFiles.isExtensionSecretsFile(name)) {
                    detail(names, ExtensionFiles.extensionSecretsNameOf(name))
                            .append("PLAINTEXT secrets (%d bytes)".formatted(byteSize(entry.getValue())));
                } else if (ExtensionFiles.isExtensionFile(name)) {
                    detail(names, ExtensionFiles.extensionNameOf(name))
                            .append("config (%d bytes)".formatted(byteSize(entry.getValue())));
                }
            }

            Set<String> critical;
            try {
                critical = ExtensionFiles
                        .parseCriticalExtensions(contents.get(ExtensionFiles.CRITICAL_EXTENSIONS_FILE));
            } catch (PDPConfigurationException e) {
                critical = Set.of();
                out.println("  (malformed critical-extensions.json)");
            }
            for (val name : critical) {
                if (!names.containsKey(name)) {
                    detail(names, name).append("NO PAYLOAD");
                }
            }

            if (names.isEmpty()) {
                out.println(NONE_LISTED);
                return;
            }
            for (val entry : names.entrySet()) {
                val marker = critical.contains(entry.getKey()) ? " [critical]" : "";
                out.printf("  - %s%s: %s%n", entry.getKey(), marker, entry.getValue());
            }
        }

        private static int byteSize(String content) {
            return content.getBytes(StandardCharsets.UTF_8).length;
        }

        private static StringBuilder detail(Map<String, StringBuilder> names, String name) {
            val builder = names.computeIfAbsent(name, key -> new StringBuilder());
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            return builder;
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
            try {
                requireOverwritable(force, privateKey);
                requireOverwritable(force, publicKey);

                val keyPairGenerator = KeyPairGenerator.getInstance(ALGORITHM_ED25519);
                val keyPair          = keyPairGenerator.generateKeyPair();

                PemUtils.writeKeyToFile(privateKey, keyPair.getPrivate());
                PemUtils.writeKeyToFile(publicKey, keyPair.getPublic());

                out.println("Generated Ed25519 keypair:");
                out.printf("  Private key: %s%n", privateKey);
                out.printf("  Public key:  %s%n", publicKey);
                return 0;

            } catch (CommandFailedException e) {
                e.printTo(err);
                return 1;
            } catch (IOException | GeneralSecurityException e) {
                err.println(ERROR_GENERATING_KEYPAIR.formatted(e.getMessage()));
                return 1;
            }
        }

    }

    // @formatter:off
    @Command(
        name = "keygen-secrets",
        mixinStandardHelpOptions = true,
        header = "Generate an X25519 keypair for sealing bundle secrets.",
        description = { """
            Generates an X25519 recipient keypair as JWK files: <prefix>.jwk
            (private) and <prefix>.pub.jwk (public). Seal secrets to the
            public key with 'sapl bundle seal --seal-to <prefix>.pub.jwk' or
            'sapl bundle create --seal-to <prefix>.pub.jwk'. The PDP unseals
            them with the matching private key. Keep the private key secret
            and distribute it only to the recipient (or cluster).
            """ },
        exitCodeListHeading = "%nExit Codes:%n",
        exitCodeList = {
            " 0:Keypair generated",
            " 1:Error (file exists without --force, or generation failed)"
        },
        footerHeading = "%nExamples:%n",
        footer = { """
              sapl bundle keygen-secrets -o recipient
              sapl bundle create -i ./policies -o policies.saplbundle --seal-to recipient.pub.jwk
            """ }
    )
    // @formatter:on
    static class SecretsKeygen implements Callable<Integer> {

        @Spec
        private CommandSpec spec;

        @Option(names = { "-o",
                "--output" }, required = true, description = "Output file prefix (creates <prefix>.jwk and <prefix>.pub.jwk)")
        private Path outputPrefix;

        @Option(names = { "--force" }, description = "Overwrite existing files")
        private boolean force;

        @Override
        public Integer call() {
            val out        = spec.commandLine().getOut();
            val err        = spec.commandLine().getErr();
            val privateKey = outputPrefix.resolveSibling(outputPrefix.getFileName() + ".jwk");
            val publicKey  = outputPrefix.resolveSibling(outputPrefix.getFileName() + ".pub.jwk");
            try {
                requireOverwritable(force, privateKey);
                requireOverwritable(force, publicKey);

                val recipient = SecretSealing.generateRecipientKey();
                Files.writeString(privateKey, recipient.toJSONString());
                restrictToOwner(privateKey);
                Files.writeString(publicKey, recipient.toPublicJWK().toJSONString());

                out.println("Generated X25519 secrets keypair:");
                out.printf("  Private key: %s%n", privateKey);
                out.printf("  Public key:  %s%n", publicKey);
                return 0;

            } catch (CommandFailedException e) {
                e.printTo(err);
                return 1;
            } catch (IOException e) {
                err.println(ERROR_GENERATING_KEYPAIR.formatted(e.getMessage()));
                return 1;
            }
        }

    }

    private static final long MAX_BUNDLE_ENTRY_BYTES = 256L * 1024 * 1024;
    private static final long MAX_BUNDLE_TOTAL_BYTES = 256L * 1024 * 1024;

    private static Map<String, String> extractBundleContents(Path bundlePath) throws IOException {
        val  contents   = new HashMap<String, String>();
        long totalBytes = 0;
        try (val zipStream = new ZipInputStream(Files.newInputStream(bundlePath))) {
            ZipEntry entry;
            while ((entry = zipStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                val bytes = readBoundedEntry(zipStream, entry.getName(), MAX_BUNDLE_ENTRY_BYTES);
                totalBytes += bytes.length;
                if (totalBytes > MAX_BUNDLE_TOTAL_BYTES) {
                    throw new IOException("Bundle exceeds total size cap of " + MAX_BUNDLE_TOTAL_BYTES + " bytes");
                }
                contents.put(entry.getName(), new String(bytes, StandardCharsets.UTF_8));
            }
        }
        return contents;
    }

    private static byte[] readBoundedEntry(ZipInputStream in, String entryName, long maxBytes) throws IOException {
        val  out    = new java.io.ByteArrayOutputStream();
        val  buffer = new byte[8192];
        long total  = 0;
        int  read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IOException(
                        "Bundle entry " + entryName + " exceeds per-entry size cap of " + maxBytes + " bytes");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static PrivateKey loadEd25519PrivateKey(Path keyFile) throws IOException, GeneralSecurityException {
        val keyBytes = PemUtils.decodePemFromFile(keyFile);
        return KeyFactory.getInstance(ALGORITHM_ED25519).generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
    }

    private static OctetKeyPair loadX25519PublicKey(Path keyFile) throws IOException, ParseException {
        return OctetKeyPair.parse(Files.readString(keyFile)).toPublicJWK();
    }

    private static OctetKeyPair loadX25519PrivateKey(Path keyFile) throws IOException, ParseException {
        return OctetKeyPair.parse(Files.readString(keyFile));
    }

    // Lenient manifest read for display only: legacy pre-4.2 manifests carry no
    // configurationId and must still be unpackable and inspectable for migration.
    private static String manifestConfigurationId(String manifestJson) {
        if (Value.ofJson(manifestJson) instanceof ObjectValue manifest
                && manifest.get("configurationId") instanceof TextValue(var configurationId)) {
            return configurationId;
        }
        return "(none recorded; manifest predates SAPL 4.2.0)";
    }

    private static String sealDocument(String jsonContent, OctetKeyPair recipientPublicKey) {
        return ValueJsonMarshaller.toJsonString(ValueSealer.seal(recipientPublicKey, Value.ofJson(jsonContent)));
    }

    private static String unsealDocument(String sealedJson, OctetKeyPair privateKey) {
        return ValueJsonMarshaller.toJsonString(ValueSealer.unseal(privateKey, Value.ofJson(sealedJson)));
    }

    private static String fileNameOf(Path path) {
        return requireNonNull(path.getFileName()).toString();
    }

    private static void requireDirectory(Path directory) throws CommandFailedException {
        if (!Files.isDirectory(directory)) {
            throw new CommandFailedException(ERROR_NOT_A_DIRECTORY.formatted(directory));
        }
    }

    private static void requireBundleFile(Path bundleFile) throws CommandFailedException {
        if (!Files.exists(bundleFile)) {
            throw new CommandFailedException(ERROR_BUNDLE_NOT_FOUND.formatted(bundleFile));
        }
    }

    // A null key means the option was not given; only a named key must exist.
    private static void requireKeyFile(@Nullable Path keyFile) throws CommandFailedException {
        if (keyFile != null && !Files.exists(keyFile)) {
            throw new CommandFailedException(ERROR_KEY_NOT_FOUND.formatted(keyFile));
        }
    }

    private static void requireOverwritable(boolean force, Path file) throws CommandFailedException {
        if (!force && Files.exists(file)) {
            throw new CommandFailedException(ERROR_FILE_ALREADY_EXISTS.formatted(file), HINT_USE_FORCE);
        }
    }

    /**
     * A command failure already phrased for the user. Subcommands print it to
     * their error stream and exit with code 1.
     */
    private static final class CommandFailedException extends Exception {

        private static final long serialVersionUID = SaplVersion.VERSION_UID;

        @Nullable
        private final String hint;

        CommandFailedException(String message) {
            this(message, null);
        }

        CommandFailedException(String message, @Nullable String hint) {
            super(message);
            this.hint = hint;
        }

        void printTo(PrintWriter err) {
            err.println(getMessage());
            if (hint != null) {
                err.println(hint);
            }
        }
    }

    private static void transform(Path source, Path target, UnaryOperator<String> transformation) throws IOException {
        if (Files.exists(target)) {
            throw new IOException(ERROR_TARGET_EXISTS.formatted(target));
        }
        Files.writeString(target, transformation.apply(Files.readString(source)));
        Files.delete(source);
    }

    /**
     * The secrets files found in a policy directory, split by sealing state as
     * carried in the file names.
     *
     * @param plainSecrets the cleartext PDP-level secrets file, or null
     * @param sealedSecrets the sealed PDP-level secrets file, or null
     * @param plainExtensionSecrets the cleartext extension secrets files
     * @param sealedExtensionSecrets the sealed extension secrets files
     */
    private record SecretsFiles(
            Path plainSecrets,
            Path sealedSecrets,
            List<Path> plainExtensionSecrets,
            List<Path> sealedExtensionSecrets) {

        static SecretsFiles scan(Path directory) throws IOException {
            Path plainSecrets           = null;
            Path sealedSecrets          = null;
            val  plainExtensionSecrets  = new ArrayList<Path>();
            val  sealedExtensionSecrets = new ArrayList<Path>();
            try (val stream = Files.list(directory)) {
                for (val file : stream.filter(Files::isRegularFile).toList()) {
                    val name = fileNameOf(file);
                    if (ExtensionFiles.SECRETS_FILE.equals(name)) {
                        plainSecrets = file;
                    } else if (ExtensionFiles.SEALED_SECRETS_FILE.equals(name)) {
                        sealedSecrets = file;
                    } else if (ExtensionFiles.isSealedExtensionSecretsFile(name)) {
                        sealedExtensionSecrets.add(file);
                    } else if (ExtensionFiles.isExtensionSecretsFile(name)) {
                        plainExtensionSecrets.add(file);
                    }
                }
            }
            return new SecretsFiles(plainSecrets, sealedSecrets, plainExtensionSecrets, sealedExtensionSecrets);
        }

        boolean hasPlaintext() {
            return plainSecrets != null || !plainExtensionSecrets.isEmpty();
        }

        boolean hasSealed() {
            return sealedSecrets != null || !sealedExtensionSecrets.isEmpty();
        }
    }

    private static void restrictToOwner(Path file) {
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        } catch (IOException | UnsupportedOperationException e) {
            // Best effort: filesystems without POSIX permissions cannot restrict the file here.
        }
    }

    private static PublicKey loadEd25519PublicKey(Path keyFile) throws IOException, GeneralSecurityException {
        val keyBytes = PemUtils.decodePemFromFile(keyFile);
        return KeyFactory.getInstance(ALGORITHM_ED25519).generatePublic(new X509EncodedKeySpec(keyBytes));
    }

}
