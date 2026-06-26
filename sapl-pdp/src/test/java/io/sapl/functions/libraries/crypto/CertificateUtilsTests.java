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
package io.sapl.functions.libraries.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERTaggedObject;
import org.bouncycastle.asn1.DERUTF8String;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import lombok.val;

@DisplayName("CertificateUtils Subject Alternative Name extraction")
class CertificateUtilsTests {

    private static final String SUBJECT_DN  = "CN=Azathoth Nuclear Daemon,O=Center of Chaos,C=ZZ";
    private static final String OTHER_OID   = "1.3.6.1.4.1.99999.1";
    private static final String OTHER_VALUE = "shoggoth-principal";

    private static KeyPair keyPair;

    @BeforeAll
    static void generateKeyPair() throws Exception {
        keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
    }

    private static X509Certificate certificateWithOtherNameSan() throws Exception {
        val subject     = new X500Name(SUBJECT_DN);
        val now         = Instant.now();
        val certBuilder = new JcaX509v3CertificateBuilder(subject, BigInteger.ONE,
                Date.from(now.minus(1, ChronoUnit.DAYS)), Date.from(now.plus(1, ChronoUnit.DAYS)), subject,
                keyPair.getPublic());

        val otherNameContent = new DERSequence(new ASN1Encodable[] { new ASN1ObjectIdentifier(OTHER_OID),
                new DERTaggedObject(true, 0, new DERUTF8String(OTHER_VALUE)) });
        val otherName        = new GeneralName(GeneralName.otherName, otherNameContent);
        val sans             = new GeneralNames(otherName);
        certBuilder.addExtension(Extension.subjectAlternativeName, false, sans);

        val signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        val holder = certBuilder.build(signer);
        return new JcaX509CertificateConverter().getCertificate(holder);
    }

    @Test
    @DisplayName("a byte[] SAN value is encoded as stable Base64, never an object identity string")
    void whenSanValueIsByteArrayThenValueIsStableBase64() throws Exception {
        val certificate = certificateWithOtherNameSan();

        val firstParse  = CertificateUtils.extractSubjectAlternativeNames(certificate);
        val secondParse = CertificateUtils.extractSubjectAlternativeNames(certificate);

        assertThat(firstParse).hasSize(1).first().satisfies(san -> {
            assertThat(san.value()).doesNotStartWith("[B@");
            assertThat(Base64.getDecoder().decode(san.value())).isNotEmpty();
        });
        assertThat(firstParse.getFirst().value()).isEqualTo(secondParse.getFirst().value());
    }
}
