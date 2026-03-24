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
package io.sapl.node.cli.support;

import java.io.PrintWriter;
import java.util.Map;

import javax.net.ssl.SSLException;

import org.slf4j.LoggerFactory;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.node.SaplNodeApplication;
import io.sapl.node.cli.options.PdpOptions;
import io.sapl.node.cli.options.RemoteConnectionOptions;
import io.sapl.pdp.remote.ProtobufRemotePolicyDecisionPoint;
import io.sapl.pdp.remote.RemoteHttpPolicyDecisionPoint.RemoteHttpPolicyDecisionPointBuilder;
import io.sapl.pdp.remote.RemotePolicyDecisionPoint;
import lombok.val;
import tools.jackson.databind.json.JsonMapper;

/**
 * Sets up a {@link PolicyDecisionPoint} and {@link JsonMapper} for CLI
 * commands. Handles both local (Spring-based) and remote (HTTP client) modes.
 * Call {@link #shutdown()} when done.
 *
 * @param pdp the policy decision point
 * @param mapper a Jackson mapper configured for SAPL types
 * @param context the Spring context (null for remote mode)
 */
public record PdpSetup(PolicyDecisionPoint pdp, JsonMapper mapper, ConfigurableApplicationContext context) {

    public static final String ERROR_BASIC_AUTH_FORMAT = "Error: --basic-auth must be in format 'user:password'.";
    public static final String ERROR_REMOTE_CONNECTION = "Error: Failed to connect to remote PDP: %s.";
    public static final String ERROR_REMOTE_WITH_LOCAL = "Error: --remote cannot be used with --dir or --bundle.";
    public static final String ERROR_REMOTE_WITH_VERIFICATION = "Error: --remote cannot be used with --public-key or --no-verify.";

    /**
     * Opens a PDP session based on the CLI options. Returns {@code null} if
     * policy resolution fails (error already printed to {@code err}).
     *
     * @param options the parsed CLI options
     * @param err writer for error messages
     * @return a ready-to-use setup, or null on failure
     * @throws SSLException if TLS configuration fails in remote mode
     */
    public static PdpSetup open(PdpOptions options, PrintWriter err) throws SSLException {
        val remote = options.remoteConnection != null && options.remoteConnection.remote;
        if (remote && options.policySource != null) {
            err.println(ERROR_REMOTE_WITH_LOCAL);
            return null;
        }
        if (remote && options.bundleVerification != null) {
            err.println(ERROR_REMOTE_WITH_VERIFICATION);
            return null;
        }
        return remote ? openRemote(options.remoteConnection) : openLocal(options, err);
    }

    public void shutdown() {
        if (context != null) {
            SpringApplication.exit(context);
        }
    }

    private static PdpSetup openRemote(RemoteConnectionOptions remote) throws SSLException {
        if (remote.rsocket) {
            return openRsocket(remote);
        }
        return openHttp(remote);
    }

    private static PdpSetup openHttp(RemoteConnectionOptions remote) throws SSLException {
        val url     = resolveWithEnv(remote.url, "SAPL_URL", "http://localhost:8443");
        val builder = RemotePolicyDecisionPoint.builder().http().baseUrl(url);
        if (remote.insecure) {
            builder.withUnsecureSSL();
        }
        configureAuth(builder, remote.auth);
        val pdp    = builder.build();
        val mapper = JsonMapper.builder().addModule(new SaplJacksonModule()).build();
        return new PdpSetup(pdp, mapper, null);
    }

    private static PdpSetup openRsocket(RemoteConnectionOptions remote) {
        val builder = ProtobufRemotePolicyDecisionPoint.builder().host(remote.rsocketHost).port(remote.rsocketPort);
        configureRsocketAuth(builder, remote.auth);
        val pdp    = builder.build();
        val mapper = JsonMapper.builder().addModule(new SaplJacksonModule()).build();
        return new PdpSetup(pdp, mapper, null);
    }

    private static PdpSetup openLocal(PdpOptions options, PrintWriter err) {
        val resolved = PolicySourceResolver.resolve(options.policySource, options.bundleVerification,
                options.saplHomeOverride, err);
        if (resolved == null) {
            return null;
        }
        val springArgs = SpringArgsBuilder.build(resolved, options.trace, options.jsonReport, options.textReport);
        val app        = new SpringApplication(SaplNodeApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setBannerMode(Banner.Mode.OFF);
        app.setAdditionalProfiles("cli");
        app.setDefaultProperties(Map.of("org.springframework.boot.logging.LoggingSystem", "none"));
        val context = app.run(springArgs);
        if (options.trace || options.jsonReport || options.textReport) {
            val logbackContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            logbackContext.getLogger("io.sapl.pdp.interceptors").setLevel(Level.INFO);
        }
        return new PdpSetup(context.getBean(PolicyDecisionPoint.class), context.getBean(JsonMapper.class), context);
    }

    private static void configureRsocketAuth(ProtobufRemotePolicyDecisionPoint.Builder builder,
            RemoteConnectionOptions.AuthOptions auth) {
        if (auth != null) {
            if (auth.basicAuth != null) {
                val separatorIndex = auth.basicAuth.indexOf(':');
                if (separatorIndex < 0) {
                    throw new IllegalArgumentException(ERROR_BASIC_AUTH_FORMAT);
                }
                builder.basicAuth(auth.basicAuth.substring(0, separatorIndex),
                        auth.basicAuth.substring(separatorIndex + 1));
                return;
            }
            if (auth.token != null) {
                builder.apiKey(auth.token);
                return;
            }
        }
        val envBasicAuth = System.getenv("SAPL_BASIC_AUTH");
        if (envBasicAuth != null) {
            val separatorIndex = envBasicAuth.indexOf(':');
            if (separatorIndex < 0) {
                throw new IllegalArgumentException(ERROR_BASIC_AUTH_FORMAT);
            }
            builder.basicAuth(envBasicAuth.substring(0, separatorIndex), envBasicAuth.substring(separatorIndex + 1));
            return;
        }
        val envToken = System.getenv("SAPL_BEARER_TOKEN");
        if (envToken != null) {
            builder.apiKey(envToken);
        }
    }

    private static void configureAuth(RemoteHttpPolicyDecisionPointBuilder builder,
            RemoteConnectionOptions.AuthOptions auth) {
        if (auth != null) {
            if (auth.basicAuth != null) {
                applyBasicAuth(builder, auth.basicAuth);
                return;
            }
            if (auth.token != null) {
                builder.apiKey(auth.token);
                return;
            }
        }
        val envBasicAuth = System.getenv("SAPL_BASIC_AUTH");
        if (envBasicAuth != null) {
            applyBasicAuth(builder, envBasicAuth);
            return;
        }
        val envToken = System.getenv("SAPL_BEARER_TOKEN");
        if (envToken != null) {
            builder.apiKey(envToken);
        }
    }

    private static void applyBasicAuth(RemoteHttpPolicyDecisionPointBuilder builder, String credentials) {
        val separatorIndex = credentials.indexOf(':');
        if (separatorIndex < 0) {
            throw new IllegalArgumentException(ERROR_BASIC_AUTH_FORMAT);
        }
        val user     = credentials.substring(0, separatorIndex);
        val password = credentials.substring(separatorIndex + 1);
        builder.basicAuth(user, password);
    }

    private static String resolveWithEnv(String flagValue, String envVar, String defaultValue) {
        if (!defaultValue.equals(flagValue)) {
            return flagValue;
        }
        val envValue = System.getenv(envVar);
        return envValue != null ? envValue : defaultValue;
    }

}
