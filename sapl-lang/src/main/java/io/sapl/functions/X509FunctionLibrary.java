/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.functions;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.Text;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;

/**
 * Provides functions for parsing and extracting information from X.509
 * certificates.
 * <p>
 * X.509 certificates are used in PKI (Public Key Infrastructure) for
 * authentication and encryption in protocols like TLS/SSL. This library
 * enables policy decisions based on certificate properties without requiring
 * external validation services.
 * <p>
 * Functions support both PEM and DER encoded certificates and extract commonly
 * used fields like subject, issuer, validity dates, and fingerprints.
 */
@UtilityClass
@FunctionLibrary(name = X509FunctionLibrary.NAME, description = X509FunctionLibrary.DESCRIPTION)
public class X509FunctionLibrary {

    public static final String NAME        = "x509";
    public static final String DESCRIPTION = "Functions for parsing and extracting information from X.509 certificates used in PKI and TLS/SSL.";

    private static final String RETURNS_TEXT = """
            {
                "type": "string"
            }
            """;

    private static final String RETURNS_BOOLEAN = """
            {
                "type": "boolean"
            }
            """;

    private static final String RETURNS_OBJECT = """
            {
                "type": "object"
            }
            """;

    private static final String RETURNS_ARRAY = """
            {
                "type": "array"
            }
            """;

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    /* Certificate Parsing */

    @Function(docs = """
            ```parseCertificate(TEXT certPem)```: Parses an X.509 certificate and returns its structure.

            Accepts certificates in PEM or DER format and returns a JSON object containing
            all certificate fields including subject, issuer, validity dates, serial number,
            and public key information.

            **Examples:**
            ```sapl
            policy "parse certificate"
            permit
            where
              var cert = x509.parseCertificate(certPem);
              cert.subject.commonName == "example.com";
              cert.serialNumber == "1234567890";
            ```
            """, schema = RETURNS_OBJECT)
    public Val parseCertificate(@Text Val certPem) {
        try {
            val certificate = decodeCertificate(certPem.getText());
            val certObject  = buildCertificateObject(certificate);
            return Val.of(certObject);
        } catch (Exception exception) {
            return Val.error("Failed to parse certificate: " + exception.getMessage());
        }
    }

    /* Field Extraction */

    @Function(docs = """
            ```extractSubjectDn(TEXT certPem)```: Extracts the Subject Distinguished Name from a certificate.

            Returns the full DN string in RFC 2253 format (e.g., "CN=example.com,O=Example Corp,C=US").

            **Examples:**
            ```sapl
            policy "check subject"
            permit
            where
              var subjectDn = x509.extractSubjectDn(certPem);
              subjectDn =~ "CN=.*\\.example\\.com";
            ```
            """, schema = RETURNS_TEXT)
    public Val extractSubjectDn(@Text Val certPem) {
        try {
            val certificate = decodeCertificate(certPem.getText());
            return Val.of(certificate.getSubjectX500Principal().getName());
        } catch (Exception exception) {
            return Val.error("Failed to extract subject DN: " + exception.getMessage());
        }
    }

    @Function(docs = """
            ```extractIssuerDn(TEXT certPem)```: Extracts the Issuer Distinguished Name from a certificate.

            Returns the full issuer DN string in RFC 2253 format.

            **Examples:**
            ```sapl
            policy "check issuer"
            permit
            where
              var issuerDn = x509.extractIssuerDn(certPem);
              issuerDn =~ "O=Trusted CA";
            ```
            """, schema = RETURNS_TEXT)
    public Val extractIssuerDn(@Text Val certPem) {
        try {
            val certificate = decodeCertificate(certPem.getText());
            return Val.of(certificate.getIssuerX500Principal().getName());
        } catch (Exception exception) {
            return Val.error("Failed to extract issuer DN: " + exception.getMessage());
        }
    }

    @Function(docs = """
            ```extractSerialNumber(TEXT certPem)```: Extracts the certificate serial number.

            Returns the serial number as a decimal string.

            **Examples:**
            ```sapl
            policy "check serial"
            permit
            where
              x509.extractSerialNumber(certPem) == "123456789";
            ```
            """, schema = RETURNS_TEXT)
    public Val extractSerialNumber(@Text Val certPem) {
        try {
            val certificate = decodeCertificate(certPem.getText());
            return Val.of(certificate.getSerialNumber().toString());
        } catch (Exception exception) {
            return Val.error("Failed to extract serial number: " + exception.getMessage());
        }
    }

    @Function(docs = """
            ```extractNotBefore(TEXT certPem)```: Extracts the certificate validity start date.

            Returns the date in ISO 8601 format (e.g., "2024-01-01T00:00:00Z").

            **Examples:**
            ```sapl
            policy "check validity start"
            permit
            where
              var notBefore = x509.extractNotBefore(certPem);
              notBefore < "2025-01-01T00:00:00Z";
            ```
            """, schema = RETURNS_TEXT)
    public Val extractNotBefore(@Text Val certPem) {
        try {
            val certificate = decodeCertificate(certPem.getText());
            val notBefore   = certificate.getNotBefore().toInstant().toString();
            return Val.of(notBefore);
        } catch (Exception exception) {
            return Val.error("Failed to extract notBefore: " + exception.getMessage());
        }
    }

    @Function(docs = """
            ```extractNotAfter(TEXT certPem)```: Extracts the certificate validity end date.

            Returns the date in ISO 8601 format (e.g., "2025-12-31T23:59:59Z").

            **Examples:**
            ```sapl
            policy "check expiration"
            permit
            where
              var notAfter = x509.extractNotAfter(certPem);
              notAfter > "2025-01-01T00:00:00Z";
            ```
            """, schema = RETURNS_TEXT)
    public Val extractNotAfter(@Text Val certPem) {
        try {
            val certificate = decodeCertificate(certPem.getText());
            val notAfter    = certificate.getNotAfter().toInstant().toString();
            return Val.of(notAfter);
        } catch (Exception exception) {
            return Val.error("Failed to extract notAfter: " + exception.getMessage());
        }
    }

    @Function(docs = """
            ```extractFingerprint(TEXT certPem, TEXT algorithm)```: Computes the certificate fingerprint.

            Calculates the fingerprint (hash of the certificate) using the specified algorithm.
            Returns the fingerprint as a lowercase hexadecimal string.

            Supported algorithms: "SHA-1", "SHA-256", "SHA-384", "SHA-512"

            **Examples:**
            ```sapl
            policy "pin certificate"
            permit
            where
              var fingerprint = x509.extractFingerprint(certPem, "SHA-256");
              fingerprint == "expected_fingerprint_value";
            ```
            """, schema = RETURNS_TEXT)
    public Val extractFingerprint(@Text Val certPem, @Text Val algorithm) {
        try {
            val certificate    = decodeCertificate(certPem.getText());
            val digest         = MessageDigest.getInstance(algorithm.getText());
            val fingerprint    = digest.digest(certificate.getEncoded());
            val hexFingerprint = HexFormat.of().formatHex(fingerprint);
            return Val.of(hexFingerprint);
        } catch (Exception exception) {
            return Val.error("Failed to compute fingerprint: " + exception.getMessage());
        }
    }

    @Function(docs = """
            ```extractSubjectAltNames(TEXT certPem)```: Extracts Subject Alternative Names from a certificate.

            Returns an array of SANs, which can include DNS names, IP addresses, email addresses,
            and URIs. Each entry is an object with 'type' and 'value' fields.

            **Examples:**
            ```sapl
            policy "check san"
            permit
            where
              var sans = x509.extractSubjectAltNames(certPem);
              "example.com" in sans[*].value;
            ```
            """, schema = RETURNS_ARRAY)
    public Val extractSubjectAltNames(@Text Val certPem) {
        try {
            val certificate = decodeCertificate(certPem.getText());
            val sans        = certificate.getSubjectAlternativeNames();
            val sansArray   = JSON.arrayNode();

            if (sans != null) {
                for (List<?> san : sans) {
                    val sanObject = JSON.objectNode();
                    val type      = (Integer) san.get(0);
                    val value     = san.get(1).toString();

                    sanObject.put("type", getSanTypeName(type));
                    sanObject.put("value", value);
                    sansArray.add(sanObject);
                }
            }

            return Val.of(sansArray);
        } catch (Exception exception) {
            return Val.error("Failed to extract subject alternative names: " + exception.getMessage());
        }
    }

    /* Validity Checks */

    @Function(docs = """
            ```isExpired(TEXT certPem)```: Checks if a certificate has expired.

            Returns true if the current time is after the certificate's notAfter date.

            **Examples:**
            ```sapl
            policy "reject expired"
            deny
            where
              x509.isExpired(clientCertificate);
            ```
            """, schema = RETURNS_BOOLEAN)
    public Val isExpired(@Text Val certPem) {
        try {
            val certificate = decodeCertificate(certPem.getText());
            val now         = new Date();
            val isExpired   = now.after(certificate.getNotAfter());
            return Val.of(isExpired);
        } catch (Exception exception) {
            return Val.error("Failed to check expiration: " + exception.getMessage());
        }
    }

    @Function(docs = """
            ```isValidAt(TEXT certPem, TEXT isoTimestamp)```: Checks if a certificate is valid at a specific time.

            Returns true if the given timestamp falls within the certificate's validity period
            (between notBefore and notAfter).

            **Examples:**
            ```sapl
            policy "check historical validity"
            permit
            where
              x509.isValidAt(certPem, "2024-06-15T12:00:00Z");
            ```
            """, schema = RETURNS_BOOLEAN)
    public Val isValidAt(@Text Val certPem, @Text Val isoTimestamp) {
        try {
            val certificate = decodeCertificate(certPem.getText());
            val timestamp   = Date.from(Instant.parse(isoTimestamp.getText()));
            val isValid     = !timestamp.before(certificate.getNotBefore())
                    && !timestamp.after(certificate.getNotAfter());
            return Val.of(isValid);
        } catch (Exception exception) {
            return Val.error("Failed to check validity: " + exception.getMessage());
        }
    }

    /**
     * Decodes a certificate from PEM or DER format.
     *
     * @param certString the certificate string
     * @return the X509Certificate
     * @throws Exception if decoding fails
     */
    private X509Certificate decodeCertificate(String certString) throws Exception {
        val    certificateFactory = CertificateFactory.getInstance("X.509");
        byte[] certBytes;

        if (certString.contains("BEGIN CERTIFICATE")) {
            val pemContent = certString.replaceAll("-----BEGIN CERTIFICATE-----", "")
                    .replaceAll("-----END CERTIFICATE-----", "").replaceAll("\\s+", "");
            certBytes = Base64.getDecoder().decode(pemContent);
        } else {
            certBytes = Base64.getDecoder().decode(certString);
        }

        val inputStream = new ByteArrayInputStream(certBytes);
        return (X509Certificate) certificateFactory.generateCertificate(inputStream);
    }

    /**
     * Builds a JSON object representation of a certificate.
     *
     * @param certificate the X509Certificate
     * @return the JSON object
     * @throws Exception if building fails
     */
    private ObjectNode buildCertificateObject(X509Certificate certificate) throws Exception {
        val certObject = JSON.objectNode();

        certObject.put("version", certificate.getVersion());
        certObject.put("serialNumber", certificate.getSerialNumber().toString());
        certObject.put("subject", certificate.getSubjectX500Principal().getName());
        certObject.put("issuer", certificate.getIssuerX500Principal().getName());
        certObject.put("notBefore", certificate.getNotBefore().toInstant().toString());
        certObject.put("notAfter", certificate.getNotAfter().toInstant().toString());
        certObject.put("signatureAlgorithm", certificate.getSigAlgName());

        val publicKeyInfo = JSON.objectNode();
        publicKeyInfo.put("algorithm", certificate.getPublicKey().getAlgorithm());
        publicKeyInfo.put("format", certificate.getPublicKey().getFormat());
        certObject.set("publicKey", publicKeyInfo);

        return certObject;
    }

    /**
     * Gets the name of a Subject Alternative Name type.
     *
     * @param type the type code
     * @return the type name
     */
    private String getSanTypeName(int type) {
        return switch (type) {
        case 0  -> "otherName";
        case 1  -> "rfc822Name";
        case 2  -> "dNSName";
        case 3  -> "x400Address";
        case 4  -> "directoryName";
        case 5  -> "ediPartyName";
        case 6  -> "uniformResourceIdentifier";
        case 7  -> "iPAddress";
        case 8  -> "registeredID";
        default -> "unknown";
        };
    }
}
