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

import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.api.pdp.StreamingPolicyDecisionPoint;
import io.sapl.reactive.api.pdp.ReactivePolicyDecisionPoint;
import io.sapl.reactive.pdp.DelegatingReactivePolicyDecisionPoint;
import io.sapl.pdp.PolicyDecisionPointBuilder;
import io.sapl.pdp.configuration.bundle.BundleSecurityPolicy;
import io.sapl.pdp.interceptors.ReportingDecisionInterceptor;
import io.sapl.pdp.remote.DelegatingBlockingPolicyDecisionPoint;
import io.sapl.pdp.remote.ProtobufRemoteReactivePolicyDecisionPoint;
import io.sapl.node.cli.options.PdpOptions;
import io.sapl.node.cli.options.RemoteConnectionOptions;
import io.sapl.node.cli.support.PolicySourceResolver.ResolvedPolicy;
import io.sapl.pdp.remote.RemoteHttpReactivePolicyDecisionPoint.RemoteHttpPolicyDecisionPointBuilder;
import io.sapl.pdp.remote.RemotePolicyDecisionPoint;
import lombok.val;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.json.JsonMapper;

import javax.net.ssl.SSLException;
import java.io.PrintWriter;

/**
 * Sets up the PDP and a {@link JsonMapper} for CLI commands. Carries
 * both shapes (blocking and reactive) so each consumer picks whichever
 * fits the use case: blocking for one-shot CLI commands, reactive for
 * stream-shaped consumers. Both local and remote modes build the PDP
 * directly through the engine, with no Spring context. For remote modes
 * the blocking surface wraps the reactive client via a
 * {@link DelegatingBlockingPolicyDecisionPoint}. Call {@link #shutdown()}
 * when done to release the held resources.
 *
 * @param blocking the blocking-shaped PDP
 * @param reactive the reactive-shaped PDP
 * @param mapper a Jackson mapper configured for SAPL types
 * @param closeable the resources to release on shutdown (null for remote mode)
 */
public record PdpSetup(
        StreamingPolicyDecisionPoint blocking,
        ReactivePolicyDecisionPoint reactive,
        JsonMapper mapper,
        @Nullable AutoCloseable closeable) {

    static final String DEFAULT_HTTP_URL = "http://localhost:8080";

    public static final String ERROR_BASIC_AUTH_FORMAT = "Error: --basic-auth must be in format 'user:password'.";
    static final String ERROR_BUNDLE_VERIFICATION_NOT_CONFIGURED = "Refusing to load bundles with signature verification disabled without an explicit opt-in.";
    public static final String ERROR_EVALUATION_FAILED = "Error: Evaluation failed: %s.";
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
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception e) {
                // The held resources log their own teardown failures. Nothing actionable here.
            }
        }
    }

    private static PdpSetup openRemote(RemoteConnectionOptions remote) throws SSLException {
        if (remote.rsocket) {
            return openRsocket(remote);
        }
        return openHttp(remote);
    }

    private static PdpSetup openHttp(RemoteConnectionOptions remote) throws SSLException {
        val url     = resolveUrl(remote.url, System.getenv("SAPL_URL"), DEFAULT_HTTP_URL);
        val builder = RemotePolicyDecisionPoint.builder().http().baseUrl(url);
        if (remote.insecure) {
            // --insecure trusts any TLS cert and accepts credentials over a plaintext http
            // connection.
            builder.withUnsecureSSL().allowInsecureTransport();
        }
        configureAuth(builder, remote.auth);
        val reactive = builder.build();
        val blocking = new DelegatingBlockingPolicyDecisionPoint(reactive);
        val mapper   = JsonMapper.builder().addModule(new SaplJacksonModule()).build();
        return new PdpSetup(blocking, reactive, mapper, null);
    }

    private static PdpSetup openRsocket(RemoteConnectionOptions remote) throws SSLException {
        val builder = ProtobufRemoteReactivePolicyDecisionPoint.builder().host(remote.rsocketHost)
                .port(remote.rsocketPort);
        if (remote.rsocketTls && remote.insecure) {
            builder.withUnsecureSSL();
        } else if (remote.rsocketTls) {
            builder.secure();
        }
        if (remote.insecure) {
            // --insecure also accepts sending credentials over a plaintext rsocket
            // connection.
            builder.allowInsecureTransport();
        }
        configureRsocketAuth(builder, remote.auth);
        val reactive = builder.build();
        val blocking = new DelegatingBlockingPolicyDecisionPoint(reactive);
        val mapper   = JsonMapper.builder().addModule(new SaplJacksonModule()).build();
        return new PdpSetup(blocking, reactive, mapper, null);
    }

    private static PdpSetup openLocal(PdpOptions options, PrintWriter err) {
        val resolved = PolicySourceResolver.resolve(options.policySource, options.bundleVerification,
                options.saplHomeOverride, err);
        if (resolved == null) {
            return null;
        }
        val mapper  = JsonMapper.builder().addModule(new SaplJacksonModule()).build();
        val builder = PolicyDecisionPointBuilder.withDefaults(mapper);
        switch (resolved.kind()) {
        case DIRECTORY        -> builder.withDirectorySource(resolved.path());
        case SINGLE_BUNDLE    -> builder.withBundle(resolved.path(), StreamingPolicyDecisionPoint.DEFAULT_PDP_ID,
                bundleSecurityPolicy(resolved));
        case BUNDLE_DIRECTORY -> builder.withBundleDirectorySource(resolved.path(), bundleSecurityPolicy(resolved));
        }
        if (options.trace || options.jsonReport || options.textReport) {
            builder.withDecisionInterceptor(new ReportingDecisionInterceptor(true, options.trace, options.jsonReport,
                    options.textReport, false, false));
        }
        val components = builder.build();
        val reactive   = new DelegatingReactivePolicyDecisionPoint(components.pdp());
        return new PdpSetup(components.pdp(), reactive, mapper, components);
    }

    private static BundleSecurityPolicy bundleSecurityPolicy(ResolvedPolicy resolved) {
        if (resolved.publicKey() != null) {
            return BundleSecurityPolicy.builder(resolved.publicKey()).build();
        }
        if (resolved.allowUnsigned()) {
            return BundleSecurityPolicy.builder().disableSignatureVerification().build();
        }
        // Unreachable for a policy from PolicySourceResolver, which already fails closed
        // when neither a key nor an explicit opt-in is present. Guard so a future caller
        // cannot silently load unverified bundles.
        throw new IllegalStateException(ERROR_BUNDLE_VERIFICATION_NOT_CONFIGURED);
    }

    private static void configureRsocketAuth(ProtobufRemoteReactivePolicyDecisionPoint.Builder builder,
            RemoteConnectionOptions.AuthOptions auth) {
        val resolved = resolveCredentials(auth);
        if (resolved.basicAuth != null) {
            val parsed = parseBasicAuth(resolved.basicAuth);
            builder.basicAuth(parsed.username(), parsed.password());
        } else if (resolved.token != null) {
            builder.apiKey(resolved.token);
        }
    }

    private static void configureAuth(RemoteHttpPolicyDecisionPointBuilder builder,
            RemoteConnectionOptions.AuthOptions auth) {
        val resolved = resolveCredentials(auth);
        if (resolved.basicAuth != null) {
            val parsed = parseBasicAuth(resolved.basicAuth);
            builder.basicAuth(parsed.username(), parsed.password());
        } else if (resolved.token != null) {
            builder.apiKey(resolved.token);
        }
    }

    private record ResolvedCredentials(@Nullable String basicAuth, @Nullable String token) {}

    private static ResolvedCredentials resolveCredentials(RemoteConnectionOptions.AuthOptions auth) {
        if (auth != null) {
            if (auth.basicAuth != null) {
                return new ResolvedCredentials(auth.basicAuth, null);
            }
            if (auth.token != null) {
                return new ResolvedCredentials(null, auth.token);
            }
        }
        val envBasicAuth = System.getenv("SAPL_BASIC_AUTH");
        if (envBasicAuth != null) {
            return new ResolvedCredentials(envBasicAuth, null);
        }
        val envToken = System.getenv("SAPL_BEARER_TOKEN");
        if (envToken != null) {
            return new ResolvedCredentials(null, envToken);
        }
        return new ResolvedCredentials(null, null);
    }

    /**
     * Parsed basic authentication credentials.
     *
     * @param username the username
     * @param password the password
     */
    public record BasicAuthCredentials(String username, String password) {}

    /**
     * Parses a "username:password" string into its components.
     *
     * @param credentials the credentials string
     * @return parsed credentials
     * @throws IllegalArgumentException if the format is invalid
     */
    public static BasicAuthCredentials parseBasicAuth(String credentials) {
        val separatorIndex = credentials.indexOf(':');
        if (separatorIndex < 0) {
            throw new IllegalArgumentException(ERROR_BASIC_AUTH_FORMAT);
        }
        return new BasicAuthCredentials(credentials.substring(0, separatorIndex),
                credentials.substring(separatorIndex + 1));
    }

    /**
     * Resolves the remote PDP URL with the documented precedence: an
     * explicitly supplied flag value wins over the environment variable,
     * which in turn wins over the built-in default. The flag is treated as
     * explicitly supplied whenever it is non-null. Because absence is
     * detected by null (the {@code --url} option carries no picocli default)
     * rather than by comparing against the default string, an operator who
     * types the literal default value still overrides the environment.
     *
     * @param flagValue the parsed {@code --url} value, or null if the flag was
     * absent
     * @param envValue the {@code SAPL_URL} environment value, or null if unset
     * @param defaultValue the built-in default URL
     * @return the resolved URL
     */
    static String resolveUrl(@Nullable String flagValue, @Nullable String envValue, String defaultValue) {
        if (flagValue != null) {
            return flagValue;
        }
        return envValue != null ? envValue : defaultValue;
    }

}
