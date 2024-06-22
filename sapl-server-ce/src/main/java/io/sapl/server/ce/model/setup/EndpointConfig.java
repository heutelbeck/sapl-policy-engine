/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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

package io.sapl.server.ce.model.setup;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.util.EnumSet;
import java.util.Set;

import com.google.common.net.InetAddresses;

import lombok.Getter;
import lombok.Setter;

public class EndpointConfig {

    private static final String FILEPATH_PREFIX = "file:";

    final String portPath;
    final String addressPath;
    final String transportPath;
    final String sslEnabledPath;
    final String sslKeyStoreTypePath;
    final String sslKeyStorePath;
    final String sslKeyStorePasswordPath;
    final String sslKeyPasswordPath;
    final String sslKeyAliasPath;
    final String sslCiphersPath;
    final String sslEnabledProtocolsPath;
    final String sslProtocolPath;

    @Getter
    @Setter
    private boolean                   saved               = false;
    @Setter
    @Getter
    private String                    address             = "";
    @Setter
    @Getter
    private int                       port;
    @Setter
    @Getter
    private Set<SupportedSslVersions> enabledSslProtocols = EnumSet.allOf(SupportedSslVersions.class);
    @Getter
    private SupportedKeystoreTypes    keyStoreType        = SupportedKeystoreTypes.PKCS12;
    @Getter
    private String                    keyStore            = "";
    @Getter
    private String                    keyPassword         = "";
    @Getter
    private String                    keyStorePassword    = "";
    @Getter
    private String                    keyAlias            = "";
    @Setter
    @Getter
    private Set<SupportedCiphers>     ciphers             = EnumSet.allOf(SupportedCiphers.class);
    private boolean                   validKeystoreConfig = false;

    public EndpointConfig(String prefix, int port) {

        addressPath   = prefix + "address";
        portPath      = prefix + "port";
        transportPath = prefix + "transport";

        sslEnabledPath          = prefix + "ssl.enabled";
        sslKeyStoreTypePath     = prefix + "ssl.key-store-type";
        sslKeyStorePath         = prefix + "ssl.key-store";
        sslKeyStorePasswordPath = prefix + "ssl.key-store-password";
        sslKeyPasswordPath      = prefix + "ssl.key-password";
        sslKeyAliasPath         = prefix + "ssl.key-alias";
        sslCiphersPath          = prefix + "ssl.ciphers";
        sslEnabledProtocolsPath = prefix + "ssl.enabled-protocols";
        sslProtocolPath         = prefix + "ssl.protocol";

        this.port = port;
    }

    public void setKeyStoreType(SupportedKeystoreTypes keyStoreType) {
        if (!keyStoreType.equals(this.keyStoreType)) {
            this.validKeystoreConfig = false;
            this.keyStoreType        = keyStoreType;
        }
    }

    public void setKeyStore(String keyStore) {
        if (!keyStore.equals(this.keyStore)) {
            this.validKeystoreConfig = false;
            this.keyStore            = keyStore;
        }
    }

    public void setKeyStorePassword(String keyStorePassword) {
        if (!keyStorePassword.equals(this.keyStorePassword)) {
            this.validKeystoreConfig = false;
            this.keyStorePassword    = keyStorePassword;
        }
    }

    public void setKeyPassword(String keyPassword) {
        if (!keyPassword.equals(this.keyPassword)) {
            this.validKeystoreConfig = false;
            this.keyPassword         = keyPassword;
        }
    }

    public void setKeyAlias(String keyAlias) {
        if (!keyAlias.equals(this.keyAlias)) {
            this.validKeystoreConfig = false;
            this.keyAlias            = keyAlias;
        }
    }

    public boolean getSslEnabled() {
        return !enabledSslProtocols.isEmpty();
    }

    public SupportedSslVersions getPrimarySslProtocol() {
        if (enabledSslProtocols.contains(SupportedSslVersions.TLSV1_3)) {
            return SupportedSslVersions.TLSV1_3;
        }
        if (enabledSslProtocols.contains(SupportedSslVersions.TLSV1_2)) {
            return SupportedSslVersions.TLSV1_2;
        }
        return null;
    }

    public boolean isValidConfig() {
        return isValidPort() && portAndProtocolsMatch() && isValidProtocolConfig();
    }

    public boolean isValidURI() {
        return InetAddresses.isUriInetAddress(this.address) || "localhost".equals(this.address);
    }

    public boolean isValidPort() {
        return this.port > 0 && this.port < 65535;
    }

    public boolean isValidProtocolConfig() {
        if (!this.enabledSslProtocols.isEmpty()) {
            return this.validKeystoreConfig && !this.ciphers.isEmpty();
        }
        return true;
    }

    public boolean portAndProtocolsMatch() {
        return (!this.enabledSslProtocols.isEmpty() && this.port != 80 && this.port != 8080)
                || (this.enabledSslProtocols.isEmpty() && this.port != 443 && this.port != 8443);
    }

    private Path getKeyStorePath() {
        if (keyStore.startsWith(FILEPATH_PREFIX)) {
            return Paths.get(keyStore.replaceFirst(FILEPATH_PREFIX, ""));
        }
        return Paths.get(keyStore);
    }

    public boolean testKeystore() throws CertificateException, KeyStoreException, NoSuchAlgorithmException, IOException,
            UnrecoverableEntryException {
        this.validKeystoreConfig = false;
        var      pwdArray = keyStorePassword.toCharArray();
        KeyStore ks       = KeyStore.getInstance(keyStoreType.name());
        try (InputStream is = Files.newInputStream(getKeyStorePath())) {
            ks.load(is, pwdArray);
            if (!ks.containsAlias(keyAlias)) {
                this.validKeystoreConfig = false;
                return false;
            }
            var            keyPwdArray           = this.keyPassword.toCharArray();
            var            keyPasswordProtection = new KeyStore.PasswordProtection(keyPwdArray);
            KeyStore.Entry entry                 = ks.getEntry(this.keyAlias, keyPasswordProtection);
            this.validKeystoreConfig = (entry != null);
        }
        return this.validKeystoreConfig;
    }

    public String getUri() {
        String uri = this.address + ":" + this.port;
        return (getSslEnabled()) ? "https://" + uri : "http://" + uri;
    }

}
