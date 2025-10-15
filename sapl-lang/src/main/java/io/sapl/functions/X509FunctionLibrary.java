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
import io.sapl.functions.util.crypto.CertificateUtils;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.format.DateTimeParseException;
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
        return withCertificate(certificatePem.getText(), certificate -> Val.of(buildCertificateObject(certificate)),
                "Failed to parse certificate");
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
        return withCertificate(certificatePem.getText(),
                certificate -> Val.of(certificate.getSubjectX500Principal().getName()), "Failed to extract subject DN");
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
        return withCertificate(certificatePem.getText(),
                certificate -> Val.of(certificate.getIssuerX500Principal().getName()), "Failed to extract issuer DN");
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
        return withCertificate(certificatePem.getText(),
                certificate -> Val.of(certificate.getSerialNumber().toString()), "Failed to extract serial number");
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
        return withCertificate(certificatePem.getText(),
                certificate -> Val.of(certificate.getNotBefore().toInstant().toString()),
                "Failed to extract notBefore");
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
        return withCertificate(certificatePem.getText(),
                certificate -> Val.of(certificate.getNotAfter().toInstant().toString()), "Failed to extract notAfter");
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
        return withCertificate(certificatePem.getText(),
                certificate -> computeFingerprint(certificate, algorithm.getText()), "Failed to compute fingerprint");
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
        return withCertificate(certificatePem.getText(), certificate -> {
            try {
                val subjectAltNames      = CertificateUtils.extractSubjectAlternativeNames(certificate);
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
        }, "Failed to extract subject alternative names");
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
        return withCertificate(certificatePem.getText(), certificate -> {
            val currentTime = new Date();
            val isExpired   = currentTime.after(certificate.getNotAfter());
            return Val.of(isExpired);
        }, "Failed to check expiration");
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
        return withCertificate(certificatePem.getText(), certificate -> {
            try {
                val timestamp = parseTimestamp(isoTimestamp.getText());
                val isValid   = !timestamp.before(certificate.getNotBefore())
                        && !timestamp.after(certificate.getNotAfter());
                return Val.of(isValid);
            } catch (PolicyEvaluationException exception) {
                return Val.error("Invalid timestamp: " + exception.getMessage());
            }
        }, "Failed to check validity");
    }

    /* Helper Methods */

    /**
     * Executes an operation on a parsed certificate with automatic error handling.
     * Parses the certificate string and applies the operation, catching any
     * exceptions and converting them to Val.error responses.
     *
     * @param certificateString the certificate string in PEM or DER format
     * @param operation the operation to perform on the certificate
     * @param errorPrefix the prefix for error messages
     * @return the result of the operation or a Val.error
     */
    private static Val withCertificate(String certificateString,
            java.util.function.Function<X509Certificate, Val> operation, String errorPrefix) {
        try {
            val certificate = CertificateUtils.parseCertificate(certificateString);
            return operation.apply(certificate);
        } catch (PolicyEvaluationException exception) {
            return Val.error(errorPrefix + ": " + exception.getMessage());
        }
    }

    /**
     * Computes the fingerprint of a certificate using the specified hash algorithm.
     *
     * @param certificate the X509Certificate to fingerprint
     * @param algorithm the hash algorithm name
     * @return a Val containing the hexadecimal fingerprint or an error
     */
    private static Val computeFingerprint(X509Certificate certificate, String algorithm) {
        try {
            val digest           = MessageDigest.getInstance(algorithm);
            val certificateBytes = CertificateUtils.encodeCertificate(certificate);
            val fingerprintBytes = digest.digest(certificateBytes);
            val fingerprintHex   = HexFormat.of().formatHex(fingerprintBytes);
            return Val.of(fingerprintHex);
        } catch (NoSuchAlgorithmException exception) {
            return Val.error("Hash algorithm not supported: " + algorithm);
        } catch (PolicyEvaluationException exception) {
            return Val.error("Failed to encode certificate: " + exception.getMessage());
        }
    }

    /**
     * Parses an ISO 8601 timestamp string to a Date object.
     *
     * @param isoTimestamp the ISO 8601 timestamp string
     * @return the parsed Date
     * @throws PolicyEvaluationException if the timestamp format is invalid
     */
    private static Date parseTimestamp(String isoTimestamp) {
        try {
            return Date.from(Instant.parse(isoTimestamp));
        } catch (DateTimeParseException exception) {
            throw new PolicyEvaluationException("Invalid ISO 8601 timestamp format: " + isoTimestamp, exception);
        }
    }

    /**
     * Builds a JSON object representation of a certificate with all fields.
     *
     * @param certificate the X509Certificate to represent
     * @return the JSON object containing certificate information
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
     * Converts a Subject Alternative Name type code to its name.
     *
     * @param type the SAN type code
     * @return the corresponding type name
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
