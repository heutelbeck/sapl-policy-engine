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
package io.sapl.node;

import static org.assertj.core.api.Assertions.assertThat;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;

import io.sapl.node.boot.SaplStartupConfigurationException;
import io.sapl.node.testing.TestCertificates;
import io.sapl.node.testing.TestCertificates.PemFiles;
import lombok.val;

/**
 * The node as an operator starts it: both APIs serve TLS, or plaintext only
 * with the opt-in, on loopback too. One pair of PEM files serves both APIs and
 * a renewed certificate is served without a restart. Each test starts the node
 * on free local ports, without containers.
 */
@DisplayName("Node TLS")
@ExtendWith(OutputCaptureExtension.class)
class NodeTlsTests {

    private static final String WARN_WITHOUT_TLS = "Serving %s without TLS because of the command-line option --no-tls. Anyone on the network path can read and change credentials and authorization decisions. Use this only where TLS is terminated upstream, by a proxy or a service mesh, or in development.";

    @TempDir
    Path directory;

    private int httpPort;
    private int rsocketPort;

    @BeforeEach
    void choosePorts() throws IOException {
        httpPort    = freePort();
        rsocketPort = freePort();
    }

    @Test
    @DisplayName("without TLS and without the opt-in the node refuses to start, on loopback too")
    void whenNoTlsAndNoOptInThenStartIsRefused() throws IOException {
        val configuration = configuration("");

        assertThatThrownBy(() -> start(configuration, "--no-auth"))
                .hasRootCauseInstanceOf(SaplStartupConfigurationException.class).rootCause()
                .hasMessage("SAPL Node refused to start. The HTTP API has no TLS.");
    }

    @Test
    @DisplayName("with --no-tls both APIs serve plaintext and each warns, naming the command-line option")
    void whenNoTlsOptionThenBothApisWarnWithTheOption(CapturedOutput output) throws IOException {
        val configuration = configuration("");

        try (val node = start(configuration, "--no-tls", "--no-auth")) {
            assertThat(output.getOut()).contains(WARN_WITHOUT_TLS.formatted("HTTP"),
                    WARN_WITHOUT_TLS.formatted("RSocket"));
        }
    }

    @Test
    @DisplayName("an SSL bundle named while server.ssl.enabled is false refuses the start")
    void whenBundleNamedButSslDisabledThenStartIsRefused() throws Exception {
        val certificate   = TestCertificates.write(directory, "first", Instant.now().plus(Duration.ofDays(90)));
        val configuration = configuration(tls(certificate) + """
                server.ssl.enabled: false
                """);

        assertThatThrownBy(() -> start(configuration, "--no-auth", "--no-tls"))
                .hasRootCauseInstanceOf(SaplStartupConfigurationException.class).rootCause().hasMessage(
                        "SAPL Node refused to start. The HTTP API names the SSL bundle 'sapl-node', but server.ssl.enabled is false.");
    }

    @Test
    @DisplayName("one pair of PEM files serves TLS on both APIs without any warning of plaintext")
    void whenSharedPemFilesThenBothApisServeThem(CapturedOutput output) throws Exception {
        val certificate   = TestCertificates.write(directory, "first", Instant.now().plus(Duration.ofDays(90)));
        val configuration = configuration(tls(certificate));

        try (val node = start(configuration, "--no-auth")) {
            assertThat(List.of(servedCommonName(httpPort, certificate), servedCommonName(rsocketPort, certificate),
                    output.getOut().contains("without TLS"))).containsExactly("CN=first", "CN=first", false);
            assertThat(output.getOut()).contains("HTTP    https://localhost:" + httpPort,
                    "RSocket tls://localhost:" + rsocketPort);
        }
    }

    @Test
    @DisplayName("a renewed certificate is served on both APIs without a restart")
    void whenCertificateIsRenewedThenBothApisServeTheNewOne() throws Exception {
        val certificate   = TestCertificates.write(directory, "first", Instant.now().plus(Duration.ofDays(90)));
        val configuration = configuration(tls(certificate) + """
                spring.ssl.bundle.watch.file.quiet-period: 100ms
                """);

        try (val node = start(configuration, "--no-auth")) {
            val renewed = TestCertificates.write(Files.createDirectories(directory.resolve("renewed")), "second",
                    Instant.now().plus(Duration.ofDays(90)));
            Files.copy(renewed.certificateChain(), certificate.certificateChain(), REPLACE_EXISTING);
            Files.copy(renewed.privateKey(), certificate.privateKey(), REPLACE_EXISTING);

            Awaitility.await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200)).ignoreExceptions()
                    .untilAsserted(() -> assertThat(
                            List.of(servedCommonName(httpPort, renewed), servedCommonName(rsocketPort, renewed)))
                            .containsExactly("CN=second", "CN=second"));
        }
    }

    @Test
    @DisplayName("a certificate that expires within two weeks is reported for both APIs at the start")
    void whenCertificateExpiresSoonThenStartWarnsForBothApis(CapturedOutput output) throws Exception {
        val certificate   = TestCertificates.write(directory, "first", Instant.now().plus(Duration.ofDays(5)));
        val configuration = configuration(tls(certificate));

        try (val node = start(configuration, "--no-auth")) {
            assertThat(output.getOut()).contains("The TLS certificate of the HTTP API expires on",
                    "The TLS certificate of the RSocket API expires on", "in 4 days. Renew it before then.");
        }
    }

    @Test
    @DisplayName("with the shipped configuration, naming an SSL bundle is enough to serve HTTPS")
    void whenShippedConfigurationNamesBundleThenHttpsIsServed() throws Exception {
        val certificate   = TestCertificates.write(directory, "first", Instant.now().plus(Duration.ofDays(90)));
        val configuration = configuration("""
                spring.ssl.bundle.pem.http.keystore:
                  certificate: file:{chain}
                  private-key: file:{key}
                server.ssl.bundle: http
                """.replace("{chain}", certificate.certificateChain().toString()).replace("{key}",
                certificate.privateKey().toString()));

        try (val node = startWithLocations(Path.of("src/main/resources/application.yml") + ",file:" + configuration,
                "--no-auth", "--sapl.pdp.rsocket.enabled=false")) {
            assertThat(servedCommonName(httpPort, certificate)).isEqualTo("CN=first");
        }
    }

    private ConfigurableApplicationContext start(Path configuration, String... options) {
        return startWithLocations(configuration.toString(), options);
    }

    private ConfigurableApplicationContext startWithLocations(String locations, String... options) {
        val args = new ArrayList<String>(List.of(options));
        args.add("--spring.config.location=file:" + locations);
        // The test classpath silences logging, the node logs as shipped.
        args.add("--logging.config=classpath:logback-spring.xml");
        val arguments = SaplNodeApplication.serverArguments(args.toArray(new String[0]));
        return SaplNodeApplication.serverApplication(arguments).run(arguments.springArguments().toArray(new String[0]));
    }

    private Path configuration(String extra) throws IOException {
        val policies = Files.createDirectories(directory.resolve("policies"));
        val file     = directory.resolve("application.yml");
        Files.writeString(file,
                """
                        server:
                          address: 127.0.0.1
                          port: {http}
                        sapl.pdp.rsocket:
                          address: 127.0.0.1
                          port: {rsocket}
                        io.sapl.pdp.embedded:
                          pdp-config-type: DIRECTORY
                          config-path: {policies}
                          policies-path: {policies}
                        spring.main.banner-mode: off
                        bucket4j.enabled: false
                        """.replace("{http}", String.valueOf(httpPort))
                        .replace("{rsocket}", String.valueOf(rsocketPort)).replace("{policies}", policies.toString())
                        + extra);
        return file;
    }

    private static String tls(PemFiles certificate) {
        return """
                io.sapl.node.tls:
                  certificate-chain: {chain}
                  private-key: {key}
                """.replace("{chain}", certificate.certificateChain().toString()).replace("{key}",
                certificate.privateKey().toString());
    }

    private static int freePort() throws IOException {
        try (val probe = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            return probe.getLocalPort();
        }
    }

    // The handshake trusts only the certificate the test expects, so a served
    // certificate of another name fails it.
    private static String servedCommonName(int port, PemFiles expected) throws IOException, GeneralSecurityException {
        val trusted = KeyStore.getInstance(KeyStore.getDefaultType());
        trusted.load(null, null);
        try (InputStream pem = Files.newInputStream(expected.certificateChain())) {
            trusted.setCertificateEntry("expected", CertificateFactory.getInstance("X.509").generateCertificate(pem));
        }
        val trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagers.init(trusted);
        val context = SSLContext.getInstance("TLS");
        context.init(null, trustManagers.getTrustManagers(), null);
        try (val socket = (SSLSocket) context.getSocketFactory().createSocket("localhost", port)) {
            socket.startHandshake();
            return ((X509Certificate) socket.getSession().getPeerCertificates()[0]).getSubjectX500Principal().getName();
        }
    }

}
