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
package io.sapl.reactive.pdp;

import io.sapl.legacy.api.attributes.AttributeBroker;
import io.sapl.legacy.api.attributes.AttributeBrokerException;
import io.sapl.legacy.api.attributes.AttributeStorage;
import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.api.pdp.configuration.PdpData;
import io.sapl.pdp.IdFactory;
import io.sapl.pdp.LazyFastClock;
import io.sapl.pdp.ThreadLocalRandomIdFactory;
import io.sapl.pdp.VoteInterceptor;
import io.sapl.reactive.api.pdp.PolicyDecisionPoint;
import io.sapl.attributes.CachingAttributeBroker;
import io.sapl.attributes.HeapAttributeStorage;
import io.sapl.attributes.InMemoryAttributeRepository;
import io.sapl.attributes.libraries.*;
import io.sapl.functions.DefaultFunctionBroker;
import io.sapl.functions.DefaultLibraries;
import io.sapl.pdp.configuration.PdpVoterSource;
import io.sapl.pdp.configuration.bundle.BundleParser;
import io.sapl.pdp.configuration.bundle.BundleSecurityPolicy;
import io.sapl.pdp.configuration.source.*;
import lombok.val;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Fluent builder for creating a Policy Decision Point with configurable
 * function libraries, policy information points,
 * and configuration sources.
 * <h2>Basic Usage</h2>
 *
 * <pre>{@code
 * var pdpComponents = PolicyDecisionPointBuilder.withDefaults().withFunctionLibrary(MyCustomLibrary.class)
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
    private final Clock      clock;

    private boolean includeDefaultFunctionLibraries       = true;
    private boolean includeDefaultPolicyInformationPoints = true;

    private final List<Class<?>> staticFunctionLibraries       = new ArrayList<>();
    private final List<Object>   instantiatedFunctionLibraries = new ArrayList<>();
    private final List<Object>   policyInformationPoints       = new ArrayList<>();

    private AttributeStorage  attributeStorage;
    private IdFactory         idFactory;
    private WebClient.Builder webClientBuilder;

    private int             functionCacheSize = -1;
    private FunctionBroker  externalFunctionBroker;
    private AttributeBroker externalAttributeBroker;

    private final List<VoteInterceptor> interceptors = new ArrayList<>();

    private PDPConfigurationSource       configurationSource;
    private final List<PDPConfiguration> initialConfigurations = new ArrayList<>();

    private CombiningAlgorithm combiningAlgorithm;
    private final List<String> policyDocuments = new ArrayList<>();

    private static final String ERROR_SOURCE_ALREADY_REGISTERED = "A configuration source has already been registered. Only one source is allowed.";

    private PolicyDecisionPointBuilder(JsonMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock  = clock;
    }

    /**
     * Creates a builder with default function libraries and policy information
     * points enabled, using a new ObjectMapper
     * and system UTC clock.
     *
     * @return a new builder instance
     */
    public static PolicyDecisionPointBuilder withDefaults() {
        return new PolicyDecisionPointBuilder(JsonMapper.builder().build(), Clock.systemUTC());
    }

    /**
     * Creates a builder with default function libraries and policy information
     * points enabled.
     *
     * @param mapper
     * the JsonMapper for JSON processing
     * @param clock
     * the clock for time-based operations
     *
     * @return a new builder instance
     */
    public static PolicyDecisionPointBuilder withDefaults(JsonMapper mapper, Clock clock) {
        return new PolicyDecisionPointBuilder(mapper, clock);
    }

    /**
     * Creates a builder without any default libraries or PIPs, using a new
     * ObjectMapper and system UTC clock.
     *
     * @return a new builder instance with defaults disabled
     */
    public static PolicyDecisionPointBuilder withoutDefaults() {
        return withoutDefaults(JsonMapper.builder().build(), Clock.systemUTC());
    }

    /**
     * Creates a builder without any default libraries or PIPs. Use this for minimal
     * configurations or testing.
     *
     * @param mapper
     * the JsonMapper for JSON processing
     * @param clock
     * the clock for time-based operations
     *
     * @return a new builder instance with defaults disabled
     */
    public static PolicyDecisionPointBuilder withoutDefaults(JsonMapper mapper, Clock clock) {
        return new PolicyDecisionPointBuilder(mapper, clock).withoutDefaultFunctionLibraries()
                .withoutDefaultPolicyInformationPoints();
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
     * Adds a static function library class. The library will be instantiated using
     * its no-arg constructor.
     *
     * @param libraryClass
     * the function library class
     *
     * @return this builder
     */
    public PolicyDecisionPointBuilder withFunctionLibrary(Class<?> libraryClass) {
        this.staticFunctionLibraries.add(libraryClass);
        return this;
    }

    /**
     * Adds an already instantiated function library. Use this for libraries that
     * require constructor dependencies.
     *
     * @param libraryInstance
     * the function library instance
     *
     * @return this builder
     */
    public PolicyDecisionPointBuilder withFunctionLibraryInstance(Object libraryInstance) {
        this.instantiatedFunctionLibraries.add(libraryInstance);
        return this;
    }

    /**
     * Adds multiple static function library classes. Each library will be
     * instantiated using its no-arg constructor.
     * This is useful for Spring integration where libraries are collected
     * automatically.
     *
     * @param libraryClasses
     * the function library classes
     *
     * @return this builder
     */
    public PolicyDecisionPointBuilder withFunctionLibraries(Collection<Class<?>> libraryClasses) {
        this.staticFunctionLibraries.addAll(libraryClasses);
        return this;
    }

    /**
     * Adds multiple already instantiated function libraries. Use this for libraries
     * that require constructor
     * dependencies. This is useful for Spring integration where library beans are
     * collected automatically.
     *
     * @param libraryInstances
     * the function library instances
     *
     * @return this builder
     */
    public PolicyDecisionPointBuilder withFunctionLibraryInstances(Collection<?> libraryInstances) {
        this.instantiatedFunctionLibraries.addAll(libraryInstances);
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
     * When an external broker is provided, the {@code withFunctionLibrary},
     * {@code withFunctionLibraryInstance},
     * {@code withFunctionLibraries}, and {@code withFunctionLibraryInstances}
     * methods have no effect - configure
     * libraries directly on the provided broker instead.
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
     * Sets a pre-built attribute broker. When set, the builder will use this broker
     * instead of creating a new one. This
     * is useful for Spring integration where the broker is managed as a bean with
     * its own dependencies.
     * <p>
     * When an external broker is provided, the {@code withPolicyInformationPoint},
     * {@code withPolicyInformationPoints},
     * and {@code withAttributeStorage} methods have no effect - configure PIPs and
     * storage directly on the provided
     * broker instead.
     *
     * @param attributeBroker
     * the pre-configured attribute broker
     *
     * @return this builder
     */
    public PolicyDecisionPointBuilder withAttributeBroker(AttributeBroker attributeBroker) {
        this.externalAttributeBroker = attributeBroker;
        return this;
    }

    /**
     * Sets the attribute storage implementation. If not set, a heap-based in-memory
     * storage will be used.
     *
     * @param attributeStorage
     * the attribute storage
     *
     * @return this builder
     */
    public PolicyDecisionPointBuilder withAttributeStorage(AttributeStorage attributeStorage) {
        this.attributeStorage = attributeStorage;
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
     * Sets a custom WebClient builder for HTTP-based PIPs. If not set, the default
     * WebClient.builder() will be used.
     *
     * @param webClientBuilder
     * the WebClient builder
     *
     * @return this builder
     */
    public PolicyDecisionPointBuilder withWebClientBuilder(WebClient.Builder webClientBuilder) {
        this.webClientBuilder = webClientBuilder;
        return this;
    }

    /**
     * Adds a vote interceptor.
     *
     * @param interceptor the interceptor to add
     * @return this builder
     */
    public PolicyDecisionPointBuilder withInterceptor(VoteInterceptor interceptor) {
        this.interceptors.add(interceptor);
        return this;
    }

    /**
     * Adds multiple traced decision interceptors. Interceptors are applied to every
     * authorization decision in priority
     * order (lower values execute first).
     * <p>
     * This is useful for Spring integration where interceptors are collected as
     * beans.
     *
     * @param interceptors
     * the interceptors to add
     *
     * @return this builder
     */
    public PolicyDecisionPointBuilder withInterceptors(Collection<? extends VoteInterceptor> interceptors) {
        this.interceptors.addAll(interceptors);
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
     * @param sourceFactory
     * factory that creates the configuration source given a consumer
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
     *
     * @throws IllegalStateException
     * if function library initialization fails
     * @throws AttributeBrokerException
     * if PIP initialization fails
     */
    public PDPComponents build() throws AttributeBrokerException {
        val functionBroker        = resolveFunctionBroker();
        val attributeBroker       = resolveAttributeBroker();
        val configurationRegister = new PdpVoterSource(functionBroker, attributeBroker, clock);
        val timestampClock        = new LazyFastClock();
        val sortedInterceptors    = List.copyOf(interceptors);
        val pdp                   = new DynamicPolicyDecisionPoint(configurationRegister, resolveIdFactory(),
                sortedInterceptors);

        // Create default configuration from collected policies
        if (!policyDocuments.isEmpty()) {
            val algorithm = combiningAlgorithm != null ? combiningAlgorithm : CombiningAlgorithm.DEFAULT;
            val config    = new PDPConfiguration("default", "config-" + System.currentTimeMillis(), algorithm,
                    List.copyOf(policyDocuments), new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT));
            initialConfigurations.add(config);
        }

        // Load initial configurations: false signals fail-fast on compile error,
        // propagating PDPConfigurationException to the build() caller.
        for (val config : initialConfigurations) {
            configurationRegister.loadConfiguration(config, false);
        }

        if (configurationSource != null) {
            // Subscribe propagates source-side compile errors via the same
            // fail-fast path when the source emits Load with keepOldOnError=false.
            configurationSource.subscribe(configurationRegister::handle);
        }

        return new PDPComponents(pdp, configurationRegister, functionBroker, attributeBroker, configurationSource,
                timestampClock, sortedInterceptors);
    }

    private FunctionBroker resolveFunctionBroker() {
        return Objects.requireNonNullElseGet(externalFunctionBroker, () -> buildFunctionBroker(functionCacheSize,
                includeDefaultFunctionLibraries, staticFunctionLibraries, instantiatedFunctionLibraries));
    }

    /**
     * Builds a {@link FunctionBroker} with the given configuration. Shared
     * by both this builder and external assemblers (for example the Spring
     * Boot auto-configuration) so the construction logic stays in a single
     * place.
     *
     * @param functionCacheSize maximum function result cache entries; values
     * less than or equal to zero use the broker's default
     * @param includeDefaultFunctionLibraries whether to load the SAPL
     * default static libraries
     * @param staticFunctionLibraries additional static library classes to load
     * @param instantiatedFunctionLibraries additional pre-instantiated
     * libraries to load
     * @return a fully configured function broker
     */
    public static FunctionBroker buildFunctionBroker(int functionCacheSize, boolean includeDefaultFunctionLibraries,
            List<Class<?>> staticFunctionLibraries, List<Object> instantiatedFunctionLibraries) {
        val functionBroker = functionCacheSize > 0 ? new DefaultFunctionBroker(functionCacheSize)
                : new DefaultFunctionBroker();

        if (includeDefaultFunctionLibraries) {
            for (val lib : DefaultLibraries.STATIC_LIBRARIES) {
                functionBroker.loadStaticFunctionLibrary(lib);
            }
        }

        for (val lib : staticFunctionLibraries) {
            functionBroker.loadStaticFunctionLibrary(lib);
        }

        for (val lib : instantiatedFunctionLibraries) {
            functionBroker.loadInstantiatedFunctionLibrary(lib);
        }

        return functionBroker;
    }

    private AttributeBroker resolveAttributeBroker() throws AttributeBrokerException {
        if (externalAttributeBroker != null) {
            return externalAttributeBroker;
        }
        return buildAttributeBroker(attributeStorage, clock, includeDefaultPolicyInformationPoints, mapper,
                webClientBuilder, policyInformationPoints);
    }

    /**
     * Builds an {@link AttributeBroker} with the given configuration. Shared
     * by both this builder and external assemblers (for example the Spring
     * Boot auto-configuration) so the construction logic stays in a single
     * place.
     *
     * @param attributeStorage attribute storage; if {@code null}, a fresh
     * {@link HeapAttributeStorage} is used
     * @param clock clock used by the attribute repository and time-based PIPs
     * @param includeDefaultPolicyInformationPoints whether to load the SAPL
     * default PIPs (HTTP, JWT, time, X.509)
     * @param mapper JSON mapper used by the reactive web client
     * @param webClientBuilder optional WebClient builder; if {@code null},
     * {@link WebClient#builder()} is used
     * @param policyInformationPoints additional PIP instances to load
     * @return a fully configured attribute broker
     * @throws AttributeBrokerException if a PIP fails to load
     */
    public static AttributeBroker buildAttributeBroker(@Nullable AttributeStorage attributeStorage, Clock clock,
            boolean includeDefaultPolicyInformationPoints, JsonMapper mapper,
            WebClient.@Nullable Builder webClientBuilder, List<Object> policyInformationPoints)
            throws AttributeBrokerException {
        val storage             = attributeStorage != null ? attributeStorage : new HeapAttributeStorage();
        val attributeRepository = new InMemoryAttributeRepository(clock, storage);
        val attributeBroker     = new CachingAttributeBroker(attributeRepository);

        if (includeDefaultPolicyInformationPoints) {
            val reactiveWebClient = new ReactiveWebClient(mapper);
            val httpPip           = new HttpPolicyInformationPoint(reactiveWebClient);
            attributeBroker.loadPolicyInformationPointLibrary(httpPip);

            val webClient      = webClientBuilder != null ? webClientBuilder : WebClient.builder();
            val jwtKeyProvider = new JWTKeyProvider(webClient);
            val jwtPip         = new JWTPolicyInformationPoint(jwtKeyProvider);
            attributeBroker.loadPolicyInformationPointLibrary(jwtPip);

            val timePip = new TimePolicyInformationPoint(clock);
            attributeBroker.loadPolicyInformationPointLibrary(timePip);

            val x509Pip = new X509PolicyInformationPoint(clock);
            attributeBroker.loadPolicyInformationPointLibrary(x509Pip);
        }

        for (val pip : policyInformationPoints) {
            attributeBroker.loadPolicyInformationPointLibrary(pip);
        }

        return attributeBroker;
    }

    private IdFactory resolveIdFactory() {
        return idFactory != null ? idFactory : new ThreadLocalRandomIdFactory();
    }

    /**
     * Components created by the builder. Implements {@link AutoCloseable}
     * so the whole bundle can be released in one call (or via a
     * try-with-resources block) when the embedding application shuts
     * down. {@link #close()} is idempotent: each held component is
     * required to tolerate being closed more than once.
     */
    public record PDPComponents(
            PolicyDecisionPoint pdp,
            PdpVoterSource pdpVoterSource,
            FunctionBroker functionBroker,
            AttributeBroker attributeBroker,
            @Nullable PDPConfigurationSource source,
            LazyFastClock timestampClock,
            List<VoteInterceptor> sortedInterceptors) implements AutoCloseable {

        private static final String WARN_ERROR_CLOSING_RESOURCE = "Error closing {}: {}";

        /**
         * Closes every held component. Resources that are not
         * {@link AutoCloseable} are skipped silently. Exceptions thrown
         * by individual components are logged and otherwise ignored so
         * a single failure cannot prevent the rest of the cleanup.
         */
        @Override
        public void close() {
            closeAll(timestampClock, source, pdpVoterSource, attributeBroker, functionBroker, sortedInterceptors);
        }

        private static void closeAll(Object... resources) {
            for (val resource : resources) {
                if (resource instanceof Iterable<?> iterable) {
                    for (val item : iterable) {
                        closeQuietly(item);
                    }
                } else {
                    closeQuietly(resource);
                }
            }
        }

        private static void closeQuietly(@Nullable Object resource) {
            if (!(resource instanceof AutoCloseable closeable)) {
                return;
            }
            try {
                closeable.close();
            } catch (Exception e) {
                log.warn(WARN_ERROR_CLOSING_RESOURCE, resource.getClass().getSimpleName(), e.getMessage());
            }
        }
    }
}
