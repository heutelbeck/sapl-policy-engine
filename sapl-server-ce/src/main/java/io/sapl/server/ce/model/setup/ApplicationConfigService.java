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

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.stereotype.Service;

import io.sapl.server.ce.model.setup.condition.SetupNotFinishedCondition;
import lombok.Getter;

@Service
@Conditional(SetupNotFinishedCondition.class)
public final class ApplicationConfigService {

    private static final String           PORT_PREFIX             = "${PORT:";
    private final ApplicationYml[]        appYmls;
    private final ConfigurableEnvironment env;
    @Getter
    private final DBMSConfig              dbmsConfig              = new DBMSConfig();
    @Getter
    private final AdminUserConfig         adminUserConfig         = new AdminUserConfig();
    @Getter
    private final EndpointConfig          httpEndpoint            = new EndpointConfig("server.", 8443);
    @Getter
    private final EndpointConfig          rsocketEndpoint         = new EndpointConfig("spring.rsocket.server.", 7000);
    @Getter
    private final ApiAuthenticationConfig apiAuthenticationConfig = new ApiAuthenticationConfig();
    @Getter
    private final LoggingConfig           loggingConfig           = new LoggingConfig();

    public ApplicationConfigService(ConfigurableEnvironment env) throws IOException {
        this.env = env;

        appYmls = this.getAppYmlsFromProperties().stream().map(ApplicationYml::new).toArray(ApplicationYml[]::new);
        for (ApplicationYml applicationYml : appYmls) {
            applicationYml.initMap();
        }
        this.initDbmsConfig();
        this.initLoggingConfig();
        this.initAdminUserConfig();
        this.initHttpEndpointConfig();
        this.initRsocketEndpointConfig();
        this.initApiAuthenticationConfig();
    }

    private List<File> getAppYmlsFromProperties() {
        List<File> files = new ArrayList<>();

        String                 projectPath     = System.getProperty("user.dir");
        MutablePropertySources propertySources = this.env.getPropertySources();

        for (PropertySource<?> source : propertySources) {
            String sourceName = source.getName();

            // Get the application.yml-propertySource, but only if it is not located in
            // classpath
            if (sourceName.contains("Config resource") && !sourceName.contains("classpath")) {
                Pattern pattern = Pattern.compile("\\[(.*?)]");
                Matcher matcher = pattern.matcher(sourceName);
                if (matcher.find()) {
                    String filePath = matcher.group(1);
                    if (Paths.get(filePath).isAbsolute()) {
                        files.add(new File(filePath));
                    } else {
                        files.add(new File(projectPath + File.separator + filePath));
                    }
                }
            }
        }
        if (files.isEmpty()) {
            files.add(new File(projectPath + File.separator + "config" + File.separator + "application.yml"));
        }
        return files;
    }

    public Object getAt(String path) {
        for (ApplicationYml f : appYmls) {
            if (f.existsAt(path)) {
                return f.getAt(path);
            }
        }
        return null;
    }

    private Object getAt(String path, Object defaultValue) {
        return ObjectUtils.firstNonNull(this.getAt(path), defaultValue);
    }

    private boolean getAtAsBoolean(String path, boolean defaultValue) {
        Object obj = this.getAt(path);
        if (obj instanceof Boolean) {
            return (boolean) obj;
        }
        return defaultValue;
    }

    private void setAt(String path, Object value) {
        for (ApplicationYml f : appYmls) {
            if (f.existsAt(path)) {
                f.setAt(path, value);
                return;
            }
        }
        // If value was not found in any file, set it in primary file
        appYmls[0].setAt(path, value);
    }

    private void persistYmlFiles() throws IOException {
        for (ApplicationYml f : appYmls) {
            f.persistYmlFile();
        }
    }

    private void initDbmsConfig() {
        this.dbmsConfig.setDbms(SupportedDatasourceTypes.getByDriverClassName(this
                .getAt(DBMSConfig.DRIVERCLASSNAME_PATH, SupportedDatasourceTypes.H2.getDriverClassName()).toString()));
        this.dbmsConfig.setUrl(this.getAt(DBMSConfig.URL_PATH, "").toString());
        if (this.dbmsConfig.getUrl().isEmpty()) {
            dbmsConfig.setUrl(dbmsConfig.getDbms().getDefaultUrl());
        }
        this.dbmsConfig.setUsername(this.getAt(DBMSConfig.USERNAME_PATH, "").toString());
        this.dbmsConfig.setPassword(this.getAt(DBMSConfig.PASSWORD_PATH, "").toString());

    }

    public void persistDbmsConfig() throws IOException {
        this.setAt(DBMSConfig.DRIVERCLASSNAME_PATH, dbmsConfig.getDbms().getDriverClassName());
        this.setAt(DBMSConfig.URL_PATH, dbmsConfig.getUrl());
        this.setAt(DBMSConfig.USERNAME_PATH, dbmsConfig.getUsername());
        this.setAt(DBMSConfig.PASSWORD_PATH, dbmsConfig.getPassword());
        this.persistYmlFiles();
        this.dbmsConfig.setSaved(true);

    }

    private void initAdminUserConfig() {
        this.adminUserConfig.setUsername(this.getAt(AdminUserConfig.USERNAME_PATH, "").toString());
    }

    public void persistAdminUserConfig() throws IOException {
        this.setAt(AdminUserConfig.USERNAME_PATH, this.adminUserConfig.getUsername());
        this.setAt(AdminUserConfig.ENCODEDPASSWORD_PATH, this.adminUserConfig.getEncodedPassword());
        this.persistYmlFiles();
        this.adminUserConfig.setSaved(true);

    }

    @SuppressWarnings("unchecked")
    private void initHttpEndpointConfig() {
        this.httpEndpoint.setAddress(this.getAt(httpEndpoint.addressPath, "localhost").toString());

        final var port = this.getAt(httpEndpoint.portPath, "").toString();
        if (getPortNumber(port) > 0) {
            this.httpEndpoint.setPort(getPortNumber(port));
        }

        if (this.getAtAsBoolean(httpEndpoint.sslEnabledPath, false)) {
            if (this.getAt(httpEndpoint.sslEnabledProtocolsPath) != null) {
                if (this.getAt(httpEndpoint.sslEnabledProtocolsPath) instanceof List) {
                    this.httpEndpoint
                            .setEnabledSslProtocols(((List<String>) this.getAt(httpEndpoint.sslEnabledProtocolsPath))
                                    .stream().map(SupportedSslVersions::getByDisplayName).filter(Objects::nonNull)
                                    .collect(Collectors.toSet()));
                } else if (this.getAt(httpEndpoint.sslEnabledProtocolsPath) instanceof String string) {
                    this.httpEndpoint.setEnabledSslProtocols(
                            Arrays.stream(string.split(",")).map(SupportedSslVersions::getByDisplayName)
                                    .filter(Objects::nonNull).collect(Collectors.toSet()));
                }
            }
            this.httpEndpoint.setKeyStoreType(ObjectUtils.firstNonNull(
                    SupportedKeystoreTypes.getByName(this.getAt(httpEndpoint.sslKeyStoreTypePath, "").toString()),
                    SupportedKeystoreTypes.PKCS12));
            this.httpEndpoint
                    .setKeyStore(this.getAt(httpEndpoint.sslKeyStorePath, "file:config/keystore.p12").toString());
            this.httpEndpoint.setKeyPassword(this.getAt(httpEndpoint.sslKeyPasswordPath, "").toString());
            this.httpEndpoint.setKeyStorePassword(this.getAt(httpEndpoint.sslKeyStorePasswordPath, "").toString());
            this.httpEndpoint.setKeyAlias(this.getAt(httpEndpoint.sslKeyAliasPath, "").toString());

            if (this.getAt(httpEndpoint.sslCiphersPath) != null) {
                if (this.getAt(httpEndpoint.sslCiphersPath) instanceof List) {
                    this.httpEndpoint.setCiphers(((List<String>) this.getAt(httpEndpoint.sslCiphersPath)).stream()
                            .map(SupportedCiphers::getByName).filter(Objects::nonNull).collect(Collectors.toSet()));
                } else if (this.getAt(httpEndpoint.sslCiphersPath) instanceof String string) {
                    this.httpEndpoint.setCiphers(Arrays.stream(string.split(",")).map(SupportedCiphers::getByName)
                            .filter(Objects::nonNull).collect(Collectors.toSet()));
                }
            }
        }
    }

    public void persistHttpEndpointConfig() throws IOException {
        this.setAt(httpEndpoint.portPath, PORT_PREFIX + this.httpEndpoint.getPort() + "}");
        this.setAt(httpEndpoint.addressPath, httpEndpoint.getAddress());

        final var tlsEnabled = this.httpEndpoint.getSslEnabled();
        this.setAt(httpEndpoint.sslEnabledPath, tlsEnabled);

        if (tlsEnabled) {
            this.setAt(httpEndpoint.sslKeyStoreTypePath, this.httpEndpoint.getKeyStoreType());
            this.setAt(httpEndpoint.sslKeyStorePath, this.httpEndpoint.getKeyStore());
            this.setAt(httpEndpoint.sslKeyStorePasswordPath, this.httpEndpoint.getKeyStorePassword());
            this.setAt(httpEndpoint.sslKeyPasswordPath, this.httpEndpoint.getKeyPassword());
            this.setAt(httpEndpoint.sslKeyAliasPath, this.httpEndpoint.getKeyAlias());
            this.setAt(httpEndpoint.sslCiphersPath, this.httpEndpoint.getCiphers());
            this.setAt(httpEndpoint.sslEnabledProtocolsPath, this.httpEndpoint.getEnabledSslProtocols().stream()
                    .map(SupportedSslVersions::getDisplayName).collect(Collectors.toSet()));
            this.setAt(httpEndpoint.sslProtocolPath, httpEndpoint.getPrimarySslProtocol().getDisplayName());
        }
        this.persistYmlFiles();
        this.httpEndpoint.setSaved(true);

    }

    @SuppressWarnings("unchecked")
    private void initRsocketEndpointConfig() {
        this.rsocketEndpoint.setAddress(this.getAt(rsocketEndpoint.addressPath, "localhost").toString());

        final var port = this.getAt(rsocketEndpoint.portPath, "").toString();
        if (getPortNumber(port) > 0) {
            this.rsocketEndpoint.setPort(getPortNumber(port));
        }

        if (this.getAtAsBoolean(rsocketEndpoint.sslEnabledPath, false)) {
            if (this.getAt(rsocketEndpoint.sslEnabledProtocolsPath) != null) {
                if (this.getAt(rsocketEndpoint.sslEnabledProtocolsPath) instanceof List) {
                    this.rsocketEndpoint
                            .setEnabledSslProtocols(((List<String>) this.getAt(rsocketEndpoint.sslEnabledProtocolsPath))
                                    .stream().map(SupportedSslVersions::getByDisplayName).filter(Objects::nonNull)
                                    .collect(Collectors.toSet()));
                } else if (this.getAt(rsocketEndpoint.sslEnabledProtocolsPath) instanceof String string) {
                    this.rsocketEndpoint.setEnabledSslProtocols(
                            Arrays.stream(string.split(",")).map(SupportedSslVersions::getByDisplayName)
                                    .filter(Objects::nonNull).collect(Collectors.toSet()));
                }
            }

            this.rsocketEndpoint.setKeyStoreType(ObjectUtils.firstNonNull(
                    SupportedKeystoreTypes.getByName(this.getAt(rsocketEndpoint.sslKeyStoreTypePath, "").toString()),
                    SupportedKeystoreTypes.PKCS12));
            this.rsocketEndpoint
                    .setKeyStore(this.getAt(rsocketEndpoint.sslKeyStorePath, "file:config/keystore.p12").toString());
            this.rsocketEndpoint.setKeyPassword(this.getAt(rsocketEndpoint.sslKeyPasswordPath, "").toString());
            this.rsocketEndpoint
                    .setKeyStorePassword(this.getAt(rsocketEndpoint.sslKeyStorePasswordPath, "").toString());
            this.rsocketEndpoint.setKeyAlias(this.getAt(rsocketEndpoint.sslKeyAliasPath, "").toString());
            if (this.getAt(rsocketEndpoint.sslCiphersPath) != null) {
                if (this.getAt(rsocketEndpoint.sslCiphersPath) instanceof List) {
                    this.rsocketEndpoint.setCiphers(((List<String>) this.getAt(rsocketEndpoint.sslCiphersPath)).stream()
                            .map(SupportedCiphers::getByName).filter(Objects::nonNull).collect(Collectors.toSet()));
                } else if (this.getAt(rsocketEndpoint.sslCiphersPath) instanceof String string) {
                    this.rsocketEndpoint.setCiphers(Arrays.stream(string.split(",")).map(SupportedCiphers::getByName)
                            .filter(Objects::nonNull).collect(Collectors.toSet()));
                }
            }
        }
    }

    public void persistRsocketEndpointConfig() throws IOException {
        this.setAt(rsocketEndpoint.portPath, PORT_PREFIX + this.rsocketEndpoint.getPort() + "}");
        this.setAt(rsocketEndpoint.addressPath, rsocketEndpoint.getAddress());
        this.setAt(rsocketEndpoint.transportPath, "tcp");

        final var tlsEnabled = this.rsocketEndpoint.getSslEnabled();
        this.setAt(rsocketEndpoint.sslEnabledPath, tlsEnabled);

        if (tlsEnabled) {
            this.setAt(rsocketEndpoint.sslKeyStoreTypePath, this.rsocketEndpoint.getKeyStoreType());
            this.setAt(rsocketEndpoint.sslKeyStorePath, this.rsocketEndpoint.getKeyStore());
            this.setAt(rsocketEndpoint.sslKeyStorePasswordPath, this.rsocketEndpoint.getKeyStorePassword());
            this.setAt(rsocketEndpoint.sslKeyPasswordPath, this.rsocketEndpoint.getKeyPassword());
            this.setAt(rsocketEndpoint.sslKeyAliasPath, this.rsocketEndpoint.getKeyAlias());
            this.setAt(rsocketEndpoint.sslCiphersPath, this.rsocketEndpoint.getCiphers());
            this.setAt(rsocketEndpoint.sslEnabledProtocolsPath, this.rsocketEndpoint.getEnabledSslProtocols().stream()
                    .map(SupportedSslVersions::getDisplayName).collect(Collectors.toSet()));
            this.setAt(rsocketEndpoint.sslProtocolPath, rsocketEndpoint.getPrimarySslProtocol().getDisplayName());
        }
        this.persistYmlFiles();
        this.rsocketEndpoint.setSaved(true);
    }

    private void initApiAuthenticationConfig() {
        this.apiAuthenticationConfig
                .setBasicAuthEnabled(this.getAtAsBoolean(ApiAuthenticationConfig.BASICAUTHENABLED_PATH, false));
        this.apiAuthenticationConfig
                .setApiKeyAuthEnabled(this.getAtAsBoolean(ApiAuthenticationConfig.APIKEYAUTHENABLED_PATH, false));
        this.apiAuthenticationConfig
                .setApiKeyCachingEnabled(this.getAtAsBoolean(ApiAuthenticationConfig.APIKEYCACHINGENABLED_PATH, false));
        this.apiAuthenticationConfig.setApiKeyCachingExpires(
                Integer.parseInt(this.getAt(ApiAuthenticationConfig.APIKEYCACHINGEXPIRE_PATH, 300).toString()));
        this.apiAuthenticationConfig.setApiKeyCachingMaxSize(
                Integer.parseInt(this.getAt(ApiAuthenticationConfig.APIKEYCACHINGMAXSIZE_PATH, 10000).toString()));
        this.apiAuthenticationConfig
                .setOAuth2AuthEnabled(this.getAtAsBoolean(ApiAuthenticationConfig.OUATH2ENABLED_PATH, false));
        this.apiAuthenticationConfig.setOAuth2RessourceServer(
                this.getAt(ApiAuthenticationConfig.OAUTH2RESOURCESERVER_PATH, "http://auth-server:8080/default")
                        .toString());
    }

    public void persistApiAuthenticationConfig() throws IOException {
        this.setAt(ApiAuthenticationConfig.BASICAUTHENABLED_PATH, this.apiAuthenticationConfig.isBasicAuthEnabled());
        this.setAt(ApiAuthenticationConfig.APIKEYAUTHENABLED_PATH, this.apiAuthenticationConfig.isApiKeyAuthEnabled());
        this.setAt(ApiAuthenticationConfig.APIKEYCACHINGENABLED_PATH,
                this.apiAuthenticationConfig.isApiKeyCachingEnabled());
        this.setAt(ApiAuthenticationConfig.APIKEYCACHINGEXPIRE_PATH,
                this.apiAuthenticationConfig.getApiKeyCachingExpires());
        this.setAt(ApiAuthenticationConfig.APIKEYCACHINGMAXSIZE_PATH,
                this.apiAuthenticationConfig.getApiKeyCachingMaxSize());
        this.setAt(ApiAuthenticationConfig.OUATH2ENABLED_PATH, this.apiAuthenticationConfig.isOAuth2AuthEnabled());
        this.setAt(ApiAuthenticationConfig.OAUTH2RESOURCESERVER_PATH,
                this.apiAuthenticationConfig.getOAuth2RessourceServer());
        this.persistYmlFiles();
        this.apiAuthenticationConfig.setSaved(true);
    }

    private void initLoggingConfig() {
        this.loggingConfig.setSaplLoggingLevel(
                LoggingLevel.getByName(this.getAt(LoggingConfig.SAPL_LOGGING_PATH), LoggingLevel.WARN));
        this.loggingConfig.setSpringLoggingLevel(
                LoggingLevel.getByName(this.getAt(LoggingConfig.SPRING_LOGGING_PATH), LoggingLevel.WARN));
        this.loggingConfig.setSaplServerLoggingLevel(
                LoggingLevel.getByName(this.getAt(LoggingConfig.SAPL_SERVER_LOGGING_PATH), LoggingLevel.WARN));

        this.loggingConfig.setPrintTrace(this.getAtAsBoolean(LoggingConfig.PRINT_TRACE_PATH, true));
        this.loggingConfig.setPrintJsonReport(this.getAtAsBoolean(LoggingConfig.PRINT_JSON_REPORT_PATH, true));
        this.loggingConfig.setPrintTextReport(this.getAtAsBoolean(LoggingConfig.PRINT_TEXT_REPORT_PATH, true));
        this.loggingConfig.setPrettyPrintReports(this.getAtAsBoolean(LoggingConfig.PRETTY_PRINT_REPORTS_PATH, false));
    }

    public void persistLoggingConfig() throws IOException {
        this.setAt(LoggingConfig.SAPL_LOGGING_PATH, this.loggingConfig.getSaplLoggingLevel());
        this.setAt(LoggingConfig.SPRING_LOGGING_PATH, this.loggingConfig.getSpringLoggingLevel());
        this.setAt(LoggingConfig.SAPL_SERVER_LOGGING_PATH, this.loggingConfig.getSaplServerLoggingLevel());

        this.setAt(LoggingConfig.PRINT_TRACE_PATH, this.loggingConfig.isPrintTrace());
        this.setAt(LoggingConfig.PRINT_JSON_REPORT_PATH, this.loggingConfig.isPrintJsonReport());
        this.setAt(LoggingConfig.PRINT_TEXT_REPORT_PATH, this.loggingConfig.isPrintTextReport());
        this.setAt(LoggingConfig.PRETTY_PRINT_REPORTS_PATH, this.loggingConfig.isPrettyPrintReports());

        this.persistYmlFiles();
        this.loggingConfig.setSaved(true);
    }

    private int getPortNumber(String s) {
        String  regex   = "\\{PORT:(\\d+)}";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(s);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        } else {
            return 0;
        }
    }

}
