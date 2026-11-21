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
package io.sapl.node.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.pem.PemSslStoreBundle;
import org.springframework.boot.ssl.pem.PemSslStoreDetails;

import io.sapl.node.testing.LogCapture;
import io.sapl.node.testing.TestCertificates;
import lombok.val;

/**
 * A renewed certificate reaches the TLS context of the HTTP connector, and one
 * that cannot be used leaves the served certificate in place with a warning,
 * so a broken renewal never takes TLS down.
 */
@DisplayName("HttpCertificateReloading")
@ExtendWith(MockitoExtension.class)
class HttpCertificateReloadingTests {

    @TempDir
    Path directory;

    @Test
    @DisplayName("a renewed bundle becomes the key store of the connector and the reload is logged")
    void whenBundleIsRenewedThenConnectorServesIt() throws Exception {
        val files   = TestCertificates.write(directory, "renewed", Instant.now().plus(Duration.ofDays(30)));
        val details = PemSslStoreDetails.forCertificate(Files.readString(files.certificateChain()))
                .withPrivateKey(Files.readString(files.privateKey()));
        val bundle  = SslBundle.of(new PemSslStoreBundle(details, null));
        val factory = new SslContextFactory.Server();

        try (val log = LogCapture.of(HttpCertificateReloading.class)) {
            HttpCertificateReloading.reload(factory, bundle);

            assertThat(List.of(factory.getKeyStore() == bundle.getStores().getKeyStore(), log.lines()))
                    .containsExactly(true, List.of("INFO Reloaded the TLS certificate of the HTTP API."));
        }
    }

    @Test
    @DisplayName("a renewed bundle that cannot be used keeps the served certificate and warns")
    void whenRenewedBundleIsBrokenThenServedCertificateStays(@Mock SslBundle broken) {
        val factory = new SslContextFactory.Server();
        val served  = factory.getKeyStore();
        when(broken.getStores()).thenThrow(new IllegalStateException("unable to read the private key"));

        try (val log = LogCapture.of(HttpCertificateReloading.class)) {
            HttpCertificateReloading.reload(factory, broken);

            assertThat(List.of(factory.getKeyStore() == served, log.lines())).containsExactly(true, List.of(
                    "WARN The TLS certificate of the HTTP API cannot be reloaded: unable to read the private key. The node keeps serving the previous certificate."));
        }
    }

}
