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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.attributes.AttributeBroker;
import io.sapl.api.attributes.AttributeBrokerException;
import io.sapl.api.attributes.AttributeStorage;
import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.api.pdp.PDPConfiguration;
import io.sapl.api.pdp.PdpData;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.attributes.CachingAttributeBroker;
import io.sapl.attributes.HeapAttributeStorage;
import io.sapl.attributes.InMemoryAttributeRepository;
import io.sapl.attributes.libraries.HttpPolicyInformationPoint;
import io.sapl.attributes.libraries.JWTKeyProvider;
import io.sapl.attributes.libraries.JWTPolicyInformationPoint;
import io.sapl.attributes.libraries.ReactiveWebClient;
import io.sapl.attributes.libraries.TimePolicyInformationPoint;
import io.sapl.functions.DefaultFunctionBroker;
import io.sapl.functions.DefaultLibraries;
import io.sapl.pdp.configuration.BundlePDPConfigurationSource;
import io.sapl.pdp.configuration.DirectoryPDPConfigurationSource;
import io.sapl.pdp.configuration.MultiDirectoryPDPConfigurationSource;
import io.sapl.pdp.configuration.PDPConfigurationSource;
import io.sapl.pdp.configuration.ResourcesPDPConfigurationSource;
import io.sapl.pdp.configuration.bundle.BundleParser;
import io.sapl.pdp.configuration.bundle.BundleSecurityPolicy;
import lombok.val;
import org.jspecify.annotations.Nullable;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

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
 * var securityPolicy = BundleSecurityPolicy.requireSignature(publicKey);
 * var pdpComponents = PolicyDecisionPointBuilder.withDefaults()
 *         .withBundleDirectorySource(Path.of("/bundles"), securityPolicy).build();
 *
 * // From classpath resources
 * var pdpComponents = PolicyDecisionPointBuilder.withDefaults().withResourcesSource("/policies").build();
 * }</pre>
 *
 * <h2>Spring Integration</h2>
 * <p>
 * When using a configuration source that monitors directories for changes,
 * ensure the source is disposed when the
 * application shuts down. The {@link PDPComponents#source()} method returns the
 * source (if any) which can be disposed
 * directly.
 * </p>
 * <p>
 * Spring does not automatically call {@code dispose()} on Reactor's
 * {@link reactor.core.Disposable} interface. When
 * exposing a configuration source as a Spring bean, explicitly specify the
 * destroy method:
 * </p>
 *
 * <pre>
 * {
 *     &#64;code
 *     &#64;Configuration
 *     public class PdpConfiguration {
 *
 *         &#64;Bean
 *         public PDPComponents pdpComponents(PDPConfigurationSource source) {
 *             return PolicyDecisionPointBuilder.withDefaults().build();
 *         }
 *
 *         &#64;Bean(destroyMethod = "dispose")
 *         public PDPConfigurationSource policySource(PdpRegister register) {
 *             return new DirectoryPDPConfigurationSource(Path.of("/policies"),
 *                     security -> register.loadConfiguration(security, true));
 *         }
 *     }
 * }
 * </pre>
 * <p>
 * Alternatively, use {@code @PreDestroy} to dispose the source:
 * </p>
 *
 * <pre>
 * {@code
 * &#64;Component
 * public class PdpLifecycle {
 *
 *     private final PDPComponents components;
 *
 *     public PdpLifecycle() {
 *         this.components = PolicyDecisionPointBuilder.withDefaults().withDirectorySource(Path.of("/policies"))
 *                 .build();
 *     }
 *
 *     @PreDestroy
 *     public void cleanup() {
 *         var source = components.source();
 *         if (source != null) {
 *             source.dispose();
 *         }
 *     }
 * }
 * }
 * </pre>
 *
 * @see PDPComponents
 */
public class PolicyDecisionPointBuilder {

    private final ObjectMapper mapper;
    private final Clock        clock;

    private boolean includeDefaultFunctionLibraries       = true;
    private boolean includeDefaultPolicyInformationPoints = true;

    private final List<Class<?>> staticFunctionLibraries       = new ArrayList<>();
    private final List<Object>   instantiatedFunctionLibraries = new ArrayList<>();
    private final List<Object>   policyInformationPoints       = new ArrayList<>();

    private AttributeStorage  attributeStorage;
    private IdFactory         idFactory;
    private WebClient.Builder webClientBuilder;

    private FunctionBroker  externalFunctionBroker;
    private AttributeBroker externalAttributeBroker;

    private final List<VoteInterceptor> interceptors = new ArrayList<>();

    private Function<Consumer<PDPConfiguration>, PDPConfigurationSource> sourceFactory;
    private final List<PDPConfiguration>                                 initialConfigurations = new ArrayList<>();

    private CombiningAlgorithm combiningAlgorithm;
    private final List<String> policyDocuments = new ArrayList<>();

    private PolicyDecisionPointBuilder(ObjectMapper mapper, Clock clock) {
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
        return new PolicyDecisionPointBuilder(new ObjectMapper(), Clock.systemUTC());
    }

    /**
     * Creates a builder with default function libraries and policy information
     * points enabled.
     *
     * @param mapper
     * the ObjectMapper for JSON processing
     * @param clock
     * the clock for time-based operations
     *
     * @return a new builder instance
     */
    public static PolicyDecisionPointBuilder withDefaults(ObjectMapper mapper, Clock clock) {
        return new PolicyDecisionPointBuilder(mapper, clock);
    }

    /**
     * Creates a builder without any default libraries or PIPs, using a new
     * ObjectMapper and system UTC clock.
     *
     * @return a new builder instance with defaults disabled
     */
    public static PolicyDecisionPointBuilder withoutDefaults() {
        return withoutDefaults(new ObjectMapper(), Clock.systemUTC());
    }

    /**
     * Creates a builder without any default libraries or PIPs. Use this for minimal
     * configurations or testing.
     *
     * @param mapper
     * the ObjectMapper for JSON processing
     * @param clock
     * the clock for time-based operations
     *
     * @return a new builder instance with defaults disabled
     */
    public static PolicyDecisionPointBuilder withoutDefaults(ObjectMapper mapper, Clock clock) {
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

    // === Decision Interceptor Methods ===

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

    // === Configuration Source Methods ===

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
    public PolicyDecisionPointBuilder withConfigurationSource(
            Function<Consumer<PDPConfiguration>, PDPConfigurationSource> sourceFactory) {
        if (this.sourceFactory != null) {
            throw new IllegalStateException(
                    "A configuration source has already been registered. Only one source is allowed.");
        }
        this.sourceFactory = sourceFactory;
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
        return withConfigurationSource(callback -> new DirectoryPDPConfigurationSource(directoryPath, callback));
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
        return withConfigurationSource(callback -> new DirectoryPDPConfigurationSource(directoryPath, pdpId, callback));
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
        return withConfigurationSource(callback -> new MultiDirectoryPDPConfigurationSource(directoryPath, callback));
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
        return withConfigurationSource(
                callback -> new MultiDirectoryPDPConfigurationSource(directoryPath, includeRootFiles, callback));
    }

    /**
     * Loads policies from bundle files (.saplbundle) in a directory. Each bundle
     * represents a separate PDP
     * configuration. Changes are monitored and hot-reloaded.
     * <p>
     * The security policy determines how bundle signatures are verified. Use
     * {@link BundleSecurityPolicy#requireSignature(java.security.PublicKey)} for
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
        return withConfigurationSource(
                callback -> new BundlePDPConfigurationSource(bundleDirectoryPath, securityPolicy, callback));
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
        return withConfigurationSource(callback -> new ResourcesPDPConfigurationSource(resourcePath, callback));
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
        return withConfigurationSource(ResourcesPDPConfigurationSource::new);
    }

    /**
     * Loads a configuration from a bundle byte array. This is useful for receiving
     * bundles via HTTP uploads or message
     * queues.
     * <p>
     * The security policy determines how bundle signatures are verified. Use
     * {@link BundleSecurityPolicy#requireSignature(java.security.PublicKey)} for
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
     * {@link BundleSecurityPolicy#requireSignature(java.security.PublicKey)} for
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
     * {@link BundleSecurityPolicy#requireSignature(java.security.PublicKey)} for
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
        val configurationRegister = new PdpRegister(functionBroker, attributeBroker);
        val timestampClock        = new LazyFastClock();
        val sortedInterceptors    = List.copyOf(interceptors);
        val pdp                   = new DynamicPolicyDecisionPoint(configurationRegister, resolveIdFactory(),
                context -> reactor.core.publisher.Mono.just(DynamicPolicyDecisionPoint.DEFAULT_PDP_ID),
                sortedInterceptors);

        // Create default configuration from collected policies
        if (!policyDocuments.isEmpty()) {
            val algorithm = combiningAlgorithm != null ? combiningAlgorithm : CombiningAlgorithm.DEFAULT;
            val config    = new PDPConfiguration("default", "config-" + System.currentTimeMillis(), algorithm,
                    List.copyOf(policyDocuments), new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT));
            initialConfigurations.add(config);
        }

        // Load initial configurations
        for (val config : initialConfigurations) {
            configurationRegister.loadConfiguration(config, false);
        }

        // Create configuration source with callback to register
        // Source calls the callback during construction with initial configs
        // and continues calling it when files change (for directory/bundle sources)
        PDPConfigurationSource source = null;
        if (sourceFactory != null) {
            Consumer<PDPConfiguration> callback = config -> configurationRegister.loadConfiguration(config, true);
            source = sourceFactory.apply(callback);
        }

        return new PDPComponents(pdp, configurationRegister, functionBroker, attributeBroker, source, timestampClock,
                sortedInterceptors);
    }

    private FunctionBroker resolveFunctionBroker() {
        return Objects.requireNonNullElseGet(externalFunctionBroker, this::buildFunctionBroker);
    }

    private FunctionBroker buildFunctionBroker() {
        val functionBroker = new DefaultFunctionBroker();

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
        return Objects.requireNonNullElseGet(externalAttributeBroker, this::buildAttributeBroker);
    }

    private AttributeBroker buildAttributeBroker() throws AttributeBrokerException {
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
     * Contains all components created by the PDP builder.
     * <p>
     * This is a simple data carrier (tuple) providing access to the built
     * components. Resources that require cleanup
     * (configuration source and timestamp clock) should be disposed when the PDP is
     * no longer needed.
     * </p>
     * <h2>Resource Management</h2>
     * <p>
     * Use the {@link #dispose()} method to clean up all disposable resources:
     * </p>
     *
     * <pre>{@code
     * var components = PolicyDecisionPointBuilder.withDefaults().withDirectorySource(Path.of("/policies")).build();
     *
     * // ... use the PDP ...
     *
     * // Cleanup all resources
     * components.dispose();
     * }</pre>
     */
    public record PDPComponents(
            PolicyDecisionPoint pdp,
            PdpRegister pdpRegister,
            FunctionBroker functionBroker,
            AttributeBroker attributeBroker,
            @Nullable PDPConfigurationSource source,
            LazyFastClock timestampClock,
            List<VoteInterceptor> sortedInterceptors) {

        /**
         * Disposes all resources held by this PDP instance.
         * <p>
         * This method should be called when the PDP is no longer needed to ensure clean
         * application shutdown. It closes
         * the timestamp clock's background thread and disposes any configuration source
         * file watchers.
         */
        public void dispose() {
            if (timestampClock != null) {
                timestampClock.close();
            }
            if (source != null) {
                source.dispose();
            }
        }
    }
}
