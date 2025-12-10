/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.test.next;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.Resource;
import io.sapl.api.attributes.AttributeBroker;
import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.model.Value;
import io.sapl.attributes.CachingAttributeBroker;
import io.sapl.attributes.HeapAttributeStorage;
import io.sapl.attributes.InMemoryAttributeRepository;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.api.pdp.PDPConfiguration;
import io.sapl.api.pdp.TraceLevel;
import io.sapl.functions.DefaultFunctionBroker;
import io.sapl.pdp.PolicyDecisionPointBuilder;
import io.sapl.pdp.configuration.PDPConfigurationLoader;
import io.sapl.pdp.configuration.bundle.BundleParser;
import io.sapl.pdp.configuration.bundle.BundleSecurityPolicy;
import io.sapl.test.next.MockingFunctionBroker.ArgumentMatcher;
import lombok.Getter;
import lombok.NonNull;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test fixture for SAPL policy unit and integration tests.
 * <p>
 * Provides a fluent API following Given-When-Then semantics:
 * <ul>
 * <li><b>Configuration (with...)</b>: Load policies, set algorithm, configure
 * brokers</li>
 * <li><b>Preconditions (given...)</b>: Mock functions, attributes, register
 * variables</li>
 * <li><b>Action (whenDecide)</b>: Submit authorization subscription for
 * evaluation</li>
 * </ul>
 * <p>
 * Two modes are available:
 * <ul>
 * <li><b>Single test</b>: Tests one policy document in isolation with
 * ONLY_ONE_APPLICABLE</li>
 * <li><b>Integration test</b>: Tests multiple policies with configurable
 * combining algorithm</li>
 * </ul>
 *
 * <h2>Single Test Example</h2>
 *
 * <pre>{@code
 * SaplTestFixture.createSingleTest().withPolicy("policy \"admin\" permit subject.role == \"admin\"")
 *         .givenFunction("time.dayOfWeek", args(), Value.of("MONDAY"))
 *         .whenDecide(AuthorizationSubscription.of("alice", "read", "doc"))
 * // ... assertions on the returned Flux
 * }</pre>
 *
 * <h2>Integration Test Example</h2>
 *
 * <pre>{@code
 * SaplTestFixture.createIntegrationTest().withConfigurationFromResources("policies/production")
 *         .givenEnvironmentAttribute("timeMock", "time.now", args(), Value.of("2025-01-06")).whenDecide(subscription)
 * // ... assertions
 * }</pre>
 */
public class SaplTestFixture {

    private final boolean singleTestMode;

    private final List<String>       policyDocuments = new ArrayList<>();
    private CombiningAlgorithm       combiningAlgorithm;
    private final Map<String, Value> variables       = new HashMap<>();
    private PDPConfiguration         loadedConfiguration;

    private FunctionBroker  customFunctionBroker;
    private AttributeBroker customAttributeBroker;
    private ObjectMapper    objectMapper;
    private Clock           clock;

    private final List<Class<?>> staticFunctionLibraries       = new ArrayList<>();
    private final List<Object>   instantiatedFunctionLibraries = new ArrayList<>();
    private final List<Object>   policyInformationPoints       = new ArrayList<>();

    @Getter
    private final MockingFunctionBroker mockingFunctionBroker = new MockingFunctionBroker();

    @Getter
    private final MockingAttributeBroker mockingAttributeBroker = new MockingAttributeBroker();

    private SaplTestFixture(boolean singleTestMode) {
        this.singleTestMode = singleTestMode;
    }

    /**
     * Creates a test fixture for single policy/policy set testing.
     * <p>
     * Single test mode:
     * <ul>
     * <li>Only one document (policy or policy set) allowed</li>
     * <li>Uses ONLY_ONE_APPLICABLE combining algorithm</li>
     * <li>Errors on second withPolicy* call</li>
     * <li>Errors on withCombiningAlgorithm call</li>
     * <li>Errors if no policy loaded at whenDecide</li>
     * </ul>
     *
     * @return a new fixture in single test mode
     */
    public static SaplTestFixture createSingleTest() {
        return new SaplTestFixture(true);
    }

    /**
     * Creates a test fixture for integration testing with multiple policies.
     * <p>
     * Integration test mode:
     * <ul>
     * <li>Multiple documents allowed</li>
     * <li>Combining algorithm required (from config file, bundle, or explicit)</li>
     * <li>No policy is valid (tests empty PDP behavior)</li>
     * <li>Errors if no combining algorithm defined at whenDecide</li>
     * </ul>
     *
     * @return a new fixture in integration test mode
     */
    public static SaplTestFixture createIntegrationTest() {
        return new SaplTestFixture(false);
    }

    /**
     * Adds an inline policy document.
     * <p>
     * In single test mode, only one document is allowed and subsequent calls will
     * throw.
     * The document is parsed immediately and errors are reported at call time.
     *
     * @param policyDocument the SAPL policy or policy set source code
     * @return this fixture for chaining
     * @throws IllegalStateException if in single test mode and a policy is already
     * loaded
     */
    public SaplTestFixture withPolicy(@NonNull String policyDocument) {
        validatePolicyAddition();
        policyDocuments.add(policyDocument);
        return this;
    }

    /**
     * Adds a policy document from a file path.
     *
     * @param filePath the path to the .sapl file
     * @return this fixture for chaining
     * @throws IllegalStateException if in single test mode and a policy is already
     * loaded
     * @throws RuntimeException if the file cannot be read
     */
    public SaplTestFixture withPolicyFromFile(@NonNull String filePath) {
        try {
            var content = Files.readString(Path.of(filePath));
            return withPolicy(content);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read policy file: " + filePath, e);
        }
    }

    /**
     * Adds a policy document from a classpath resource.
     *
     * @param resourcePath the classpath resource path
     * @return this fixture for chaining
     * @throws IllegalStateException if in single test mode and a policy is already
     * loaded
     * @throws RuntimeException if the resource cannot be read
     */
    public SaplTestFixture withPolicyFromResource(@NonNull String resourcePath) {
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new RuntimeException("Resource not found: " + resourcePath);
            }
            var content = new String(is.readAllBytes());
            return withPolicy(content);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read policy resource: " + resourcePath, e);
        }
    }

    /**
     * Loads a complete PDP configuration from a directory.
     * <p>
     * Loads all .sapl files and pdp.json (if present) from the directory.
     * The combining algorithm and variables are taken from pdp.json.
     *
     * @param directoryPath the path to the directory containing policies and
     * pdp.json
     * @return this fixture for chaining
     * @throws IllegalStateException if in single test mode
     */
    public SaplTestFixture withConfigurationFromDirectory(@NonNull String directoryPath) {
        if (singleTestMode) {
            throw new IllegalStateException("withConfigurationFromDirectory not allowed in single test mode.");
        }
        loadedConfiguration = loadConfigurationFromDirectory(Path.of(directoryPath));
        return this;
    }

    /**
     * Loads a complete PDP configuration from classpath resources.
     * <p>
     * Loads all .sapl files and pdp.json (if present) from the resource path.
     * The combining algorithm and variables are taken from pdp.json.
     *
     * @param resourcePath the classpath resource path
     * @return this fixture for chaining
     * @throws IllegalStateException if in single test mode
     */
    public SaplTestFixture withConfigurationFromResources(@NonNull String resourcePath) {
        if (singleTestMode) {
            throw new IllegalStateException("withConfigurationFromResources not allowed in single test mode.");
        }
        loadedConfiguration = loadConfigurationFromResources(resourcePath);
        return this;
    }

    /**
     * Loads PDP configuration (combining algorithm, variables) from a pdp.json
     * file.
     * <p>
     * This only loads the configuration metadata, not policies. Use withPolicy*
     * methods
     * to load policies separately.
     *
     * @param filePath the path to pdp.json
     * @return this fixture for chaining
     * @throws IllegalStateException if in single test mode
     */
    public SaplTestFixture withConfigFile(@NonNull String filePath) {
        if (singleTestMode) {
            throw new IllegalStateException("withConfigFile not allowed in single test mode.");
        }
        loadedConfiguration = loadPdpJsonFromFile(Path.of(filePath));
        return this;
    }

    /**
     * Loads PDP configuration from a pdp.json classpath resource.
     *
     * @param resourcePath the classpath resource path to pdp.json
     * @return this fixture for chaining
     * @throws IllegalStateException if in single test mode
     */
    public SaplTestFixture withConfigFileFromResource(@NonNull String resourcePath) {
        if (singleTestMode) {
            throw new IllegalStateException("withConfigFileFromResource not allowed in single test mode.");
        }
        loadedConfiguration = loadPdpJsonFromResource(resourcePath);
        return this;
    }

    /**
     * Loads policies from a bundle file without signature verification.
     * <p>
     * Use this for test bundles that are not signed.
     *
     * @param filePath the path to the .saplbundle file
     * @return this fixture for chaining
     * @throws IllegalStateException if in single test mode
     */
    public SaplTestFixture withBundle(@NonNull String filePath) {
        if (singleTestMode) {
            throw new IllegalStateException("withBundle not allowed in single test mode.");
        }
        loadedConfiguration = BundleParser.parse(Path.of(filePath), "test", noSignatureVerification());
        return this;
    }

    /**
     * Loads policies from a bundle classpath resource without signature
     * verification.
     *
     * @param resourcePath the classpath resource path to the .saplbundle file
     * @return this fixture for chaining
     * @throws IllegalStateException if in single test mode
     */
    public SaplTestFixture withBundleFromResource(@NonNull String resourcePath) {
        if (singleTestMode) {
            throw new IllegalStateException("withBundleFromResource not allowed in single test mode.");
        }
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new RuntimeException("Bundle resource not found: " + resourcePath);
            }
            loadedConfiguration = BundleParser.parse(is, "test", noSignatureVerification());
        } catch (IOException e) {
            throw new RuntimeException("Failed to read bundle resource: " + resourcePath, e);
        }
        return this;
    }

    /**
     * Loads policies from a bundle file with signature verification.
     * <p>
     * Use this for production bundles that require signature verification.
     *
     * @param filePath the path to the .saplbundle file
     * @param securityPolicy the security policy for signature verification
     * @return this fixture for chaining
     * @throws IllegalStateException if in single test mode
     */
    public SaplTestFixture withVerifiedBundle(@NonNull String filePath, @NonNull BundleSecurityPolicy securityPolicy) {
        if (singleTestMode) {
            throw new IllegalStateException("withVerifiedBundle not allowed in single test mode.");
        }
        loadedConfiguration = BundleParser.parse(Path.of(filePath), "test", securityPolicy);
        return this;
    }

    /**
     * Loads policies from a bundle classpath resource with signature verification.
     *
     * @param resourcePath the classpath resource path to the .saplbundle file
     * @param securityPolicy the security policy for signature verification
     * @return this fixture for chaining
     * @throws IllegalStateException if in single test mode
     */
    public SaplTestFixture withVerifiedBundleFromResource(@NonNull String resourcePath,
            @NonNull BundleSecurityPolicy securityPolicy) {
        if (singleTestMode) {
            throw new IllegalStateException("withVerifiedBundleFromResource not allowed in single test mode.");
        }
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new RuntimeException("Bundle resource not found: " + resourcePath);
            }
            loadedConfiguration = BundleParser.parse(is, "test", securityPolicy);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read bundle resource: " + resourcePath, e);
        }
        return this;
    }

    /**
     * Sets the combining algorithm for policy evaluation.
     * <p>
     * Not allowed in single test mode (always uses ONLY_ONE_APPLICABLE).
     *
     * @param algorithm the combining algorithm
     * @return this fixture for chaining
     * @throws IllegalStateException if in single test mode
     */
    public SaplTestFixture withCombiningAlgorithm(@NonNull CombiningAlgorithm algorithm) {
        if (singleTestMode) {
            throw new IllegalStateException("withCombiningAlgorithm not allowed in single test mode.");
        }
        this.combiningAlgorithm = algorithm;
        return this;
    }

    /**
     * Sets a custom function broker.
     * <p>
     * The mocking broker will wrap this broker, so unmocked functions delegate to
     * it.
     *
     * @param functionBroker the function broker to use
     * @return this fixture for chaining
     */
    public SaplTestFixture withFunctionBroker(@NonNull FunctionBroker functionBroker) {
        this.customFunctionBroker = functionBroker;
        return this;
    }

    /**
     * Sets a custom attribute broker.
     * <p>
     * The mocking broker will wrap this broker, so unmocked attributes delegate to
     * it.
     *
     * @param attributeBroker the attribute broker to use
     * @return this fixture for chaining
     */
    public SaplTestFixture withAttributeBroker(@NonNull AttributeBroker attributeBroker) {
        this.customAttributeBroker = attributeBroker;
        return this;
    }

    /**
     * Sets a custom ObjectMapper for JSON processing.
     * <p>
     * Use this when the policies under test require specific ObjectMapper
     * configuration, such as custom serializers or date formats.
     *
     * @param objectMapper the ObjectMapper to use
     * @return this fixture for chaining
     */
    public SaplTestFixture withObjectMapper(@NonNull ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        return this;
    }

    /**
     * Sets a custom Clock for time-based operations.
     * <p>
     * Use this to control the time seen by policies during testing. A fixed clock
     * is particularly useful for testing time-dependent policies deterministically.
     *
     * @param clock the Clock to use
     * @return this fixture for chaining
     */
    public SaplTestFixture withClock(@NonNull Clock clock) {
        this.clock = clock;
        return this;
    }

    /**
     * Registers a static function library class.
     * <p>
     * The library class must have a no-arg constructor and methods annotated with
     * {@code @Function}.
     *
     * @param libraryClass the function library class
     * @return this fixture for chaining
     */
    public SaplTestFixture withFunctionLibrary(@NonNull Class<?> libraryClass) {
        staticFunctionLibraries.add(libraryClass);
        return this;
    }

    /**
     * Registers an instantiated function library.
     * <p>
     * Use this when the library requires constructor arguments or specific
     * configuration.
     *
     * @param libraryInstance the function library instance
     * @return this fixture for chaining
     */
    public SaplTestFixture withFunctionLibraryInstance(@NonNull Object libraryInstance) {
        instantiatedFunctionLibraries.add(libraryInstance);
        return this;
    }

    /**
     * Registers a policy information point (PIP).
     * <p>
     * The PIP must have methods annotated with {@code @Attribute} or
     * {@code @EnvironmentAttribute}.
     *
     * @param pip the policy information point instance
     * @return this fixture for chaining
     */
    public SaplTestFixture withPolicyInformationPoint(@NonNull Object pip) {
        policyInformationPoints.add(pip);
        return this;
    }

    /**
     * Mocks a function to return the specified values.
     * <p>
     * If multiple values are provided, they are returned in sequence with the last
     * value repeated indefinitely.
     *
     * @param functionName fully qualified name (e.g., "time.dayOfWeek")
     * @param arguments argument matchers created via {@code args()}
     * @param returnValues one or more values to return
     * @return this fixture for chaining
     */
    public SaplTestFixture givenFunction(@NonNull String functionName, @NonNull Parameters arguments,
            @NonNull Value... returnValues) {
        mockingFunctionBroker.mock(functionName, arguments, returnValues);
        return this;
    }

    /**
     * Mocks an environment attribute with an initial value.
     *
     * @param mockId unique identifier for this mock (used for emit)
     * @param attributeName fully qualified name (e.g., "time.now")
     * @param arguments argument matchers created via {@code args()}
     * @param initialValue value to emit immediately
     * @return this fixture for chaining
     */
    public SaplTestFixture givenEnvironmentAttribute(@NonNull String mockId, @NonNull String attributeName,
            @NonNull Parameters arguments, @NonNull Value initialValue) {
        mockingAttributeBroker.mockEnvironmentAttribute(mockId, attributeName, arguments, initialValue);
        return this;
    }

    /**
     * Mocks an environment attribute without an initial value.
     * <p>
     * The attribute stream will only emit when {@code emit(mockId, value)} is
     * called
     * after {@code whenDecide()}.
     *
     * @param mockId unique identifier for this mock (used for emit)
     * @param attributeName fully qualified name (e.g., "time.now")
     * @param arguments argument matchers created via {@code args()}
     * @return this fixture for chaining
     */
    public SaplTestFixture givenEnvironmentAttribute(@NonNull String mockId, @NonNull String attributeName,
            @NonNull Parameters arguments) {
        mockingAttributeBroker.mockEnvironmentAttribute(mockId, attributeName, arguments);
        return this;
    }

    /**
     * Mocks a regular (entity-based) attribute with an initial value.
     *
     * @param mockId unique identifier for this mock (used for emit)
     * @param attributeName fully qualified name
     * @param entityMatcher matcher for the entity value
     * @param arguments argument matchers created via {@code args()}
     * @param initialValue value to emit immediately
     * @return this fixture for chaining
     */
    public SaplTestFixture givenAttribute(@NonNull String mockId, @NonNull String attributeName,
            @NonNull ArgumentMatcher entityMatcher, @NonNull Parameters arguments, @NonNull Value initialValue) {
        mockingAttributeBroker.mockAttribute(mockId, attributeName, entityMatcher, arguments, initialValue);
        return this;
    }

    /**
     * Mocks a regular (entity-based) attribute without an initial value.
     *
     * @param mockId unique identifier for this mock (used for emit)
     * @param attributeName fully qualified name
     * @param entityMatcher matcher for the entity value
     * @param arguments argument matchers created via {@code args()}
     * @return this fixture for chaining
     */
    public SaplTestFixture givenAttribute(@NonNull String mockId, @NonNull String attributeName,
            @NonNull ArgumentMatcher entityMatcher, @NonNull Parameters arguments) {
        mockingAttributeBroker.mockAttribute(mockId, attributeName, entityMatcher, arguments);
        return this;
    }

    /**
     * Registers a variable in the evaluation context.
     * <p>
     * Throws if a variable with the same name is already registered.
     *
     * @param name the variable name
     * @param value the variable value
     * @return this fixture for chaining
     * @throws IllegalArgumentException if a variable with this name already exists
     */
    public SaplTestFixture givenVariable(@NonNull String name, @NonNull Value value) {
        if (variables.containsKey(name)) {
            throw new IllegalArgumentException("Variable '%s' is already registered.".formatted(name));
        }
        variables.put(name, value);
        return this;
    }

    /**
     * Submits an authorization subscription for evaluation and returns the decision
     * result.
     * <p>
     * This assembles the PDP with all configured policies, brokers, and mocks, then
     * evaluates the subscription.
     *
     * @param subscription the authorization subscription to evaluate
     * @return a DecisionResult for asserting on decisions and emitting to attribute
     * streams
     * @throws IllegalStateException if configuration is invalid (e.g., no policy in
     * single mode,
     * no algorithm in integration mode)
     */
    public DecisionResult whenDecide(@NonNull AuthorizationSubscription subscription) {
        validateBeforeDecide();

        var effectiveMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        var effectiveClock  = clock != null ? clock : Clock.systemUTC();
        var pdpBuilder      = PolicyDecisionPointBuilder.withoutDefaults(effectiveMapper, effectiveClock);

        // Build function broker delegate with registered libraries
        var functionBrokerDelegate = customFunctionBroker;
        if (functionBrokerDelegate == null
                && (!staticFunctionLibraries.isEmpty() || !instantiatedFunctionLibraries.isEmpty())) {
            var defaultBroker = new DefaultFunctionBroker();
            for (var libraryClass : staticFunctionLibraries) {
                defaultBroker.loadStaticFunctionLibrary(libraryClass);
            }
            for (var libraryInstance : instantiatedFunctionLibraries) {
                defaultBroker.loadInstantiatedFunctionLibrary(libraryInstance);
            }
            functionBrokerDelegate = defaultBroker;
        }
        if (functionBrokerDelegate != null) {
            mockingFunctionBroker.setDelegate(functionBrokerDelegate);
        }

        // Set up attribute broker with mocking wrapper
        if (customAttributeBroker != null) {
            mockingAttributeBroker.setDelegate(customAttributeBroker);
        } else if (!policyInformationPoints.isEmpty()) {
            // Build an attribute broker from registered PIPs to use as delegate
            var effectivePipClock   = clock != null ? clock : Clock.systemUTC();
            var storage             = new HeapAttributeStorage();
            var attributeRepository = new InMemoryAttributeRepository(effectivePipClock, storage);
            var attributeBroker     = new CachingAttributeBroker(attributeRepository);
            for (var pip : policyInformationPoints) {
                attributeBroker.loadPolicyInformationPointLibrary(pip);
            }
            mockingAttributeBroker.setDelegate(attributeBroker);
        }

        // Register PIPs on the builder (kept for potential direct access, though
        // delegate handles them now)
        pdpBuilder.withPolicyInformationPoints(policyInformationPoints);

        // Build the configuration
        var effectiveAlgorithm = resolveAlgorithm();
        var effectiveVariables = resolveVariables();
        var effectivePolicies  = resolvePolicies();

        var configuration = new PDPConfiguration("default", "test-config-" + System.currentTimeMillis(),
                effectiveAlgorithm, TraceLevel.COVERAGE, effectivePolicies, effectiveVariables);

        // Build PDP with mocking brokers wrapping the real ones
        var components = pdpBuilder.withFunctionBroker(mockingFunctionBroker)
                .withAttributeBroker(mockingAttributeBroker).withConfiguration(configuration).build();

        // Subscribe and get the decision flux
        var decisionFlux = components.pdp().decide(subscription);

        return new DecisionResult(decisionFlux, mockingAttributeBroker, components);
    }

    private void validatePolicyAddition() {
        if (singleTestMode && !policyDocuments.isEmpty()) {
            throw new IllegalStateException(
                    "Single test mode only allows one policy document. Use createIntegrationTest() for multiple policies.");
        }
        if (loadedConfiguration != null) {
            throw new IllegalStateException(
                    "Cannot add policy when configuration is already loaded from bundle/directory.");
        }
    }

    private void validateBeforeDecide() {
        if (singleTestMode) {
            if (policyDocuments.isEmpty() && loadedConfiguration == null) {
                throw new IllegalStateException("Single test mode requires exactly one policy document.");
            }
        } else {
            // Integration test mode
            if (combiningAlgorithm == null && loadedConfiguration == null) {
                throw new IllegalStateException(
                        "Integration test mode requires a combining algorithm. Use withCombiningAlgorithm(), withConfigFile(), or load from bundle/directory.");
            }
        }
    }

    private CombiningAlgorithm resolveAlgorithm() {
        if (singleTestMode) {
            return CombiningAlgorithm.ONLY_ONE_APPLICABLE;
        }
        if (combiningAlgorithm != null) {
            return combiningAlgorithm;
        }
        if (loadedConfiguration != null && loadedConfiguration.combiningAlgorithm() != null) {
            return loadedConfiguration.combiningAlgorithm();
        }
        throw new IllegalStateException(
                "No combining algorithm specified. Use withCombiningAlgorithm() or load configuration with a combining algorithm.");
    }

    private Map<String, Value> resolveVariables() {
        var result = new HashMap<String, Value>();
        if (loadedConfiguration != null && loadedConfiguration.variables() != null) {
            result.putAll(loadedConfiguration.variables());
        }
        result.putAll(variables);
        return result;
    }

    private List<String> resolvePolicies() {
        if (loadedConfiguration != null && loadedConfiguration.saplDocuments() != null) {
            var result = new ArrayList<>(loadedConfiguration.saplDocuments());
            result.addAll(policyDocuments);
            return result;
        }
        return policyDocuments;
    }

    private PDPConfiguration loadConfigurationFromDirectory(Path directoryPath) {
        return PDPConfigurationLoader.loadFromDirectory(directoryPath, "test");
    }

    private PDPConfiguration loadPdpJsonFromFile(Path filePath) {
        try {
            var content = Files.readString(filePath);
            return PDPConfigurationLoader.loadFromContent(content, Map.of(), "test", filePath.toString());
        } catch (IOException e) {
            throw new RuntimeException("Failed to read pdp.json from: " + filePath, e);
        }
    }

    private PDPConfiguration loadPdpJsonFromResource(String resourcePath) {
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new RuntimeException("Resource not found: " + resourcePath);
            }
            var content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return PDPConfigurationLoader.loadFromContent(content, Map.of(), "test", resourcePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read pdp.json resource: " + resourcePath, e);
        }
    }

    private PDPConfiguration loadConfigurationFromResources(String resourcePath) {
        var    normalizedPath = normalizeResourcePath(resourcePath);
        var    saplFiles      = new HashMap<String, String>();
        String pdpJson        = null;

        try (var scanResult = new ClassGraph().acceptPaths(normalizedPath).scan()) {
            for (var resource : scanResult.getAllResources()) {
                var relativePath = getRelativePath(resource.getPath(), normalizedPath);
                // Only process root-level files, skip subdirectories
                if (!relativePath.contains("/")) {
                    if ("pdp.json".equals(relativePath)) {
                        pdpJson = readResource(resource);
                    } else if (relativePath.endsWith(".sapl")) {
                        saplFiles.put(relativePath, readResource(resource));
                    }
                }
            }
        }

        return PDPConfigurationLoader.loadFromContent(pdpJson, saplFiles, "test", "/" + normalizedPath);
    }

    private String normalizeResourcePath(String path) {
        var normalized = path;
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String getRelativePath(String fullPath, String basePath) {
        if (fullPath.startsWith(basePath + "/")) {
            return fullPath.substring(basePath.length() + 1);
        }
        if (fullPath.startsWith(basePath)) {
            return fullPath.substring(basePath.length());
        }
        return fullPath;
    }

    private String readResource(Resource resource) {
        try {
            return new String(resource.load(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read resource: " + resource.getPath(), e);
        }
    }

    private static BundleSecurityPolicy noSignatureVerification() {
        return BundleSecurityPolicy.builder().disableSignatureVerification().acceptUnsignedBundleRisks().build();
    }

    /**
     * Marker interface for argument matchers.
     * <p>
     * Create instances using
     * {@link Matchers#args(MockingFunctionBroker.ArgumentMatcher...)}.
     */
    public interface Parameters {
        // Marker interface
    }

    /**
     * Result of a policy decision providing step-by-step verification using
     * StepVerifier.
     * <p>
     * Each expect method adds a verification step, and terminal operations execute
     * them:
     *
     * <pre>{@code
     * fixture.whenDecide(subscription).expectPermit().thenEmit("timeMock", newValue).expectDeny().verify();
     * }</pre>
     */
    public static class DecisionResult {

        private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

        private StepVerifier.Step<AuthorizationDecision>       step;
        private final MockingAttributeBroker                   attributeBroker;
        private final PolicyDecisionPointBuilder.PDPComponents components;

        DecisionResult(Flux<AuthorizationDecision> decisionFlux,
                MockingAttributeBroker attributeBroker,
                PolicyDecisionPointBuilder.PDPComponents components) {
            this.step            = StepVerifier.create(decisionFlux);
            this.attributeBroker = attributeBroker;
            this.components      = components;
        }

        /**
         * Expects the next decision to match the given decision exactly.
         *
         * @param expected the expected authorization decision
         * @return this result for chaining
         */
        public DecisionResult expectDecision(@NonNull AuthorizationDecision expected) {
            step = step.expectNextMatches(expected::equals);
            return this;
        }

        /**
         * Expects the next decision to match the given matcher.
         *
         * @param matcher the decision matcher
         * @return this result for chaining
         */
        public DecisionResult expectDecisionMatches(@NonNull DecisionMatcher matcher) {
            step = step.expectNextMatches(matcher);
            return this;
        }

        /**
         * Expects the next decision to be PERMIT.
         *
         * @return this result for chaining
         */
        public DecisionResult expectPermit() {
            return expectDecisionMatches(DecisionMatchers.isPermit());
        }

        /**
         * Expects the next decision to be DENY.
         *
         * @return this result for chaining
         */
        public DecisionResult expectDeny() {
            return expectDecisionMatches(DecisionMatchers.isDeny());
        }

        /**
         * Expects the next decision to be INDETERMINATE.
         *
         * @return this result for chaining
         */
        public DecisionResult expectIndeterminate() {
            return expectDecisionMatches(DecisionMatchers.isIndeterminate());
        }

        /**
         * Expects the next decision to be NOT_APPLICABLE.
         *
         * @return this result for chaining
         */
        public DecisionResult expectNotApplicable() {
            return expectDecisionMatches(DecisionMatchers.isNotApplicable());
        }

        /**
         * Emits a value to a mocked attribute stream.
         *
         * @param mockId the mock identifier from
         * givenAttribute/givenEnvironmentAttribute
         * @param value the value to emit
         * @return this result for chaining
         */
        public DecisionResult thenEmit(@NonNull String mockId, @NonNull Value value) {
            step = step.then(() -> attributeBroker.emit(mockId, value));
            return this;
        }

        /**
         * Waits for the specified duration before continuing.
         *
         * @param duration the duration to wait
         * @return this result for chaining
         */
        public DecisionResult thenAwait(@NonNull Duration duration) {
            step = step.thenAwait(duration);
            return this;
        }

        /**
         * Executes verification, cancels the subscription, and disposes resources.
         *
         * @throws AssertionError if any expectation is not met
         */
        public void verify() {
            verify(DEFAULT_TIMEOUT);
        }

        /**
         * Executes verification with timeout, cancels the subscription, and disposes
         * resources.
         *
         * @param timeout maximum time to wait for decisions
         * @throws AssertionError if any expectation is not met
         */
        public void verify(@NonNull Duration timeout) {
            try {
                step.thenCancel().verify(timeout);
            } finally {
                dispose();
            }
        }

        private void dispose() {
            if (components != null) {
                components.dispose();
            }
        }
    }
}
