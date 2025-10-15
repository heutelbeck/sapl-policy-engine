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
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.Text;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Collection;
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

    private static final String PEM_CERTIFICATE_BEGIN = "-----BEGIN CERTIFICATE-----";
    private static final String PEM_CERTIFICATE_END   = "-----END CERTIFICATE-----";

    private static final int SAN_TYPE_OTHER_NAME    = 0;
    private static final int SAN_TYPE_RFC822_NAME   = 1;
    private static final int SAN_TYPE_DNS_NAME      = 2;
    private static final int SAN_TYPE_X400_ADDRESS  = 3;
    private static final int SAN_TYPE_DIRECTORY     = 4;
    private static final int SAN_TYPE_EDI_PARTY     = 5;
    private static final int SAN_TYPE_URI           = 6;
    private static final int SAN_TYPE_IP_ADDRESS    = 7;
    private static final int SAN_TYPE_REGISTERED_ID = 8;

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
    public Val parseCertificate(@Text Val certificatePem) {
        try {
            val certificate       = decodeCertificate(certificatePem.getText());
            val certificateObject = buildCertificateObject(certificate);
            return Val.of(certificateObject);
        } catch (PolicyEvaluationException exception) {
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
    public Val extractSubjectDn(@Text Val certificatePem) {
        try {
            val certificate = decodeCertificate(certificatePem.getText());
            return Val.of(certificate.getSubjectX500Principal().getName());
        } catch (PolicyEvaluationException exception) {
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
    public Val extractIssuerDn(@Text Val certificatePem) {
        try {
            val certificate = decodeCertificate(certificatePem.getText());
            return Val.of(certificate.getIssuerX500Principal().getName());
        } catch (PolicyEvaluationException exception) {
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
    public Val extractSerialNumber(@Text Val certificatePem) {
        try {
            val certificate = decodeCertificate(certificatePem.getText());
            return Val.of(certificate.getSerialNumber().toString());
        } catch (PolicyEvaluationException exception) {
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
    public Val extractNotBefore(@Text Val certificatePem) {
        try {
            val certificate = decodeCertificate(certificatePem.getText());
            val notBefore   = certificate.getNotBefore().toInstant().toString();
            return Val.of(notBefore);
        } catch (PolicyEvaluationException exception) {
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
    public Val extractNotAfter(@Text Val certificatePem) {
        try {
            val certificate = decodeCertificate(certificatePem.getText());
            val notAfter    = certificate.getNotAfter().toInstant().toString();
            return Val.of(notAfter);
        } catch (PolicyEvaluationException exception) {
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
    public Val extractFingerprint(@Text Val certificatePem, @Text Val algorithm) {
        try {
            val certificate      = decodeCertificate(certificatePem.getText());
            val digest           = createMessageDigest(algorithm.getText());
            val certificateBytes = encodeCertificate(certificate);
            val fingerprintBytes = digest.digest(certificateBytes);
            val fingerprintHex   = HexFormat.of().formatHex(fingerprintBytes);
            return Val.of(fingerprintHex);
        } catch (PolicyEvaluationException exception) {
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
    public Val extractSubjectAltNames(@Text Val certificatePem) {
        try {
            val certificate          = decodeCertificate(certificatePem.getText());
            val subjectAltNames      = extractSubjectAlternativeNames(certificate);
            val subjectAltNamesArray = JSON.arrayNode();

            if (subjectAltNames != null) {
                for (List<?> subjectAltName : subjectAltNames) {
                    val sanObject = JSON.objectNode();
                    val sanType   = (Integer) subjectAltName.get(0);
                    val sanValue  = subjectAltName.get(1).toString();

                    sanObject.put("type", getSanTypeName(sanType));
                    sanObject.put("value", sanValue);
                    subjectAltNamesArray.add(sanObject);
                }
            }

            return Val.of(subjectAltNamesArray);
        } catch (PolicyEvaluationException exception) {
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
    public Val isExpired(@Text Val certificatePem) {
        try {
            val certificate = decodeCertificate(certificatePem.getText());
            val currentTime = new Date();
            val isExpired   = currentTime.after(certificate.getNotAfter());
            return Val.of(isExpired);
        } catch (PolicyEvaluationException exception) {
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
    public Val isValidAt(@Text Val certificatePem, @Text Val isoTimestamp) {
        try {
            val certificate = decodeCertificate(certificatePem.getText());
            val timestamp   = parseTimestamp(isoTimestamp.getText());
            val isValid     = !timestamp.before(certificate.getNotBefore())
                    && !timestamp.after(certificate.getNotAfter());
            return Val.of(isValid);
        } catch (PolicyEvaluationException exception) {
            return Val.error("Failed to check validity: " + exception.getMessage());
        }
    }

    /**
     * Decodes a certificate from PEM or DER format.
     *
     * @param certificateString the certificate string
     * @return the X509Certificate
     * @throws PolicyEvaluationException if decoding fails
     */
    private static X509Certificate decodeCertificate(String certificateString) {
        val certificateFactory = getCertificateFactory();
        val certificateBytes   = extractCertificateBytes(certificateString);
        val inputStream        = new ByteArrayInputStream(certificateBytes);

        try {
            return (X509Certificate) certificateFactory.generateCertificate(inputStream);
        } catch (CertificateException exception) {
            throw new PolicyEvaluationException("Failed to parse X.509 certificate", exception);
        }
    }

    /**
     * Gets the X.509 certificate factory.
     *
     * @return the CertificateFactory
     * @throws PolicyEvaluationException if X.509 is not supported
     */
    private static CertificateFactory getCertificateFactory() {
        try {
            return CertificateFactory.getInstance("X.509");
        } catch (CertificateException exception) {
            throw new PolicyEvaluationException("X.509 certificate factory not available", exception);
        }
    }

    /**
     * Extracts certificate bytes from PEM or Base64 DER format.
     *
     * @param certificateString the certificate string
     * @return the decoded certificate bytes
     * @throws PolicyEvaluationException if Base64 decoding fails
     */
    private static byte[] extractCertificateBytes(String certificateString) {
        if (certificateString.contains("BEGIN CERTIFICATE")) {
            val pemContent = certificateString.replace(PEM_CERTIFICATE_BEGIN, "").replace(PEM_CERTIFICATE_END, "")
                    .replaceAll("\\s+", "");
            return decodeBase64(pemContent);
        }
        return decodeBase64(certificateString);
    }

    /**
     * Decodes Base64 content.
     *
     * @param content the Base64-encoded content
     * @return the decoded bytes
     * @throws PolicyEvaluationException if decoding fails
     */
    private static byte[] decodeBase64(String content) {
        try {
            return Base64.getDecoder().decode(content);
        } catch (IllegalArgumentException exception) {
            throw new PolicyEvaluationException("Invalid Base64 encoding in certificate: " + exception.getMessage(),
                    exception);
        }
    }

    /**
     * Creates a MessageDigest for the specified algorithm.
     *
     * @param algorithm the hash algorithm name
     * @return the MessageDigest
     * @throws PolicyEvaluationException if algorithm is not supported
     */
    private static MessageDigest createMessageDigest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException exception) {
            throw new PolicyEvaluationException("Hash algorithm not supported: " + algorithm, exception);
        }
    }

    /**
     * Encodes a certificate to its DER byte representation.
     *
     * @param certificate the X509Certificate
     * @return the encoded bytes
     * @throws PolicyEvaluationException if encoding fails
     */
    private static byte[] encodeCertificate(X509Certificate certificate) {
        try {
            return certificate.getEncoded();
        } catch (CertificateEncodingException exception) {
            throw new PolicyEvaluationException("Failed to encode certificate", exception);
        }
    }

    /**
     * Extracts subject alternative names from a certificate.
     *
     * @param certificate the X509Certificate
     * @return the collection of SANs, or null if none present
     * @throws PolicyEvaluationException if extraction fails
     */
    private static Collection<List<?>> extractSubjectAlternativeNames(X509Certificate certificate) {
        try {
            return certificate.getSubjectAlternativeNames();
        } catch (CertificateException exception) {
            throw new PolicyEvaluationException("Failed to extract subject alternative names", exception);
        }
    }

    /**
     * Parses an ISO 8601 timestamp to a Date.
     *
     * @param isoTimestamp the ISO 8601 timestamp string
     * @return the Date
     * @throws PolicyEvaluationException if parsing fails
     */
    private static Date parseTimestamp(String isoTimestamp) {
        try {
            return Date.from(Instant.parse(isoTimestamp));
        } catch (DateTimeParseException exception) {
            throw new PolicyEvaluationException("Invalid ISO 8601 timestamp format: " + isoTimestamp, exception);
        }
    }

    /**
     * Builds a JSON object representation of a certificate.
     *
     * @param certificate the X509Certificate
     * @return the JSON object
     */
    private static ObjectNode buildCertificateObject(X509Certificate certificate) {
        val certificateObject = JSON.objectNode();

        certificateObject.put("version", certificate.getVersion());
        certificateObject.put("serialNumber", certificate.getSerialNumber().toString());
        certificateObject.put("subject", certificate.getSubjectX500Principal().getName());
        certificateObject.put("issuer", certificate.getIssuerX500Principal().getName());
        certificateObject.put("notBefore", certificate.getNotBefore().toInstant().toString());
        certificateObject.put("notAfter", certificate.getNotAfter().toInstant().toString());
        certificateObject.put("signatureAlgorithm", certificate.getSigAlgName());

        val publicKeyInfo = JSON.objectNode();
        publicKeyInfo.put("algorithm", certificate.getPublicKey().getAlgorithm());
        publicKeyInfo.put("format", certificate.getPublicKey().getFormat());
        certificateObject.set("publicKey", publicKeyInfo);

        return certificateObject;
    }

    /**
     * Gets the name of a Subject Alternative Name type.
     *
     * @param type the type code
     * @return the type name
     */
    private static String getSanTypeName(int type) {
        return switch (type) {
        case SAN_TYPE_OTHER_NAME    -> "otherName";
        case SAN_TYPE_RFC822_NAME   -> "rfc822Name";
        case SAN_TYPE_DNS_NAME      -> "dNSName";
        case SAN_TYPE_X400_ADDRESS  -> "x400Address";
        case SAN_TYPE_DIRECTORY     -> "directoryName";
        case SAN_TYPE_EDI_PARTY     -> "ediPartyName";
        case SAN_TYPE_URI           -> "uniformResourceIdentifier";
        case SAN_TYPE_IP_ADDRESS    -> "iPAddress";
        case SAN_TYPE_REGISTERED_ID -> "registeredID";
        default                     -> "unknown";
        };
    }
}
