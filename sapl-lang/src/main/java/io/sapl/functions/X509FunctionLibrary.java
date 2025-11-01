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
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;

/**
 * Functions for making access control decisions based on X.509 certificate
 * properties in mutual TLS and client certificate authentication scenarios.
 * <p>
 * Common use cases include verifying client certificates in mTLS connections,
 * implementing certificate pinning, checking certificate validity during
 * maintenance windows, and restricting access based on certificate subjects or
 * SANs. All functions work with both PEM and DER encoded certificates.
 * <p>
 * Example - mTLS client authorization:
 *
 * <pre>{@code
 * policy "allow known clients"
 * permit action == "api.call"
 * where
 *   var clientCert = x509.parseCertificate(request.clientCertificate);
 *   clientCert.subject =~ "O=Trusted Partners";
 *   !x509.isExpired(request.clientCertificate);
 *   x509.hasDnsName(request.clientCertificate, resource.serviceName);
 * }</pre>
 */
@UtilityClass
@FunctionLibrary(name = X509FunctionLibrary.NAME, description = X509FunctionLibrary.DESCRIPTION)
public class X509FunctionLibrary {

    public static final String NAME        = "x509";
    public static final String DESCRIPTION = "Functions for certificate-based access control in mTLS and PKI scenarios.";

    private static final String RETURNS_TEXT = """
            {
                "type": "string"
            }
            """;

    private static final String RETURNS_NUMBER = """
            {
                "type": "number"
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

            Accepts certificates in PEM or DER format and returns a JSON object containing all
            certificate fields including subject, issuer, validity dates, serial number, and
            public key information. Use this when multiple certificate properties are needed
            in a single policy.

            Example - Validate multiple certificate properties for mTLS:
            ```sapl
            policy "require valid partner certificate"
            permit action == "api.call"
            where
              var cert = x509.parseCertificate(request.clientCertificate);
              cert.subject =~ "O=Trusted Partners Inc";
              cert.issuer =~ "CN=Internal CA";
              cert.serialNumber in resource.allowedSerials;
            ```
            """, schema = RETURNS_OBJECT)
    public static Val parseCertificate(@Text Val certificatePem) {
        return withCertificate(certificatePem.getText(), certificate -> Val.of(buildCertificateObject(certificate)),
                "Failed to parse certificate");
    }

    /* Field Extraction */

    @Function(docs = """
            ```extractSubjectDn(TEXT certPem)```: Extracts the Subject Distinguished Name.

            Returns the full DN string in RFC 2253 format. Use this for matching against
            specific organizations or organizational units in certificate-based access control.

            Example - Restrict access to specific department:
            ```sapl
            policy "allow hr department only"
            permit action == "read" && resource.type == "personnel-records"
            where
              var subjectDn = x509.extractSubjectDn(request.clientCertificate);
              subjectDn =~ "OU=Human Resources,O=Acme Corp";
            ```
            """, schema = RETURNS_TEXT)
    public static Val extractSubjectDn(@Text Val certificatePem) {
        return withCertificate(certificatePem.getText(),
                certificate -> Val.of(certificate.getSubjectX500Principal().getName()), "Failed to extract subject DN");
    }

    @Function(docs = """
            ```extractIssuerDn(TEXT certPem)```: Extracts the Issuer Distinguished Name.

            Returns the full issuer DN string in RFC 2253 format. Use this to verify
            certificates were issued by trusted CAs.

            Example - Require certificates from internal CA:
            ```sapl
            policy "internal ca only"
            permit
            where
              var issuerDn = x509.extractIssuerDn(request.clientCertificate);
              issuerDn =~ "CN=Acme Internal CA,O=Acme Corp";
            ```
            """, schema = RETURNS_TEXT)
    public static Val extractIssuerDn(@Text Val certificatePem) {
        return withCertificate(certificatePem.getText(),
                certificate -> Val.of(certificate.getIssuerX500Principal().getName()), "Failed to extract issuer DN");
    }

    @Function(docs = """
            ```extractCommonName(TEXT certPem)```: Extracts the Common Name from the subject.

            Returns just the CN field from the certificate subject, which typically contains
            the hostname or entity name. This is simpler than parsing the full DN when only
            the CN is needed.

            Example - Verify service identity in mTLS:
            ```sapl
            policy "service-to-service auth"
            permit action == "invoke"
            where
              var serviceName = x509.extractCommonName(request.clientCertificate);
              serviceName in resource.allowedServices;
            ```
            """, schema = RETURNS_TEXT)
    public static Val extractCommonName(@Text Val certificatePem) {
        return withCertificate(certificatePem.getText(), certificate -> {
            val subjectDn  = certificate.getSubjectX500Principal().getName();
            val commonName = extractCnFromDn(subjectDn);
            if (commonName == null) {
                return Val.error("Certificate subject does not contain a Common Name.");
            }
            return Val.of(commonName);
        }, "Failed to extract common name");
    }

    @Function(docs = """
            ```extractSerialNumber(TEXT certPem)```: Extracts the certificate serial number.

            Returns the serial number as a decimal string. Use this for certificate revocation
            checking or tracking specific certificates in audit logs.

            Example - Block revoked certificates:
            ```sapl
            policy "check revocation list"
            deny
            where
              var serial = x509.extractSerialNumber(request.clientCertificate);
              serial in data.revokedSerials;
            ```
            """, schema = RETURNS_TEXT)
    public static Val extractSerialNumber(@Text Val certificatePem) {
        return withCertificate(certificatePem.getText(),
                certificate -> Val.of(certificate.getSerialNumber().toString()), "Failed to extract serial number");
    }

    @Function(docs = """
            ```extractNotBefore(TEXT certPem)```: Extracts the certificate validity start date.

            Returns the date in ISO 8601 format. Use this to implement time-based access
            restrictions or maintenance windows.

            Example - Enforce staged certificate rollout:
            ```sapl
            policy "new certificates only after cutover"
            permit
            where
              var notBefore = x509.extractNotBefore(request.clientCertificate);
              notBefore >= "2025-01-01T00:00:00Z";
            ```
            """, schema = RETURNS_TEXT)
    public static Val extractNotBefore(@Text Val certificatePem) {
        return withCertificate(certificatePem.getText(),
                certificate -> Val.of(certificate.getNotBefore().toInstant().toString()),
                "Failed to extract notBefore date");
    }

    @Function(docs = """
            ```extractNotAfter(TEXT certPem)```: Extracts the certificate validity end date.

            Returns the date in ISO 8601 format. Use this to implement proactive certificate
            renewal warnings or temporary access grants.

            Example - Warn about expiring certificates:
            ```sapl
            policy "certificate expiring soon"
            permit
            where
              var notAfter = x509.extractNotAfter(request.clientCertificate);
              notAfter < time.plusDays(time.now(), 30);
            advice
              "certificate-renewal-warning"
            ```
            """, schema = RETURNS_TEXT)
    public static Val extractNotAfter(@Text Val certificatePem) {
        return withCertificate(certificatePem.getText(),
                certificate -> Val.of(certificate.getNotAfter().toInstant().toString()),
                "Failed to extract notAfter date");
    }

    /* Fingerprint Computation */

    @Function(docs = """
            ```extractFingerprint(TEXT certPem, TEXT algorithm)```: Computes the certificate fingerprint.

            Computes the hash of the entire certificate using the specified algorithm (SHA-1,
            SHA-256, or SHA-512). Returns the fingerprint as a hexadecimal string. Use this for
            certificate pinning to ensure the exact certificate is being used.

            Example - Certificate pinning for critical services:
            ```sapl
            policy "pin database certificate"
            permit action == "query" && resource.type == "production-db"
            where
              var fingerprint = x509.extractFingerprint(request.clientCertificate, "SHA-256");
              x509.matchesFingerprint(request.clientCertificate, resource.expectedFingerprint, "SHA-256");
            ```
            """, schema = RETURNS_TEXT)
    public static Val extractFingerprint(@Text Val certificatePem, @Text Val algorithm) {
        return withCertificate(certificatePem.getText(),
                certificate -> computeFingerprint(certificate, algorithm.getText()), "Failed to extract fingerprint");
    }

    @Function(docs = """
            ```matchesFingerprint(TEXT certPem, TEXT expectedFingerprint, TEXT algorithm)```: Checks if certificate matches expected fingerprint.

            Computes the certificate fingerprint using the specified algorithm and compares it
            to the expected value. This implements certificate pinning to ensure the exact
            certificate is being used, preventing man-in-the-middle attacks.

            Example - Pin production service certificates:
            ```sapl
            policy "verify pinned certificate"
            permit action == "connect" && resource.type == "payment-gateway"
            where
              x509.matchesFingerprint(
                request.clientCertificate,
                "a1b2c3d4e5f6...",
                "SHA-256"
              );
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val matchesFingerprint(@Text Val certificatePem, @Text Val expectedFingerprint, @Text Val algorithm) {
        return withCertificate(certificatePem.getText(), certificate -> {
            val fingerprintResult = computeFingerprint(certificate, algorithm.getText());
            if (fingerprintResult.isError()) {
                return fingerprintResult;
            }
            val matches = fingerprintResult.getText().equalsIgnoreCase(expectedFingerprint.getText());
            return Val.of(matches);
        }, "Failed to match fingerprint");
    }

    /* Subject Alternative Names */

    @Function(docs = """
            ```extractSubjectAltNames(TEXT certPem)```: Extracts Subject Alternative Names.

            Returns an array of SANs, which can include DNS names, IP addresses, email addresses,
            and URIs. Each entry is an object with type and value fields. Use this when the
            certificate subject doesn't match the accessed resource name.

            Example - Check SAN for virtual hosts:
            ```sapl
            policy "allow san-based routing"
            permit action == "route"
            where
              var sans = x509.extractSubjectAltNames(request.clientCertificate);
              resource.hostname in sans[*].value;
            ```
            """, schema = RETURNS_ARRAY)
    public static Val extractSubjectAltNames(@Text Val certificatePem) {
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
            } catch (CertificateParsingException exception) {
                return Val.error("Failed to extract subject alternative names: " + exception.getMessage() + ".");
            }
        }, "Failed to extract subject alternative names");
    }

    @Function(docs = """
            ```hasDnsName(TEXT certPem, TEXT dnsName)```: Checks if certificate contains a specific DNS name.

            Checks both the subject CN and all Subject Alternative Names for the specified DNS
            name. This is simpler than extracting SANs and checking manually, and handles
            wildcard certificates correctly.

            Example - Verify certificate is valid for accessed domain:
            ```sapl
            policy "validate domain match"
            permit action == "connect"
            where
              x509.hasDnsName(request.serverCertificate, resource.domain);
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val hasDnsName(@Text Val certificatePem, @Text Val dnsName) {
        return withCertificate(certificatePem.getText(), certificate -> {
            val targetDnsName = dnsName.getText().toLowerCase();

            val commonName = extractCnFromDn(certificate.getSubjectX500Principal().getName());
            if (commonName != null && matchesDnsName(commonName, targetDnsName)) {
                return Val.of(true);
            }

            try {
                val subjectAltNames = CertificateUtils.extractSubjectAlternativeNames(certificate);
                if (subjectAltNames != null) {
                    for (List<?> san : subjectAltNames) {
                        val sanType  = (Integer) san.get(0);
                        val sanValue = san.get(1).toString();
                        if (sanType == SAN_TYPE_DNS_NAME && matchesDnsName(sanValue, targetDnsName)) {
                            return Val.of(true);
                        }
                    }
                }
            } catch (CertificateParsingException exception) {
                return Val.error("Failed to check DNS names: " + exception.getMessage() + ".");
            }

            return Val.of(false);
        }, "Failed to check DNS name");
    }

    @Function(docs = """
            ```hasIpAddress(TEXT certPem, TEXT ipAddress)```: Checks if certificate contains a specific IP address.

            Checks Subject Alternative Names for the specified IP address. Use this when
            authorizing connections from IP-identified clients rather than DNS-named hosts.

            Example - Authorize by client IP:
            ```sapl
            policy "allow specific ips"
            permit action == "connect"
            where
              x509.hasIpAddress(request.clientCertificate, request.sourceIp);
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val hasIpAddress(@Text Val certificatePem, @Text Val ipAddress) {
        return withCertificate(certificatePem.getText(), certificate -> {
            val targetIp = ipAddress.getText();

            try {
                val subjectAltNames = CertificateUtils.extractSubjectAlternativeNames(certificate);
                return Val.of(containsIpAddress(subjectAltNames, targetIp));
            } catch (CertificateParsingException exception) {
                return Val.error("Failed to check IP addresses: " + exception.getMessage() + ".");
            }
        }, "Failed to check IP address");
    }

    /**
     * Checks if the given Subject Alternative Names contain a specific IP address.
     *
     * @param subjectAltNames the collection of SANs from a certificate
     * @param targetIp the target IP address to search for
     * @return true if the IP address is found in the SANs
     */
    private static boolean containsIpAddress(java.util.Collection<List<?>> subjectAltNames, String targetIp) {
        if (subjectAltNames == null) {
            return false;
        }

        for (List<?> san : subjectAltNames) {
            val sanType = (Integer) san.get(0);
            if (sanType == SAN_TYPE_IP_ADDRESS) {
                val sanValue     = san.get(1);
                val normalizedIp = extractIpAddress(sanValue);
                if (normalizedIp != null && normalizedIp.equals(targetIp)) {
                    return true;
                }
            }
        }

        return false;
    }
    /* Validity Checks */

    @Function(docs = """
            ```isExpired(TEXT certPem)```: Checks if a certificate has expired.

            Returns true if the current time is after the certificate's notAfter date. Use this
            as a basic validity check before allowing access.

            Example - Reject expired certificates:
            ```sapl
            policy "reject expired certificates"
            deny
            where
              x509.isExpired(request.clientCertificate);
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val isExpired(@Text Val certificatePem) {
        return withCertificate(certificatePem.getText(), certificate -> {
            val currentTime = new Date();
            val isExpired   = currentTime.after(certificate.getNotAfter());
            return Val.of(isExpired);
        }, "Failed to check expiration");
    }

    @Function(docs = """
            ```isValidAt(TEXT certPem, TEXT isoTimestamp)```: Checks if certificate is valid at a specific time.

            Returns true if the given timestamp falls within the certificate's validity period
            (between notBefore and notAfter). Use this for time-based access or historical
            audit validation.

            Example - Check validity during maintenance window:
            ```sapl
            policy "maintenance window access"
            permit action == "admin" && resource.type == "production"
            where
              var maintenanceStart = "2025-06-15T02:00:00Z";
              x509.isValidAt(request.adminCertificate, maintenanceStart);
            ```
            """, schema = RETURNS_BOOLEAN)
    public static Val isValidAt(@Text Val certificatePem, @Text Val isoTimestamp) {
        return withCertificate(certificatePem.getText(), certificate -> {
            try {
                val timestamp = parseTimestamp(isoTimestamp.getText());
                val isValid   = !timestamp.before(certificate.getNotBefore())
                        && !timestamp.after(certificate.getNotAfter());
                return Val.of(isValid);
            } catch (PolicyEvaluationException exception) {
                return Val.error("Invalid timestamp: " + exception.getMessage() + ".");
            }
        }, "Failed to check validity");
    }

    @Function(docs = """
            ```remainingValidityDays(TEXT certPem)```: Returns the number of days until certificate expires.

            Calculates how many days remain until the certificate's notAfter date. Returns a
            negative number if already expired. Use this to trigger certificate renewal warnings
            or implement graceful certificate rotation.

            Example - Trigger renewal warning:
            ```sapl
            policy "certificate renewal warning"
            permit
            where
              var daysRemaining = x509.remainingValidityDays(request.clientCertificate);
              daysRemaining > 0;
            advice
              var daysRemaining = x509.remainingValidityDays(request.clientCertificate);
              daysRemaining < 30;
            obligation
              {
                "type": "certificate-expiring-soon",
                "daysRemaining": daysRemaining
              }
            ```
            """, schema = RETURNS_NUMBER)
    public static Val remainingValidityDays(@Text Val certificatePem) {
        return withCertificate(certificatePem.getText(), certificate -> {
            val now           = Instant.now();
            val notAfter      = certificate.getNotAfter().toInstant();
            val daysRemaining = ChronoUnit.DAYS.between(now, notAfter);
            return Val.of(daysRemaining);
        }, "Failed to calculate remaining validity");
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
        } catch (CertificateException | PolicyEvaluationException exception) {
            val message      = exception.getMessage();
            val errorMessage = message != null && message.endsWith(".") ? errorPrefix + ": " + message
                    : errorPrefix + ": " + message + ".";
            return Val.error(errorMessage);
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
            return Val.error("Hash algorithm not supported: " + algorithm + ".");
        } catch (CertificateEncodingException exception) {
            return Val.error("Failed to encode certificate: " + exception.getMessage() + ".");
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
     * Extracts the Common Name from a Distinguished Name string.
     * Handles DN strings in RFC 2253 format.
     *
     * @param dn the Distinguished Name string
     * @return the Common Name or null if not present
     */
    private static String extractCnFromDn(String dn) {
        val parts = dn.split(",");
        for (String part : parts) {
            val trimmed = part.trim();
            if (trimmed.startsWith("CN=")) {
                return trimmed.substring(3);
            }
        }
        return null;
    }

    /**
     * Checks if a DNS name from a certificate matches a target DNS name.
     * Handles wildcard certificates (e.g., *.example.com).
     *
     * @param certificateDnsName the DNS name from the certificate (may contain
     * wildcard)
     * @param targetDnsName the target DNS name to match
     * @return true if the names match
     */
    private static boolean matchesDnsName(String certificateDnsName, String targetDnsName) {
        val certName = certificateDnsName.toLowerCase();
        val target   = targetDnsName.toLowerCase();

        if (certName.equals(target)) {
            return true;
        }

        if (certName.startsWith("*.")) {
            val certBaseDomain = certName.substring(2);
            val targetParts    = target.split("\\.", 2);
            return targetParts.length == 2 && targetParts[1].equals(certBaseDomain);
        }

        return false;
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

    /**
     * Extracts an IP address string from X.509 certificate SAN representation.
     * X.509 certificates can store IP addresses as either String or byte array.
     * This method handles both formats and converts to standard dotted-decimal
     * notation.
     *
     * @param sanValue the raw SAN value (String or byte array)
     * @return the IP address in standard format, or null if conversion fails
     */
    private static String extractIpAddress(Object sanValue) {
        if (sanValue instanceof String stringValue) {
            return normalizeIpAddress(stringValue);
        }

        if (sanValue instanceof byte[] bytes) {
            return convertBytesToIpAddress(bytes);
        }

        return null;
    }

    /**
     * Normalizes an IP address string from various string representations.
     * Handles formats with leading slashes or hostname prefixes.
     *
     * @param rawIpValue the raw IP address string
     * @return the normalized IP address string
     */
    private static String normalizeIpAddress(String rawIpValue) {
        if (rawIpValue == null || rawIpValue.isEmpty()) {
            return rawIpValue;
        }

        // Handle InetAddress toString format (e.g., "/192.168.1.1")
        if (rawIpValue.startsWith("/")) {
            return rawIpValue.substring(1);
        }

        // Handle potential hostname/IP format (e.g., "hostname/192.168.1.1")
        val slashIndex = rawIpValue.lastIndexOf('/');
        if (slashIndex > 0) {
            return rawIpValue.substring(slashIndex + 1);
        }

        return rawIpValue;
    }

    /**
     * Converts IP address bytes to standard string notation.
     * Handles both IPv4 (4 bytes) and IPv6 (16 bytes) addresses.
     *
     * @param bytes the IP address bytes
     * @return the IP address in standard notation
     */
    private static String convertBytesToIpAddress(byte[] bytes) {
        if (bytes == null) {
            return null;
        }

        if (bytes.length == 4) {
            // IPv4: convert to dotted decimal
            return String.format("%d.%d.%d.%d", bytes[0] & 0xFF, bytes[1] & 0xFF, bytes[2] & 0xFF, bytes[3] & 0xFF);
        }

        if (bytes.length == 16) {
            // IPv6: convert to colon-separated hex format
            val parts = new String[8];
            for (int i = 0; i < 8; i++) {
                val high = bytes[i * 2] & 0xFF;
                val low  = bytes[i * 2 + 1] & 0xFF;
                parts[i] = String.format("%x", (high << 8) | low);
            }
            return String.join(":", parts);
        }

        return null;
    }
}
