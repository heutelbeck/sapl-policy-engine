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
import java.time.Instant;
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
 * // Or verify with expiration check
 * BundleSigner.verify(manifest, actualFiles, publicKey, Instant.now());
 * }</pre>
 *
 * @see BundleManifest
 * @see BundleBuilder
 */
@UtilityClass
public class BundleSigner {

    private static final Set<String> ED25519_ALGORITHM_NAMES = Set.of(BundleManifest.SIGNATURE_ALGORITHM, "EdDSA");

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
    public BundleManifest sign(Map<String, String> files, PrivateKey privateKey, String keyId) {
        return sign(files, privateKey, keyId, null);
    }

    /**
     * Signs bundle contents with an expiration time.
     *
     * @param files
     * map of filename to file content
     * @param privateKey
     * Ed25519 private key for signing
     * @param keyId
     * identifier for the signing key
     * @param expires
     * expiration timestamp, or null for no expiration
     *
     * @return signed manifest
     *
     * @throws BundleSignatureException
     * if signing fails
     */
    public BundleManifest sign(Map<String, String> files, PrivateKey privateKey, String keyId, Instant expires) {
        validatePrivateKey(privateKey);

        val builder = BundleManifest.builder().expires(expires);

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
    public void verify(BundleManifest manifest, Map<String, String> actualFiles, PublicKey publicKey) {
        verify(manifest, actualFiles, publicKey, null);
    }

    /**
     * Verifies a bundle manifest with expiration check.
     *
     * @param manifest
     * the manifest to verify
     * @param actualFiles
     * map of filename to actual file content from the bundle
     * @param publicKey
     * Ed25519 public key for verification
     * @param currentTime
     * current time for expiration check, or null to skip expiration check
     *
     * @throws BundleSignatureException
     * if verification fails
     */
    public void verify(BundleManifest manifest, Map<String, String> actualFiles, PublicKey publicKey,
            Instant currentTime) {
        validatePublicKey(publicKey);
        verifyManifestStructure(manifest);
        verifySignature(manifest, publicKey);
        verifyExpiration(manifest, currentTime);
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
    public void verifySignatureOnly(BundleManifest manifest, PublicKey publicKey) {
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
    public boolean isSigned(BundleManifest manifest) {
        return manifest != null && manifest.signature() != null && manifest.signature().value() != null;
    }

    private void validatePrivateKey(PrivateKey privateKey) {
        if (privateKey == null) {
            throw new BundleSignatureException("Private key must not be null.");
        }
        if (!ED25519_ALGORITHM_NAMES.contains(privateKey.getAlgorithm())) {
            throw new BundleSignatureException("Private key must be Ed25519, got: " + privateKey.getAlgorithm() + ".");
        }
    }

    private void validatePublicKey(PublicKey publicKey) {
        if (publicKey == null) {
            throw new BundleSignatureException("Public key must not be null.");
        }
        if (!ED25519_ALGORITHM_NAMES.contains(publicKey.getAlgorithm())) {
            throw new BundleSignatureException("Public key must be Ed25519, got: " + publicKey.getAlgorithm() + ".");
        }
    }

    private void verifyManifestStructure(BundleManifest manifest) {
        if (manifest == null) {
            throw new BundleSignatureException("Manifest is null.");
        }
        if (manifest.signature() == null) {
            throw new BundleSignatureException("Manifest is not signed.");
        }
        if (manifest.signature().value() == null || manifest.signature().value().isBlank()) {
            throw new BundleSignatureException("Manifest signature value is empty.");
        }
        if (!BundleManifest.SIGNATURE_ALGORITHM.equals(manifest.signature().algorithm())) {
            throw new BundleSignatureException(
                    "Unsupported signature algorithm: " + manifest.signature().algorithm() + ".");
        }
        if (manifest.files() == null || manifest.files().isEmpty()) {
            throw new BundleSignatureException("Manifest contains no file entries.");
        }
    }

    private void verifySignature(BundleManifest manifest, PublicKey publicKey) {
        val unsignedManifest = manifest.withoutSignature();
        val bytesToVerify    = unsignedManifest.toCanonicalBytes();

        byte[] signatureBytes;
        try {
            signatureBytes = Base64.getDecoder().decode(manifest.signature().value());
        } catch (IllegalArgumentException e) {
            throw new BundleSignatureException("Invalid signature encoding: " + e.getMessage(), e);
        }

        val isValid = verifySignatureBytes(bytesToVerify, signatureBytes, publicKey);
        if (!isValid) {
            throw new BundleSignatureException("Signature verification failed: signature does not match manifest.");
        }
    }

    private void verifyExpiration(BundleManifest manifest, Instant currentTime) {
        if (currentTime == null || manifest.expires() == null) {
            return;
        }
        if (currentTime.isAfter(manifest.expires())) {
            throw new BundleSignatureException(
                    "Manifest has expired. Expiration: " + manifest.expires() + ", current time: " + currentTime + ".");
        }
    }

    private void verifyFileIntegrity(BundleManifest manifest, Map<String, String> actualFiles) {
        val expectedFiles = manifest.files();
        val actualKeys    = new TreeMap<>(actualFiles).keySet();
        val expectedKeys  = new TreeMap<>(expectedFiles).keySet();

        // Check for missing files (in manifest but not in bundle)
        for (val expectedFile : expectedKeys) {
            if (!actualKeys.contains(expectedFile)) {
                throw new BundleSignatureException("Missing file in bundle: " + expectedFile + ".");
            }
        }

        // Check for extra files (in bundle but not in manifest)
        for (val actualFile : actualKeys) {
            if (!expectedKeys.contains(actualFile)) {
                throw new BundleSignatureException(
                        "Unexpected file in bundle not covered by signature: " + actualFile + ".");
            }
        }

        // Verify each file's hash
        for (val entry : expectedFiles.entrySet()) {
            val filename     = entry.getKey();
            val expectedHash = entry.getValue();
            val actualHash   = BundleManifest.computeHash(actualFiles.get(filename));

            if (!expectedHash.equals(actualHash)) {
                throw new BundleSignatureException("File integrity check failed for: " + filename + ". Expected hash: "
                        + expectedHash + ", actual: " + actualHash + ".");
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
            throw new BundleSignatureException("Ed25519 algorithm not available.", e);
        } catch (InvalidKeyException e) {
            throw new BundleSignatureException("Invalid private key: " + e.getMessage(), e);
        } catch (SignatureException e) {
            throw new BundleSignatureException("Signing failed: " + e.getMessage(), e);
        }
    }

    private boolean verifySignatureBytes(byte[] data, byte[] signatureBytes, PublicKey publicKey) {
        try {
            val signature = Signature.getInstance(BundleManifest.SIGNATURE_ALGORITHM);
            signature.initVerify(publicKey);
            signature.update(data);
            return signature.verify(signatureBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new BundleSignatureException("Ed25519 algorithm not available.", e);
        } catch (InvalidKeyException e) {
            throw new BundleSignatureException("Invalid public key: " + e.getMessage(), e);
        } catch (SignatureException e) {
            throw new BundleSignatureException("Signature verification error: " + e.getMessage(), e);
        }
    }
}
