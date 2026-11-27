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
package io.sapl.node.testing;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Date;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.openssl.jcajce.JcaPKCS8Generator;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * Self-signed certificates for localhost, made for each test run, so that no
 * private key lies in the repository and none expires.
 */
public final class TestCertificates {

    private TestCertificates() {
    }

    /**
     * Writes a self-signed certificate for localhost and its private key as PEM
     * files.
     *
     * @param directory the directory of the files
     * @param name the base name of the files and the common name of the
     * certificate
     * @param notAfter the end of the validity of the certificate
     * @return the paths of the certificate and of the key
     * @throws IOException if a file cannot be written
     * @throws GeneralSecurityException if the certificate cannot be made
     */
    public static PemFiles write(Path directory, String name, Instant notAfter)
            throws IOException, GeneralSecurityException {
        final var generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        final var             keyPair = generator.generateKeyPair();
        final var             subject = new X500Name("CN=" + name);
        final var             builder = new JcaX509v3CertificateBuilder(subject, BigInteger.valueOf(System.nanoTime()),
                Date.from(Instant.now().minusSeconds(3600)), Date.from(notAfter), subject, keyPair.getPublic());
        final X509Certificate certificate;
        try {
            builder.addExtension(Extension.subjectAlternativeName, false,
                    new GeneralNames(new GeneralName(GeneralName.dNSName, "localhost")));
            certificate = new JcaX509CertificateConverter().getCertificate(
                    builder.build(new JcaContentSignerBuilder("SHA256withECDSA").build(keyPair.getPrivate())));
        } catch (OperatorCreationException e) {
            throw new GeneralSecurityException(e);
        }
        final var files = new PemFiles(directory.resolve(name + ".pem"), directory.resolve(name + ".key"), certificate,
                keyPair.getPrivate());
        Files.writeString(files.certificateChain(), pem(certificate));
        Files.writeString(files.privateKey(), pem(new JcaPKCS8Generator(keyPair.getPrivate(), null)));
        return files;
    }

    private static String pem(Object object) throws IOException {
        final var text = new StringWriter();
        try (var writer = new JcaPEMWriter(text)) {
            writer.writeObject(object);
        }
        return text.toString();
    }

    /**
     * The PEM files of a certificate, and what they hold.
     *
     * @param certificateChain the file of the certificate
     * @param privateKey the file of the private key
     * @param certificate the certificate
     * @param key the private key
     */
    public record PemFiles(Path certificateChain, Path privateKey, X509Certificate certificate, PrivateKey key) {}

}
