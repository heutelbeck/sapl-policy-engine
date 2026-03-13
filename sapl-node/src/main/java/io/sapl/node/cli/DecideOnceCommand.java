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
package io.sapl.node.cli;

import static io.sapl.pdp.configuration.source.PdpIdValidator.resolveHomeFolderIfPresent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.Callable;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.node.SaplNodeApplication;
import lombok.val;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Evaluates a single authorization subscription against local policies and
 * prints the decision as JSON to stdout.
 * <p>
 * Supports three policy source modes:
 * <ul>
 * <li>{@code --dir} for a directory of .sapl files</li>
 * <li>{@code --bundle} for a .saplbundle file (with signature
 * verification)</li>
 * <li>Auto-detection from {@code ~/.sapl/} when neither is specified</li>
 * </ul>
 * <p>
 * Subscription input is either named flags ({@code -s}, {@code -a},
 * {@code -r}) or a JSON file ({@code -f}). When running in a non-interactive
 * environment without explicit input, stdin is read as JSON.
 */
@Command(name = "decide-once", description = "Evaluate a single authorization decision", mixinStandardHelpOptions = true)
class DecideOnceCommand implements Callable<Integer> {

    private static final String ERROR_BOTH_POLICY_TYPES_FOUND      = "Error: Found both .sapl and .saplbundle files in %s. Use --dir or --bundle explicitly.";
    private static final String ERROR_BUNDLE_NOT_FOUND             = "Error: Bundle file not found: %s";
    private static final String ERROR_BUNDLE_VERIFICATION_REQUIRED = "Error: Bundle signature verification required. Provide --public-key <file>, place public-key.pem in ~/.sapl/, or use --no-verify.";
    private static final String ERROR_DIRECTORY_NOT_FOUND          = "Error: Policy directory not found: %s";
    private static final String ERROR_EVALUATION_FAILED            = "Error: Evaluation failed: ";
    private static final String ERROR_INVALID_JSON_VALUE           = "Error: Invalid JSON value: ";
    private static final String ERROR_NO_POLICIES_FOUND            = "Error: No policies found. Use --dir, --bundle, or create ~/.sapl/ with policy files.";
    private static final String ERROR_PUBLIC_KEY_NOT_FOUND         = "Error: Public key file not found: %s";
    private static final String ERROR_READING_SUBSCRIPTION_FILE    = "Error: Failed to read subscription file: ";
    private static final String ERROR_SAPL_HOME_NOT_FOUND          = "Error: ~/.sapl/ directory not found. Use --dir or --bundle to specify policy location.";
    private static final String ERROR_SECRETS_NOT_OBJECT           = "Error: --secrets value must be a JSON object.";
    private static final String ERROR_SUBSCRIPTION_FILE_NOT_FOUND  = "Error: Subscription file not found: %s";
    private static final String ERROR_SUBSCRIPTION_INVALID_JSON    = "Error: Invalid JSON in subscription: ";
    private static final String ERROR_SUBSCRIPTION_REQUIRED        = "Error: No subscription provided. Use -s/-a/-r flags, -f <file>, or pipe JSON to stdin.";

    @Spec
    private CommandSpec spec;

    @ArgGroup(exclusive = true)
    PolicySource policySource;

    @ArgGroup(exclusive = true)
    BundleVerification bundleVerification;

    @ArgGroup(exclusive = true, multiplicity = "0..1")
    SubscriptionInput subscriptionInput;

    @Option(names = "--trace", description = "Print full evaluation trace to stderr")
    boolean trace;

    @Option(names = "--json-report", description = "Print JSON evaluation report to stderr")
    boolean jsonReport;

    @Option(names = "--text-report", description = "Print text evaluation report to stderr")
    boolean textReport;

    Path saplHomeOverride;

    static class PolicySource {

        @Option(names = "--dir", description = "Policy directory")
        Path dir;

        @Option(names = "--bundle", description = "Bundle file")
        Path bundle;

    }

    static class BundleVerification {

        @Option(names = "--public-key", description = "Ed25519 public key for bundle verification")
        Path publicKey;

        @Option(names = "--no-verify", description = "Skip bundle signature verification")
        boolean noVerify;

    }

    static class SubscriptionInput {

        @ArgGroup(exclusive = false)
        NamedSubscription named;

        @Option(names = { "-f", "--file" }, description = "Subscription JSON file")
        Path file;

    }

    static class NamedSubscription {

        @Option(names = { "-s", "--subject" }, required = true, description = "Subject (JSON)")
        String subject;

        @Option(names = { "-a", "--action" }, required = true, description = "Action (JSON)")
        String action;

        @Option(names = { "-r", "--resource" }, required = true, description = "Resource (JSON)")
        String resource;

        @Option(names = { "-e", "--environment" }, description = "Environment (JSON)")
        String environment;

        @Option(names = "--secrets", description = "Secrets (JSON object)")
        String secrets;

    }

    private record ResolvedPolicy(String configType, String path, String publicKeyPath, boolean allowUnsigned) {}

    @Override
    public Integer call() {
        val err = spec.commandLine().getErr();
        val out = spec.commandLine().getOut();

        val resolved = resolvePolicyConfiguration();
        if (resolved == null) {
            return 1;
        }

        val springArgs = buildSpringArgs(resolved.configType(), resolved.path(), resolved.publicKeyPath(),
                resolved.allowUnsigned());

        try {
            val app = new SpringApplication(SaplNodeApplication.class);
            app.setWebApplicationType(WebApplicationType.NONE);
            app.setBannerMode(Banner.Mode.OFF);
            app.setAdditionalProfiles("cli");

            try (val context = app.run(springArgs)) {
                val pdp          = context.getBean(PolicyDecisionPoint.class);
                val mapper       = context.getBean(JsonMapper.class);
                val subscription = resolveSubscription(mapper);
                val decision     = pdp.decideOnceBlocking(subscription);
                out.println(mapper.writeValueAsString(decision));
                return SpringApplication.exit(context);
            }
        } catch (IllegalArgumentException e) {
            err.println(e.getMessage());
            return 1;
        } catch (RuntimeException e) {
            err.println(ERROR_EVALUATION_FAILED + e.getMessage());
            return 1;
        }
    }

    String[] buildSpringArgs(String configType, String path, String publicKeyPath, boolean allowUnsigned) {
        val args = new ArrayList<String>();
        args.add("--io.sapl.pdp.embedded.pdp-config-type=" + configType);
        args.add("--io.sapl.pdp.embedded.config-path=" + path);
        args.add("--io.sapl.pdp.embedded.policies-path=" + path);

        if (publicKeyPath != null) {
            args.add("--io.sapl.pdp.embedded.bundle-security.public-key-path=" + publicKeyPath);
        }
        if (allowUnsigned) {
            args.add("--io.sapl.pdp.embedded.bundle-security.allow-unsigned=true");
        }
        if (trace) {
            args.add("--io.sapl.pdp.embedded.print-trace=true");
        }
        if (jsonReport) {
            args.add("--io.sapl.pdp.embedded.print-json-report=true");
        }
        if (textReport) {
            args.add("--io.sapl.pdp.embedded.print-text-report=true");
        }
        if (trace || jsonReport || textReport) {
            args.add("--logging.level.[io.sapl.pdp.interceptors]=INFO");
        }
        return args.toArray(String[]::new);
    }

    AuthorizationSubscription buildSubscription(JsonMapper mapper) {
        if (subscriptionInput.named != null) {
            return buildNamedSubscription(mapper);
        }
        return buildFileSubscription(mapper);
    }

    private AuthorizationSubscription resolveSubscription(JsonMapper mapper) {
        if (subscriptionInput != null) {
            return buildSubscription(mapper);
        }
        if (System.console() == null) {
            try {
                val input = new String(System.in.readAllBytes(), StandardCharsets.UTF_8).trim();
                if (!input.isEmpty()) {
                    return deserializeSubscription(mapper, input);
                }
            } catch (IOException ignored) {
                // stdin not readable, fall through to error
            }
        }
        throw new IllegalArgumentException(ERROR_SUBSCRIPTION_REQUIRED);
    }

    private AuthorizationSubscription buildNamedSubscription(JsonMapper mapper) {
        val named       = subscriptionInput.named;
        val subject     = parseJson(mapper, named.subject);
        val action      = parseJson(mapper, named.action);
        val resource    = parseJson(mapper, named.resource);
        val environment = named.environment != null ? parseJson(mapper, named.environment) : null;
        val secrets     = named.secrets != null ? parseSecretsJson(mapper, named.secrets) : null;
        return AuthorizationSubscription.of(subject, action, resource, environment, secrets, mapper);
    }

    private AuthorizationSubscription buildFileSubscription(JsonMapper mapper) {
        val file = subscriptionInput.file;
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException(ERROR_SUBSCRIPTION_FILE_NOT_FOUND.formatted(file));
        }
        try {
            val content = Files.readString(file);
            return deserializeSubscription(mapper, content);
        } catch (IOException e) {
            throw new IllegalArgumentException(ERROR_READING_SUBSCRIPTION_FILE + e.getMessage(), e);
        }
    }

    private static JsonNode parseJson(JsonMapper mapper, String value) {
        try {
            return mapper.readTree(value);
        } catch (JacksonException e) {
            throw new IllegalArgumentException(ERROR_INVALID_JSON_VALUE + value, e);
        }
    }

    private static JsonNode parseSecretsJson(JsonMapper mapper, String value) {
        val node = parseJson(mapper, value);
        if (!node.isObject()) {
            throw new IllegalArgumentException(ERROR_SECRETS_NOT_OBJECT);
        }
        return node;
    }

    private static AuthorizationSubscription deserializeSubscription(JsonMapper mapper, String json) {
        try {
            return mapper.readValue(json, AuthorizationSubscription.class);
        } catch (JacksonException e) {
            throw new IllegalArgumentException(ERROR_SUBSCRIPTION_INVALID_JSON + e.getMessage(), e);
        }
    }

    private ResolvedPolicy resolvePolicyConfiguration() {
        if (policySource != null && policySource.dir != null) {
            return resolveDirectorySource();
        }
        if (policySource != null && policySource.bundle != null) {
            return resolveBundleSource();
        }
        return autoDetectPolicySource();
    }

    private ResolvedPolicy resolveDirectorySource() {
        val err = spec.commandLine().getErr();
        if (!Files.isDirectory(policySource.dir)) {
            err.println(ERROR_DIRECTORY_NOT_FOUND.formatted(policySource.dir));
            return null;
        }
        val path = policySource.dir.toAbsolutePath().toString();
        return new ResolvedPolicy("DIRECTORY", path, null, false);
    }

    private ResolvedPolicy resolveBundleSource() {
        val err = spec.commandLine().getErr();
        if (!Files.isRegularFile(policySource.bundle)) {
            err.println(ERROR_BUNDLE_NOT_FOUND.formatted(policySource.bundle));
            return null;
        }
        val path = policySource.bundle.toAbsolutePath().getParent().toString();
        return resolveBundleVerification(path);
    }

    private ResolvedPolicy autoDetectPolicySource() {
        val err      = spec.commandLine().getErr();
        val saplHome = resolveSaplHome();

        if (!Files.isDirectory(saplHome)) {
            err.println(ERROR_SAPL_HOME_NOT_FOUND);
            return null;
        }

        try (val stream = Files.list(saplHome)) {
            val files          = stream.toList();
            val hasSaplFiles   = files.stream().anyMatch(p -> p.getFileName().toString().endsWith(".sapl"));
            val hasBundleFiles = files.stream().anyMatch(p -> p.getFileName().toString().endsWith(".saplbundle"));

            if (hasSaplFiles && hasBundleFiles) {
                err.println(ERROR_BOTH_POLICY_TYPES_FOUND.formatted(saplHome));
                return null;
            }

            if (!hasSaplFiles && !hasBundleFiles) {
                err.println(ERROR_NO_POLICIES_FOUND);
                return null;
            }

            val path = saplHome.toAbsolutePath().toString();

            if (hasSaplFiles) {
                return new ResolvedPolicy("DIRECTORY", path, null, false);
            }

            return resolveBundleVerification(path);
        } catch (IOException e) {
            err.println(ERROR_SAPL_HOME_NOT_FOUND);
            return null;
        }
    }

    private ResolvedPolicy resolveBundleVerification(String path) {
        val err = spec.commandLine().getErr();

        if (bundleVerification != null && bundleVerification.publicKey != null) {
            if (!Files.isRegularFile(bundleVerification.publicKey)) {
                err.println(ERROR_PUBLIC_KEY_NOT_FOUND.formatted(bundleVerification.publicKey));
                return null;
            }
            return new ResolvedPolicy("BUNDLES", path, bundleVerification.publicKey.toAbsolutePath().toString(), false);
        }

        if (bundleVerification != null && bundleVerification.noVerify) {
            return new ResolvedPolicy("BUNDLES", path, null, true);
        }

        val saplHome = resolveSaplHome();
        if (Files.isDirectory(saplHome)) {
            val defaultKey = saplHome.resolve("public-key.pem");
            if (Files.isRegularFile(defaultKey)) {
                return new ResolvedPolicy("BUNDLES", path, defaultKey.toAbsolutePath().toString(), false);
            }
        }

        err.println(ERROR_BUNDLE_VERIFICATION_REQUIRED);
        return null;
    }

    private Path resolveSaplHome() {
        if (saplHomeOverride != null) {
            return saplHomeOverride;
        }
        return resolveHomeFolderIfPresent("~/.sapl");
    }

}
