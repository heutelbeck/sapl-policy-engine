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
package io.sapl.pdp;

import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.api.pdp.configuration.PdpData;
import io.sapl.api.stream.BlockingWebClient;
import io.sapl.api.stream.RealTimeScheduler;
import io.sapl.api.stream.TimeScheduler;
import io.sapl.attributes.libraries.HttpPolicyInformationPoint;
import io.sapl.attributes.libraries.JWTKeyProvider;
import io.sapl.attributes.libraries.JWTPolicyInformationPoint;
import io.sapl.attributes.libraries.TimePolicyInformationPoint;
import io.sapl.attributes.libraries.X509PolicyInformationPoint;
import io.sapl.attributes.broker.AttributeBroker;
import io.sapl.attributes.broker.AttributeRepository;
import io.sapl.attributes.broker.pip.PolicyInformationPointAttributeBroker;
import io.sapl.attributes.broker.pip.PipLoadException;
import io.sapl.attributes.broker.repository.InMemoryAttributeRepository;
import io.sapl.api.pdp.DecisionInterceptor;
import io.sapl.api.pdp.SubscriptionLifecycleListener;
import io.sapl.functions.DefaultFunctionBroker;
import io.sapl.functions.DefaultLibraries;
import io.sapl.pdp.configuration.ConfigurationIds;
import io.sapl.pdp.configuration.PdpVoterSource;
import io.sapl.pdp.configuration.bundle.BundleParser;
import io.sapl.pdp.configuration.bundle.BundleSecurityPolicy;
import io.sapl.pdp.configuration.source.*;
import io.sapl.pdp.plugins.PluginsBundle;
import io.sapl.pdp.plugins.PluginsSource;
import io.sapl.pdp.plugins.StaticPluginsSource;
import lombok.val;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Fluent builder for creating a Policy Decision Point with configurable
 * function libraries, policy information points,
 * and configuration sources.
 * <h2>Basic Usage</h2>
 *
 * <pre>{@code
 * var pdpComponents = PolicyDecisionPointBuilder.withDefaults().withFunctionLibrary(new MyCustomLibrary())
 *         .withPolicyInformationPoint(new MyCustomPIP()).build();
 *
 * var pdp = pdpComponents.pdp();
 * pdpComponents.pdpRegister().loadConfiguration(pdpConfiguration, false);
 * }</pre>
 *
 * <h2>Configuration Sources</h2>
 *
 * <pre>{@code
 * // From filesystem directory
 * var pdpComponents = PolicyDecisionPointBuilder.withDefaults().withDirectorySource(Path.of("/policies")).build();
 *
 * // From bundle files
 * var securityPolicy = BundleSecurityPolicy.builder(publicKey).build();
 * var pdpComponents = PolicyDecisionPointBuilder.withDefaults()
 *         .withBundleDirectorySource(Path.of("/bundles"), securityPolicy).build();
 *
 * // From classpath resources
 * var pdpComponents = PolicyDecisionPointBuilder.withDefaults().withResourcesSource("/policies").build();
 * }</pre>
 *
 * <h2>Lifecycle</h2>
 * <p>
 * The returned {@link PDPComponents} is an {@link AutoCloseable}. Call
 * {@link PDPComponents#close()} (or use a try-with-resources block) on
 * shutdown to release every component held by the builder.
 * </p>
 *
 * @see PDPComponents
 */
@Slf4j
public class PolicyDecisionPointBuilder {

    private final JsonMapper mapper;
    private Clock            clock;

    // Observability timestamp source (decision trace, attribute freshness),
    // distinct from the temporal
    // clock. Null defaults to the temporal clock. ownsTimestampSource gates
    // closing.
    private InstantSource timestampSource;
    private boolean       ownsTimestampSource = false;

    private boolean includeDefaultFunctionLibraries       = true;
    private boolean includeDefaultPolicyInformationPoints = true;

    private final List<Object> functionLibraries       = new ArrayList<>();
    private final List<Object> policyInformationPoints = new ArrayList<>();

    private IdFactory idFactory;

    private int                 functionCacheSize = -1;
    private FunctionBroker      externalFunctionBroker;
    private PluginsSource       externalPluginsSource;
    private AttributeBroker     externalAttributeBroker;
    private AttributeRepository externalRepository;

    private final List<DecisionInterceptor>           decisionInterceptors = new ArrayList<>();
    private final List<SubscriptionLifecycleListener> lifecycleListeners   = new ArrayList<>();

    private PDPConfigurationSource       configurationSource;
    private final List<PDPConfiguration> initialConfigurations = new ArrayList<>();

    private CombiningAlgorithm combiningAlgorithm;
    private final List<String> policyDocuments = new ArrayList<>();

    private static final String ERROR_NO_PLUGINS_AVAILABLE      = "Cannot build the PDP: no plugins bundle is available from the plugins source.";
    private static final String ERROR_SOURCE_ALREADY_REGISTERED = "A configuration source has already been registered. Only one source is allowed.";
    private static final String WARN_ERROR_CLOSING_RESOURCE     = "Error closing {} during failed PDP build: {}.";

    private PolicyDecisionPointBuilder(JsonMapper mapper) {
        this.mapper = mapper;
        this.clock  = Clock.systemUTC();
    }

    /**
     * Creates a builder with default function libraries and policy information
     * points enabled, using a new ObjectMapper.
     *
     * @return a new builder instance
     */
    public static PolicyDecisionPointBuilder withDefaults() {
        return new PolicyDecisionPointBuilder(JsonMapper.builder().build());
    }

    /**
     * Creates a builder with default function libraries and policy information
     * points enabled.
     *
     * @param mapper the JsonMapper for JSON processing
     * @return a new builder instance
     */
    public static PolicyDecisionPointBuilder withDefaults(JsonMapper mapper) {
        return new PolicyDecisionPointBuilder(mapper);
    }

    /**
     * Creates a builder without any default libraries or PIPs, using a new
     * ObjectMapper.
     *
     * @return a new builder instance with defaults disabled
     */
    public static PolicyDecisionPointBuilder withoutDefaults() {
        return withoutDefaults(JsonMapper.builder().build());
    }

    /**
     * Creates a builder without any default libraries or PIPs. Use this for minimal
     * configurations or testing.
     *
     * @param mapper the JsonMapper for JSON processing
     * @return a new builder instance with defaults disabled
     */
    public static PolicyDecisionPointBuilder withoutDefaults(JsonMapper mapper) {
        return new PolicyDecisionPointBuilder(mapper).withoutDefaultFunctionLibraries()
                .withoutDefaultPolicyInformationPoints();
    }

    /**
     * Sets the clock that drives time-based policy reasoning: the time PIP,
     * certificate validity, JWT expiry, and scheduling. Defaults to
     * {@link Clock#systemUTC()}. This is independent of the timestamp source for
     * observability stamps (see {@link #withTimestampSource} and
     * {@link #withCoarseTimestamps}).
     *
     * @param clock the temporal-reasoning clock
     * @return this builder
     */
    public PolicyDecisionPointBuilder withClock(Clock clock) {
        this.clock = clock;
        return this;
    }

    /**
     * Disables loading of default function libraries.
     *
     * @return this builder
     */
    public PolicyDecisionPointBuilder withoutDefaultFunctionLibraries() {
        this.includeDefaultFunctionLibraries = false;
        return this;
    }

    /**
     * Disables loading of default policy information points.
     *
     * @return this builder
     */
    public PolicyDecisionPointBuilder withoutDefaultPolicyInformationPoints() {
        this.includeDefaultPolicyInformationPoints = false;
        return this;
    }

    /**
     * Sets the source used for observability timestamps (decision-trace
     * timestamps and attribute value freshness). This is independent of the
     * temporal-reasoning clock, which keeps driving time-based policy logic
     * (JWT expiry, certificate validity, the time PIP, scheduling). The caller
     * retains ownership: the source is not closed when the PDP is closed.
     *
     * @param source the timestamp source
     * @return this builder
     */
    public PolicyDecisionPointBuilder withTimestampSource(InstantSource source) {
        this.timestampSource     = source;
        this.ownsTimestampSource = false;
        return this;
    }

    /**
     * Uses a coarse-resolution cached clock ({@link CoarseClock}) for
     * observability timestamps. The clock is created and owned by the resulting
     * PDP and is closed when the PDP is closed. The cached read is far cheaper
     * than {@link java.time.Clock#instant()} on high-throughput timestamping
     * paths, at the cost of coarser (interval-bounded) timestamp precision.
     * Temporal reasoning is unaffected and stays on the accurate clock.
     *
     * @return this builder
     */
    public PolicyDecisionPointBuilder withCoarseTimestamps() {
        this.timestampSource     = new CoarseClock();
        this.ownsTimestampSource = true;
        return this;
    }

    /**
     * Adds a function library instance.
     *
     * @param libraryInstance the function library instance
     * @return this builder
     */
    public PolicyDecisionPointBuilder withFunctionLibrary(Object libraryInstance) {
        this.functionLibraries.add(libraryInstance);
        return this;
    }

    /**
     * Adds multiple function library instances. Useful for Spring integration
     * where library beans are collected automatically.
     *
     * @param libraryInstances the function library instances
     * @return this builder
     */
    public PolicyDecisionPointBuilder withFunctionLibraries(Collection<?> libraryInstances) {
        this.functionLibraries.addAll(libraryInstances);
        return this;
    }

    /**
     * Adds a policy information point instance.
     *
     * @param pip
     * the policy information point
     *
     * @return this builder
     */
    public PolicyDecisionPointBuilder withPolicyInformationPoint(Object pip) {
        this.policyInformationPoints.add(pip);
        return this;
    }

    /**
     * Adds multiple policy information point instances. This is useful for Spring
     * integration where PIPs are collected
     * automatically.
     *
     * @param pips
     * the policy information points
     *
     * @return this builder
     */
    public PolicyDecisionPointBuilder withPolicyInformationPoints(Collection<?> pips) {
        this.policyInformationPoints.addAll(pips);
        return this;
    }

    /**
     * Sets a pre-built function broker. When set, the builder will use this broker
     * instead of creating a new one. This
     * is useful for Spring integration where the broker is managed as a bean with
     * its own dependencies.
     * <p>
     * When an external broker is provided, the {@code withFunctionLibrary} and
     * {@code withFunctionLibraries} methods have no effect - configure libraries
     * directly on the provided broker instead.
     *
     * @param functionBroker
     * the pre-configured function broker
     *
     * @return this builder
     */
    public PolicyDecisionPointBuilder withFunctionBroker(FunctionBroker functionBroker) {
        this.externalFunctionBroker = functionBroker;
        return this;
    }

    /**
     * Sets a {@link PluginsSource}. When set the source overrides every
     * other plugin-side configuration on the builder (function libraries,
     * decision interceptors, lifecycle listeners). The voter source
     * subscribes to it for snapshots. Emissions after build time trigger
     * recompile of every retained PDP configuration.
     *
     * @param source the plugins source
     * @return this builder
     */
    public PolicyDecisionPointBuilder withPluginsSource(PluginsSource source) {
        this.externalPluginsSource = source;
        return this;
    }

    /**
     * Sets the maximum number of entries in the function result cache.
     * SAPL functions are pure and side-effect-free, so results are cached
     * across evaluations. Uses Window-TinyLFU eviction via Caffeine.
     * Default is 10,000 entries.
     *
     * @param size maximum cache entries
     * @return this builder
     */
    public PolicyDecisionPointBuilder withFunctionCacheSize(int size) {
        this.functionCacheSize = size;
        return this;
    }

    /**
     * Sets a custom {@link AttributeBroker}. When set, this broker is
     * used as-is and the builder's catalog and fallback wiring are
     * bypassed. Use {@link #withRepository(AttributeRepository)}
     * instead if the goal is only to swap the fallback repository.
     *
     * @param attributeBroker the pre-configured attribute broker
     * @return this builder
     */
    public PolicyDecisionPointBuilder withAttributeBroker(AttributeBroker attributeBroker) {
        this.externalAttributeBroker = attributeBroker;
        return this;
    }

    /**
     * Sets the fallback repository for invocations that have no
     * matching PIP. The PIP broker routes catalog-matched invocations
     * through the loaded PIPs and routes the rest through this
     * repository. If not set, a default {@link InMemoryAttributeRepository}
     * is used.
     * <p>
     * This setter has no effect when
     * {@link #withAttributeBroker(AttributeBroker)} supplies a fully
     * custom top-level broker.
     *
     * @param repository the fallback {@link AttributeRepository}
     * @return this builder
     */
    public PolicyDecisionPointBuilder withRepository(AttributeRepository repository) {
        this.externalRepository = repository;
        return this;
    }

    /**
     * Sets a custom ID factory for generating subscription IDs. If not set, a
     * default factory will be used.
     *
     * @param idFactory
     * the ID factory
     *
     * @return this builder
     */
    public PolicyDecisionPointBuilder withIdFactory(IdFactory idFactory) {
        this.idFactory = idFactory;
        return this;
    }

    /**
     * Adds a decision interceptor that observes every
     * {@link io.sapl.api.pdp.TracedDecision}
     * produced by the PDP.
     *
     * @param interceptor the decision interceptor to add
     * @return this builder
     */
    public PolicyDecisionPointBuilder withDecisionInterceptor(DecisionInterceptor interceptor) {
        this.decisionInterceptors.add(interceptor);
        return this;
    }

    /**
     * Adds a collection of decision interceptors. Useful for Spring
     * integration where interceptor beans are collected automatically.
     *
     * @param interceptors the decision interceptors to add
     * @return this builder
     */
    public PolicyDecisionPointBuilder withDecisionInterceptors(Collection<? extends DecisionInterceptor> interceptors) {
        this.decisionInterceptors.addAll(interceptors);
        return this;
    }

    /**
     * Adds a subscription lifecycle listener that receives one
     * {@code onSubscribe} call when an authorization subscription stream
     * begins and one {@code onUnsubscribe} call when it ends.
     *
     * @param listener the lifecycle listener to add
     * @return this builder
     */
    public PolicyDecisionPointBuilder withSubscriptionLifecycleListener(SubscriptionLifecycleListener listener) {
        this.lifecycleListeners.add(listener);
        return this;
    }

    /**
     * Adds a collection of subscription lifecycle listeners. Useful for
     * Spring integration where listener beans are collected
     * automatically.
     *
     * @param listeners the lifecycle listeners to add
     * @return this builder
     */
    public PolicyDecisionPointBuilder withSubscriptionLifecycleListeners(
            Collection<? extends SubscriptionLifecycleListener> listeners) {
        this.lifecycleListeners.addAll(listeners);
        return this;
    }

    /**
     * Sets a custom configuration source factory. The factory receives a consumer
     * and must return a source that will
     * invoke the consumer when configurations are loaded or updated.
     * <p>
     * The source is created during build. The consumer is connected to the
     * configuration register so configurations are
     * automatically loaded into the PDP.
     * <p>
     * Only one configuration source can be registered. Attempting to register
     * multiple sources will throw an exception.
     *
     * @param configurationSource
     * the configuration source to register
     *
     * @return this builder
     *
     * @throws IllegalStateException
     * if a configuration source has already been registered
     */
    public PolicyDecisionPointBuilder withConfigurationSource(PDPConfigurationSource configurationSource) {
        if (this.configurationSource != null) {
            throw new IllegalStateException(ERROR_SOURCE_ALREADY_REGISTERED);
        }
        this.configurationSource = configurationSource;
        return this;
    }

    /**
     * Loads policies from a filesystem directory. The directory should contain
     * pdp.json (optional) and .sapl files.
     * Changes are monitored and hot-reloaded.
     *
     * @param directoryPath
     * the path to the policy directory
     *
     * @return this builder
     */
    public PolicyDecisionPointBuilder withDirectorySource(Path directoryPath) {
        return withConfigurationSource(new DirectoryPDPConfigurationSource(directoryPath));
    }

    /**
     * Loads policies from a filesystem directory with a custom PDP ID.
     * <p>
     * The configuration ID is determined from pdp.json if present, otherwise
     * auto-generated in the format:
     * {@code dir:<path>@<timestamp>@sha256:<hash>}
     * </p>
     *
     * @param directoryPath
     * the path to the policy directory
     * @param pdpId
     * the PDP identifier
     *
     * @return this builder
     */
    public PolicyDecisionPointBuilder withDirectorySource(Path directoryPath, String pdpId) {
        return withConfigurationSource(new DirectoryPDPConfigurationSource(directoryPath, pdpId));
    }

    /**
     * Loads policies from multiple subdirectories, where each subdirectory
     * represents a separate PDP configuration. The
     * subdirectory name becomes the PDP ID. Changes are monitored and hot-reloaded.
     * <p>
     * Configuration IDs are determined from pdp.json in each subdirectory, or
     * auto-generated if not present.
     * </p>
     *
     * @param directoryPath
     * the root directory containing PDP subdirectories
     *
     * @return this builder
     */
    public PolicyDecisionPointBuilder withMultiDirectorySource(Path directoryPath) {
        return withConfigurationSource(new MultiDirectoryPDPConfigurationSource(directoryPath));
    }

    /**
     * Loads policies from multiple subdirectories, with optional root-level files
     * as "default" PDP.
     * <p>
     * Configuration IDs are determined from pdp.json in each subdirectory (or
     * root), or auto-generated if not present.
     * </p>
     *
     * @param directoryPath
     * the root directory containing PDP subdirectories
     * @param includeRootFiles
     * if true, root-level .sapl and pdp.json files are loaded as "default" PDP
     *
     * @return this builder
     */
    public PolicyDecisionPointBuilder withMultiDirectorySource(Path directoryPath, boolean includeRootFiles) {
        return withConfigurationSource(new MultiDirectoryPDPConfigurationSource(directoryPath, includeRootFiles));
    }

    /**
     * Loads policies from bundle files (.saplbundle) in a directory. Each bundle
     * represents a separate PDP
     * configuration. Changes are monitored and hot-reloaded.
     * <p>
     * The security policy determines how bundle signatures are verified. Use
     * {@link BundleSecurityPolicy#builder(java.security.PublicKey)} for
     * production environments.
     * </p>
     * <p>
     * Each bundle must contain a pdp.json file with a {@code configurationId}
     * field. The PDP ID is derived from the
     * bundle filename (minus the .saplbundle extension).
     * </p>
     *
     * @param bundleDirectoryPath
     * the path to the directory containing .saplbundle files
     * @param securityPolicy
     * the security policy for bundle signature verification
     *
     * @return this builder
     */
    public PolicyDecisionPointBuilder withBundleDirectorySource(Path bundleDirectoryPath,
            BundleSecurityPolicy securityPolicy) {
        return withConfigurationSource(new BundlePDPConfigurationSource(bundleDirectoryPath, securityPolicy));
    }

    /**
     * Loads policies from a remote HTTP server that serves {@code .saplbundle}
     * files. Each configured PDP ID is fetched independently with change
     * detection via HTTP conditional requests (ETag).
     *
     * @param config
     * the remote bundle source configuration
     *
     * @return this builder
     */
    public PolicyDecisionPointBuilder withRemoteBundleSource(RemoteBundleSourceConfig config) {
        return withConfigurationSource(new RemoteBundlePDPConfigurationSource(config));
    }

    /**
     * Loads policies from classpath resources. This is useful for embedded policies
     * shipped with your application.
     * Supports both single-PDP (root-level files) and multi-PDP (subdirectories)
     * layouts.
     * <p>
     * Configuration IDs are determined from pdp.json if present, otherwise
     * auto-generated in the format:
     * {@code res:<path>@sha256:<hash>}
     * </p>
     *
     * @param resourcePath
     * the classpath resource path (e.g., "/policies")
     *
     * @return this builder
     */
    public PolicyDecisionPointBuilder withResourcesSource(String resourcePath) {
        return withConfigurationSource(new ResourcesPDPConfigurationSource(resourcePath));
    }

    /**
     * Loads policies from the default classpath resource path "/policies".
     * <p>
     * Configuration IDs are determined from pdp.json if present, otherwise
     * auto-generated in the format:
     * {@code res:<path>@sha256:<hash>}
     * </p>
     *
     * @return this builder
     */
    public PolicyDecisionPointBuilder withResourcesSource() {
        return withConfigurationSource(new ResourcesPDPConfigurationSource());
    }

    /**
     * Loads a configuration from a bundle byte array. This is useful for receiving
     * bundles via HTTP uploads or message
     * queues.
     * <p>
     * The security policy determines how bundle signatures are verified. Use
     * {@link BundleSecurityPolicy#builder(java.security.PublicKey)} for
     * production environments.
     * </p>
     * <p>
     * The bundle must contain a pdp.json file with a {@code configurationId} field.
     * </p>
     *
     * @param bundleBytes
     * the bundle data
     * @param pdpId
     * the PDP identifier
     * @param securityPolicy
     * the security policy for bundle signature verification
     *
     * @return this builder
     */
    public PolicyDecisionPointBuilder withBundle(byte[] bundleBytes, String pdpId,
            BundleSecurityPolicy securityPolicy) {
        this.initialConfigurations.add(BundleParser.parse(bundleBytes, pdpId, securityPolicy));
        return this;
    }

    /**
     * Loads a configuration from a bundle input stream. This is useful for
     * receiving bundles via HTTP uploads.
     * <p>
     * The security policy determines how bundle signatures are verified. Use
     * {@link BundleSecurityPolicy#builder(java.security.PublicKey)} for
     * production environments.
     * </p>
     * <p>
     * The bundle must contain a pdp.json file with a {@code configurationId} field.
     * </p>
     *
     * @param bundleStream
     * the bundle input stream
     * @param pdpId
     * the PDP identifier
     * @param securityPolicy
     * the security policy for bundle signature verification
     *
     * @return this builder
     */
    public PolicyDecisionPointBuilder withBundle(InputStream bundleStream, String pdpId,
            BundleSecurityPolicy securityPolicy) {
        this.initialConfigurations.add(BundleParser.parse(bundleStream, pdpId, securityPolicy));
        return this;
    }

    /**
     * Loads a configuration from a bundle file path.
     * <p>
     * The security policy determines how bundle signatures are verified. Use
     * {@link BundleSecurityPolicy#builder(java.security.PublicKey)} for
     * production environments.
     * </p>
     * <p>
     * The bundle must contain a pdp.json file with a {@code configurationId} field.
     * </p>
     *
     * @param bundlePath
     * the path to the .saplbundle file
     * @param pdpId
     * the PDP identifier
     * @param securityPolicy
     * the security policy for bundle signature verification
     *
     * @return this builder
     */
    public PolicyDecisionPointBuilder withBundle(Path bundlePath, String pdpId, BundleSecurityPolicy securityPolicy) {
        this.initialConfigurations.add(BundleParser.parse(bundlePath, pdpId, securityPolicy));
        return this;
    }

    /**
     * Loads a pre-built PDP configuration. This is useful when you have already
     * constructed a configuration
     * programmatically.
     *
     * @param configuration
     * the PDP configuration
     *
     * @return this builder
     */
    public PolicyDecisionPointBuilder withConfiguration(PDPConfiguration configuration) {
        this.initialConfigurations.add(configuration);
        return this;
    }

    /**
     * Sets the combining algorithm for the default PDP configuration.
     * If not set, {@link CombiningAlgorithm#DEFAULT} is used.
     *
     * @param algorithm the combining algorithm
     * @return this builder
     */
    public PolicyDecisionPointBuilder withCombiningAlgorithm(CombiningAlgorithm algorithm) {
        this.combiningAlgorithm = algorithm;
        return this;
    }

    /**
     * Adds a policy document to the default PDP configuration.
     *
     * @param policyDocument the SAPL policy document text
     * @return this builder
     */
    public PolicyDecisionPointBuilder withPolicy(String policyDocument) {
        this.policyDocuments.add(policyDocument);
        return this;
    }

    /**
     * Adds multiple policy documents to the default PDP configuration.
     *
     * @param policyDocuments the SAPL policy document texts
     * @return this builder
     */
    public PolicyDecisionPointBuilder withPolicies(String... policyDocuments) {
        this.policyDocuments.addAll(List.of(policyDocuments));
        return this;
    }

    /**
     * Builds the PDP and all related components.
     *
     * @return the PDP components including the PDP and configuration register
     * @throws IllegalStateException if function library initialization fails
     * @throws PipLoadException if PIP loading into the attribute broker
     * fails
     */
    public PDPComponents build() {
        // Default the timestamp source to the temporal clock unless the caller opted
        // into a separate one.
        val resolvedTimestampSource = timestampSource != null ? timestampSource : clock;
        val ownsResolvedSource      = ownsTimestampSource;
        val attributeBroker         = resolveAttributeBroker(resolvedTimestampSource);
        val pluginsSource           = resolvePluginsSource();
        val ownsPluginsSource       = externalPluginsSource == null;
        val voterSource             = new PdpVoterSource(pluginsSource, clock);
        val blockingPdp             = new BlockingPolicyDecisionPoint(voterSource, attributeBroker, resolveIdFactory(),
                resolvedTimestampSource);

        try {
            // Create default configuration from collected policies
            if (!policyDocuments.isEmpty()) {
                val algorithm = combiningAlgorithm != null ? combiningAlgorithm : CombiningAlgorithm.DEFAULT;
                val config    = new PDPConfiguration("default", ConfigurationIds.generate("config"), algorithm,
                        List.copyOf(policyDocuments), new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT));
                initialConfigurations.add(config);
            }

            // Load initial configurations: false signals fail-fast on compile error,
            // propagating PDPConfigurationException to the build() caller.
            for (val config : initialConfigurations) {
                voterSource.loadConfiguration(config, false);
            }

            if (configurationSource != null) {
                // Subscribe propagates source-side compile errors via the same
                // fail-fast path when the source emits Load with keepOldOnError=false.
                configurationSource.subscribe(voterSource::handle);
            }

            val plugins = voterSource.getPlugins();
            if (plugins == null) {
                throw new IllegalStateException(ERROR_NO_PLUGINS_AVAILABLE);
            }
            return new PDPComponents(blockingPdp, voterSource, plugins.functionBroker(), attributeBroker,
                    configurationSource, resolvedTimestampSource, ownsResolvedSource, plugins.decisionInterceptors(),
                    plugins.lifecycleListeners(), pluginsSource, ownsPluginsSource);
        } catch (RuntimeException e) {
            // A failed build never transfers ownership to PDPComponents, so close what this
            // builder created.
            closeQuietly(voterSource);
            if (ownsPluginsSource) {
                closeQuietly(pluginsSource);
            }
            if (ownsResolvedSource && resolvedTimestampSource instanceof AutoCloseable closeableSource) {
                closeQuietly(closeableSource);
            }
            if (externalAttributeBroker == null) {
                closeQuietly(attributeBroker);
            }
            throw e;
        }
    }

    private void closeQuietly(AutoCloseable resource) {
        try {
            resource.close();
        } catch (Exception e) {
            log.warn(WARN_ERROR_CLOSING_RESOURCE, resource.getClass().getSimpleName(), e.getMessage());
        }
    }

    private PluginsSource resolvePluginsSource() {
        if (externalPluginsSource != null) {
            return externalPluginsSource;
        }
        val broker = externalFunctionBroker != null ? externalFunctionBroker
                : buildFunctionBroker(functionCacheSize, includeDefaultFunctionLibraries, functionLibraries);
        val bundle = new PluginsBundle(broker, List.copyOf(decisionInterceptors), List.copyOf(lifecycleListeners));
        return new StaticPluginsSource(bundle);
    }

    /**
     * Builds a {@link FunctionBroker} with the given configuration. Shared
     * by both this builder and external assemblers (for example the Spring
     * Boot auto-configuration) so the construction logic stays in a single
     * place.
     *
     * @param functionCacheSize maximum function result cache entries. Values
     * less than or equal to zero use the broker's default.
     * @param includeDefaultFunctionLibraries whether to load the SAPL
     * default libraries
     * @param additionalFunctionLibraries additional library instances to load
     * @return a fully configured function broker
     */
    public static FunctionBroker buildFunctionBroker(int functionCacheSize, boolean includeDefaultFunctionLibraries,
            List<?> additionalFunctionLibraries) {
        val functionBroker = functionCacheSize > 0 ? new DefaultFunctionBroker(functionCacheSize)
                : new DefaultFunctionBroker();

        if (includeDefaultFunctionLibraries) {
            for (val lib : DefaultLibraries.defaults()) {
                functionBroker.load(lib);
            }
        }

        for (val lib : additionalFunctionLibraries) {
            functionBroker.load(lib);
        }

        return functionBroker;
    }

    private AttributeBroker resolveAttributeBroker(InstantSource timestampSource) {
        if (externalAttributeBroker != null) {
            return externalAttributeBroker;
        }
        val repository = externalRepository != null ? externalRepository : new InMemoryAttributeRepository();
        return buildPolicyInformationPointAttributeBroker(clock, timestampSource, mapper,
                includeDefaultPolicyInformationPoints, policyInformationPoints, repository);
    }

    /**
     * Builds an {@link PolicyInformationPointAttributeBroker} configured with the
     * SAPL default PIPs (when {@code includeDefaults} is true) and any
     * additional PIPs passed in. Shared by this builder and external
     * assemblers (for example the Spring Boot auto-configuration) so
     * the construction logic stays in a single place.
     *
     * @param clock clock used by the time-based PIPs and the
     * {@link TimeScheduler}
     * @param mapper JSON mapper used by the {@link BlockingWebClient}
     * for HTTP-based PIPs
     * @param includeDefaults whether to load the SAPL default PIPs
     * (HTTP, JWT, time, X.509)
     * @param additionalPips additional PIP instances to load on top of
     * the defaults
     * @return a fully configured attribute broker
     * @throws PipLoadException if a PIP fails to load
     */
    public static PolicyInformationPointAttributeBroker buildPolicyInformationPointAttributeBroker(Clock clock,
            JsonMapper mapper, boolean includeDefaults, List<Object> additionalPips) {
        return buildPolicyInformationPointAttributeBroker(clock, mapper, includeDefaults, additionalPips, null);
    }

    /**
     * Same as the 4-arg overload, with an explicit fallback
     * repository for invocations that have no matching PIP. A
     * {@code null} fallback yields a broker that surfaces
     * {@link Value#UNDEFINED} for unmatched invocations.
     *
     * @param clock clock used by the time-based PIPs and the
     * {@link TimeScheduler}
     * @param mapper JSON mapper used by the {@link BlockingWebClient}
     * for HTTP-based PIPs
     * @param includeDefaults whether to load the SAPL default PIPs
     * (HTTP, JWT, time, X.509)
     * @param additionalPips additional PIP instances to load on top of
     * the defaults
     * @param fallback fallback repository for unmatched invocations
     * @return a fully configured attribute broker
     * @throws PipLoadException if a PIP fails to load
     */
    public static PolicyInformationPointAttributeBroker buildPolicyInformationPointAttributeBroker(Clock clock,
            JsonMapper mapper, boolean includeDefaults, List<Object> additionalPips, AttributeRepository fallback) {
        return buildPolicyInformationPointAttributeBroker(clock, clock, mapper, includeDefaults, additionalPips,
                fallback);
    }

    /**
     * Same as the 5-arg overload, but with an explicit timestamp source for the
     * broker's attribute value-arrival stamps, kept separate from the temporal
     * {@code clock} that drives the time-based PIPs.
     *
     * @param clock clock used by the time-based PIPs and the
     * {@link TimeScheduler}
     * @param timestampSource source for attribute value-arrival timestamps
     * @param mapper JSON mapper used by the {@link BlockingWebClient} for
     * HTTP-based PIPs
     * @param includeDefaults whether to load the SAPL default PIPs (HTTP, JWT,
     * time, X.509)
     * @param additionalPips additional PIP instances to load on top of the
     * defaults
     * @param fallback fallback repository for unmatched invocations
     * @return a fully configured attribute broker
     * @throws PipLoadException if a PIP fails to load
     */
    public static PolicyInformationPointAttributeBroker buildPolicyInformationPointAttributeBroker(Clock clock,
            InstantSource timestampSource, JsonMapper mapper, boolean includeDefaults, List<Object> additionalPips,
            AttributeRepository fallback) {
        val broker    = new PolicyInformationPointAttributeBroker(Duration.ZERO, fallback, timestampSource);
        val scheduler = new RealTimeScheduler(clock);

        if (includeDefaults) {
            // 5 second TCP connect cap so a stalled remote PIP host does not
            // hang the calling decision indefinitely.
            val httpClient  = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            val webClient   = new BlockingWebClient(mapper, httpClient);
            val keyProvider = new JWTKeyProvider(httpClient, clock);

            broker.load(new TimePolicyInformationPoint(clock, scheduler));
            broker.load(new X509PolicyInformationPoint(clock, scheduler));
            broker.load(new HttpPolicyInformationPoint(webClient));
            broker.load(new JWTPolicyInformationPoint(keyProvider, clock, scheduler));
        }

        for (val pip : additionalPips) {
            broker.load(pip);
        }

        return broker;
    }

    private IdFactory resolveIdFactory() {
        return idFactory != null ? idFactory : new ThreadLocalRandomIdFactory();
    }

}
