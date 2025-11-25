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
package io.sapl.functions.libraries.crypto;

import io.sapl.compiler.PolicyEvaluationException;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.io.ByteArrayInputStream;
import java.security.cert.*;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static io.sapl.functions.libraries.crypto.CryptoConstants.CERTIFICATE_TYPE_X509;

/**
 * Utilities for working with X.509 certificates. Provides methods for parsing,
 * encoding, and extracting information
 * from certificates.
 */
@UtilityClass
public class CertificateUtils {

    /**
     * Parses an X.509 certificate from PEM or DER format.
     *
     * @param certificateString
     * the certificate string in PEM or DER format
     *
     * @return the parsed X509Certificate
     *
     * @throws PolicyEvaluationException
     * if parsing fails
     */
    public static X509Certificate parseCertificate(String certificateString) throws CertificateException {
        val certificateFactory = getCertificateFactory();
        val certificateBytes   = PemUtils.decodeCertificatePem(certificateString);
        val inputStream        = new ByteArrayInputStream(certificateBytes);
        return (X509Certificate) certificateFactory.generateCertificate(inputStream);
    }

    /**
     * Encodes a certificate to its DER byte representation.
     *
     * @param certificate
     * the X509Certificate to encode
     *
     * @return the encoded certificate bytes
     *
     * @throws PolicyEvaluationException
     * if encoding fails
     */
    public static byte[] encodeCertificate(X509Certificate certificate) throws CertificateEncodingException {
        return certificate.getEncoded();
    }

    /**
     * Extracts Subject Alternative Names from a certificate.
     *
     * @param certificate
     * the X509Certificate to extract SANs from
     *
     * @return list of SubjectAlternativeName records, empty if no SANs present
     *
     * @throws CertificateParsingException
     * if extraction fails
     */
    public static List<SubjectAlternativeName> extractSubjectAlternativeNames(X509Certificate certificate)
            throws CertificateParsingException {
        val rawSans = certificate.getSubjectAlternativeNames();
        if (rawSans == null) {
            return Collections.emptyList();
        }
        return rawSans.stream().map(CertificateUtils::toSubjectAlternativeName).toList();
    }

    private static SubjectAlternativeName toSubjectAlternativeName(List<?> rawSan) {
        val type  = (Integer) rawSan.get(0);
        val value = rawSan.get(1).toString();
        return new SubjectAlternativeName(type, value);
    }

    /**
     * Gets the X.509 certificate factory instance.
     *
     * @return the CertificateFactory for X.509 certificates
     *
     * @throws PolicyEvaluationException
     * if X.509 certificate factory is not available
     */
    public static CertificateFactory getCertificateFactory() throws CertificateException {
        return CertificateFactory.getInstance(CERTIFICATE_TYPE_X509);
    }
}
