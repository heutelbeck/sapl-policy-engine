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
package io.sapl.pdp.configuration.bundle;

import lombok.experimental.UtilityClass;
import lombok.val;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Utility for signing and verifying SAPL bundle manifests using Ed25519.
 * <p>
 * This class provides cryptographic operations for bundle integrity and
 * authenticity verification. It uses Ed25519
 * signatures which provide strong security with small key and signature sizes.
 * </p>
 * <h2>Signing a Bundle</h2>
 *
 * <pre>{@code
 * // Prepare file contents
 * Map<String, String> files = Map.of("pdp.json", "{ \"algorithm\": \"DENY_OVERRIDES\" }", "policy.sapl",
 *         "policy \"test\" permit true");
 *
 * // Sign with Ed25519 private key
 * BundleManifest manifest = BundleSigner.sign(files, privateKey, "my-key-id");
 * }</pre>
 *
 * <h2>Verifying a Bundle</h2>
 *
 * <pre>{@code
 * // Verify signature and file integrity
 * BundleSigner.verify(manifest, actualFiles, publicKey);
 *
 * }</pre>
 *
 * @see BundleManifest
 * @see BundleBuilder
 */
@UtilityClass
public class BundleSigner {

    private static final Set<String> ED25519_ALGORITHM_NAMES = Set.of(BundleManifest.SIGNATURE_ALGORITHM, "EdDSA");

    private static final String ERROR_ED25519_NOT_AVAILABLE           = "Ed25519 algorithm not available.";
    private static final String ERROR_FILE_INTEGRITY_CHECK_FAILED     = "File integrity check failed for: %s. Expected hash: %s, actual: %s.";
    private static final String ERROR_INVALID_PRIVATE_KEY             = "Invalid private key: %s";
    private static final String ERROR_INVALID_PUBLIC_KEY              = "Invalid public key: %s";
    private static final String ERROR_INVALID_SIGNATURE_ENCODING      = "Invalid signature encoding: %s";
    private static final String ERROR_MANIFEST_IS_NOT_SIGNED          = "Manifest is not signed.";
    private static final String ERROR_MANIFEST_IS_NULL                = "Manifest is null.";
    private static final String ERROR_MANIFEST_NO_FILE_ENTRIES        = "Manifest contains no file entries.";
    private static final String ERROR_MANIFEST_SIGNATURE_VALUE_EMPTY  = "Manifest signature value is empty.";
    private static final String ERROR_MISSING_FILE_IN_BUNDLE          = "Missing file in bundle: %s.";
    private static final String ERROR_PRIVATE_KEY_MUST_BE_ED25519     = "Private key must be Ed25519, got: %s.";
    private static final String ERROR_PRIVATE_KEY_NULL                = "Private key must not be null.";
    private static final String ERROR_PUBLIC_KEY_MUST_BE_ED25519      = "Public key must be Ed25519, got: %s.";
    private static final String ERROR_PUBLIC_KEY_NULL                 = "Public key must not be null.";
    private static final String ERROR_SIGNATURE_DOES_NOT_MATCH        = "Signature verification failed: signature does not match manifest.";
    private static final String ERROR_SIGNATURE_VERIFICATION_ERROR    = "Signature verification error: %s";
    private static final String ERROR_SIGNING_FAILED                  = "Signing failed: %s";
    private static final String ERROR_UNEXPECTED_FILE_IN_BUNDLE       = "Unexpected file in bundle not covered by signature: %s.";
    private static final String ERROR_UNSUPPORTED_SIGNATURE_ALGORITHM = "Unsupported signature algorithm: %s.";

    /**
     * Signs bundle contents and creates a manifest with embedded signature.
     *
     * @param files
     * map of filename to file content
     * @param privateKey
     * Ed25519 private key for signing
     * @param keyId
     * identifier for the signing key (for key rotation support)
     *
     * @return signed manifest
     *
     * @throws BundleSignatureException
     * if signing fails
     */
    public static BundleManifest sign(Map<String, String> files, PrivateKey privateKey, String keyId) {
        validatePrivateKey(privateKey);

        val builder = BundleManifest.builder();

        for (val entry : files.entrySet()) {
            builder.addFile(entry.getKey(), entry.getValue());
        }

        val unsignedManifest = builder.buildUnsigned();
        val bytesToSign      = unsignedManifest.toCanonicalBytes();
        val signatureBytes   = createSignature(bytesToSign, privateKey);
        val signatureBase64  = Base64.getEncoder().encodeToString(signatureBytes);

        return builder.signature(keyId, signatureBase64).build();
    }

    /**
     * Verifies a bundle manifest signature and file integrity.
     *
     * @param manifest
     * the manifest to verify
     * @param actualFiles
     * map of filename to actual file content from the bundle
     * @param publicKey
     * Ed25519 public key for verification
     *
     * @throws BundleSignatureException
     * if verification fails
     */
    public static void verify(BundleManifest manifest, Map<String, String> actualFiles, PublicKey publicKey) {
        validatePublicKey(publicKey);
        verifyManifestStructure(manifest);
        verifySignature(manifest, publicKey);
        verifyFileIntegrity(manifest, actualFiles);
    }

    /**
     * Verifies only the cryptographic signature without checking file contents.
     * Useful for quick signature validation
     * before full verification.
     *
     * @param manifest
     * the manifest to verify
     * @param publicKey
     * Ed25519 public key for verification
     *
     * @throws BundleSignatureException
     * if signature verification fails
     */
    public static void verifySignatureOnly(BundleManifest manifest, PublicKey publicKey) {
        validatePublicKey(publicKey);
        verifyManifestStructure(manifest);
        verifySignature(manifest, publicKey);
    }

    /**
     * Checks if a manifest is signed.
     *
     * @param manifest
     * the manifest to check
     *
     * @return true if the manifest contains a signature
     */
    public static boolean isSigned(BundleManifest manifest) {
        return manifest != null && manifest.signature() != null && manifest.signature().value() != null;
    }

    private void validatePrivateKey(PrivateKey privateKey) {
        if (privateKey == null) {
            throw new BundleSignatureException(ERROR_PRIVATE_KEY_NULL);
        }
        if (!ED25519_ALGORITHM_NAMES.contains(privateKey.getAlgorithm())) {
            throw new BundleSignatureException(ERROR_PRIVATE_KEY_MUST_BE_ED25519.formatted(privateKey.getAlgorithm()));
        }
    }

    private void validatePublicKey(PublicKey publicKey) {
        if (publicKey == null) {
            throw new BundleSignatureException(ERROR_PUBLIC_KEY_NULL);
        }
        if (!ED25519_ALGORITHM_NAMES.contains(publicKey.getAlgorithm())) {
            throw new BundleSignatureException(ERROR_PUBLIC_KEY_MUST_BE_ED25519.formatted(publicKey.getAlgorithm()));
        }
    }

    private void verifyManifestStructure(BundleManifest manifest) {
        if (manifest == null) {
            throw new BundleSignatureException(ERROR_MANIFEST_IS_NULL);
        }
        if (manifest.signature() == null) {
            throw new BundleSignatureException(ERROR_MANIFEST_IS_NOT_SIGNED);
        }
        if (manifest.signature().value() == null || manifest.signature().value().isBlank()) {
            throw new BundleSignatureException(ERROR_MANIFEST_SIGNATURE_VALUE_EMPTY);
        }
        if (!BundleManifest.SIGNATURE_ALGORITHM.equals(manifest.signature().algorithm())) {
            throw new BundleSignatureException(
                    ERROR_UNSUPPORTED_SIGNATURE_ALGORITHM.formatted(manifest.signature().algorithm()));
        }
        if (manifest.files() == null || manifest.files().isEmpty()) {
            throw new BundleSignatureException(ERROR_MANIFEST_NO_FILE_ENTRIES);
        }
    }

    private void verifySignature(BundleManifest manifest, PublicKey publicKey) {
        val unsignedManifest = manifest.withoutSignature();
        val bytesToVerify    = unsignedManifest.toCanonicalBytes();

        byte[] signatureBytes;
        try {
            signatureBytes = Base64.getDecoder().decode(manifest.signature().value());
        } catch (IllegalArgumentException e) {
            throw new BundleSignatureException(ERROR_INVALID_SIGNATURE_ENCODING.formatted(e.getMessage()), e);
        }

        val isValid = verifySignatureBytes(bytesToVerify, signatureBytes, publicKey);
        if (!isValid) {
            throw new BundleSignatureException(ERROR_SIGNATURE_DOES_NOT_MATCH);
        }
    }

    private void verifyFileIntegrity(BundleManifest manifest, Map<String, String> actualFiles) {
        val expectedFiles = manifest.files();
        val actualKeys    = new TreeMap<>(actualFiles).keySet();
        val expectedKeys  = new TreeMap<>(expectedFiles).keySet();

        // Check for missing files (in manifest but not in bundle)
        for (val expectedFile : expectedKeys) {
            if (!actualKeys.contains(expectedFile)) {
                throw new BundleSignatureException(ERROR_MISSING_FILE_IN_BUNDLE.formatted(expectedFile));
            }
        }

        // Check for extra files (in bundle but not in manifest)
        for (val actualFile : actualKeys) {
            if (!expectedKeys.contains(actualFile)) {
                throw new BundleSignatureException(ERROR_UNEXPECTED_FILE_IN_BUNDLE.formatted(actualFile));
            }
        }

        // Verify each file's hash
        for (val entry : expectedFiles.entrySet()) {
            val filename     = entry.getKey();
            val expectedHash = entry.getValue();
            val actualHash   = BundleManifest.computeHash(actualFiles.get(filename));

            if (!expectedHash.equals(actualHash)) {
                throw new BundleSignatureException(
                        ERROR_FILE_INTEGRITY_CHECK_FAILED.formatted(filename, expectedHash, actualHash));
            }
        }
    }

    private byte[] createSignature(byte[] data, PrivateKey privateKey) {
        try {
            val signature = Signature.getInstance(BundleManifest.SIGNATURE_ALGORITHM);
            signature.initSign(privateKey);
            signature.update(data);
            return signature.sign();
        } catch (NoSuchAlgorithmException e) {
            throw new BundleSignatureException(ERROR_ED25519_NOT_AVAILABLE, e);
        } catch (InvalidKeyException e) {
            throw new BundleSignatureException(ERROR_INVALID_PRIVATE_KEY.formatted(e.getMessage()), e);
        } catch (SignatureException e) {
            throw new BundleSignatureException(ERROR_SIGNING_FAILED.formatted(e.getMessage()), e);
        }
    }

    private boolean verifySignatureBytes(byte[] data, byte[] signatureBytes, PublicKey publicKey) {
        try {
            val signature = Signature.getInstance(BundleManifest.SIGNATURE_ALGORITHM);
            signature.initVerify(publicKey);
            signature.update(data);
            return signature.verify(signatureBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new BundleSignatureException(ERROR_ED25519_NOT_AVAILABLE, e);
        } catch (InvalidKeyException e) {
            throw new BundleSignatureException(ERROR_INVALID_PUBLIC_KEY.formatted(e.getMessage()), e);
        } catch (SignatureException e) {
            throw new BundleSignatureException(ERROR_SIGNATURE_VERIFICATION_ERROR.formatted(e.getMessage()), e);
        }
    }
}
