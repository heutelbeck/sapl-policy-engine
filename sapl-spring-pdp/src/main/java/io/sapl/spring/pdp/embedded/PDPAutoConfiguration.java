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
package io.sapl.spring.pdp.embedded;

import static io.sapl.functions.libraries.crypto.CryptoConstants.ALGORITHM_ED25519;
import static io.sapl.pdp.PolicyDecisionPointBuilder.buildPolicyInformationPointAttributeBroker;
import static io.sapl.pdp.PolicyDecisionPointBuilder.buildFunctionBroker;

import io.sapl.api.attributes.PolicyInformationPoint;
import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.functions.FunctionLibraryProvider;
import io.sapl.api.pdp.DecisionInterceptor;
import io.sapl.api.pdp.SubscriptionLifecycleListener;
import io.sapl.attributes.broker.AttributeRepository;
import io.sapl.attributes.broker.AttributeBroker;
import io.sapl.attributes.broker.pip.PolicyInformationPointAttributeBroker;
import io.sapl.attributes.broker.repository.InMemoryAttributeRepository;
import io.sapl.functions.libraries.crypto.PemUtils;
import io.sapl.pdp.BlockingPolicyDecisionPoint;
import io.sapl.pdp.IdFactory;
import io.sapl.pdp.CoarseClock;
import io.sapl.pdp.ThreadLocalRandomIdFactory;
import io.sapl.pdp.configuration.PdpVoterSource;
import io.sapl.pdp.configuration.bundle.BundleSecurityPolicy;
import io.sapl.pdp.configuration.source.BundlePDPConfigurationSource;
import io.sapl.pdp.configuration.source.DirectoryPDPConfigurationSource;
import io.sapl.pdp.configuration.source.MultiDirectoryPDPConfigurationSource;
import io.sapl.pdp.configuration.source.PDPConfigurationSource;
import io.sapl.pdp.configuration.source.PdpIdValidator;
import io.sapl.pdp.configuration.source.RemoteBundlePDPConfigurationSource;
import io.sapl.pdp.configuration.source.RemoteBundleSourceConfig;
import io.sapl.pdp.configuration.source.ResourcesPDPConfigurationSource;
import io.sapl.pdp.plugins.HotReloadingPluginsSource;
import io.sapl.pdp.plugins.PluginsBundle;
import io.sapl.pdp.plugins.PluginsSource;
import io.sapl.pdp.plugins.SaplPluginManager;
import io.sapl.reactive.api.pdp.ReactivePolicyDecisionPoint;
import io.sapl.reactive.pdp.DelegatingReactivePolicyDecisionPoint;
import io.sapl.spring.pdp.embedded.EmbeddedPDPProperties.BundleSecurityProperties;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Role;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Auto-configuration for the embedded Policy Decision Point.
 * <p>
 * Each top-level collaborator of the PDP is exposed as its own Spring
 * bean so the container manages each lifecycle individually:
 * {@link FunctionBroker}, {@link AttributeBroker},
 * {@link PDPConfigurationSource}, {@link PdpVoterSource},
 * {@link IdFactory}, and the {@link ReactivePolicyDecisionPoint} itself. Beans
 * that hold real resources implement {@link AutoCloseable} (the voter
 * source and the configuration source); Spring invokes their
 * {@code close()} method on context shutdown.
 * <p>
 * Every bean is declared {@link ConditionalOnMissingBean} so an
 * application can replace any single piece (for example a custom
 * {@link FunctionBroker} pre-loaded with proprietary libraries)
 * without having to opt out of the rest of the auto-configuration. To
 * disable the embedded PDP entirely, set
 * {@code io.sapl.pdp.embedded.enabled=false}.
 * <p>
 * The auto-configuration discovers and registers:
 * <ul>
 * <li>Custom function libraries (beans annotated with
 * {@link FunctionLibrary})</li>
 * <li>Custom policy information points (beans annotated with
 * {@link PolicyInformationPoint})</li>
 * <li>Decision interceptors (beans implementing
 * {@link DecisionInterceptor})</li>
 * <li>Subscription lifecycle listeners (beans implementing
 * {@link SubscriptionLifecycleListener})</li>
 * <li>Multi-tenant routing via the {@code pdpId}-form methods on
 * {@link ReactivePolicyDecisionPoint}</li>
 * </ul>
 * <p>
 * Configuration is controlled via {@link EmbeddedPDPProperties} with prefix
 * {@code io.sapl.pdp.embedded}.
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(EmbeddedPDPProperties.class)
@ConditionalOnClass(name = "io.sapl.pdp.PolicyDecisionPointBuilder")
@ConditionalOnProperty(prefix = "io.sapl.pdp.embedded", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PDPAutoConfiguration {

    private static final String ERROR_FAILED_TO_PARSE_KEY_IN_CATALOGUE = "Failed to parse public key '%s' in key catalogue";
    private static final String ERROR_FAILED_TO_PARSE_PUBLIC_KEY       = "Failed to parse Ed25519 public key";
    private static final String ERROR_FAILED_TO_READ_PUBLIC_KEY        = "Failed to read public key from: ";

    @Bean
    @ConditionalOnMissingBean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Source for observability timestamps (decision trace, attribute value
     * freshness). Defaults to the temporal {@code clock}. When
     * {@code io.sapl.pdp.embedded.coarse-timestamps=true}, a coarse-resolution
     * cached clock is used instead, which is cheaper per decision at high
     * throughput at the cost of coarser timestamp precision. The coarse clock is
     * owned by the application context and closed on shutdown.
     */
    @Bean
    @ConditionalOnMissingBean(name = "timestampSource")
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    InstantSource timestampSource(EmbeddedPDPProperties properties, Clock clock) {
        if (properties.isCoarseTimestamps()) {
            log.debug("Using coarse-resolution clock for observability timestamps.");
            return new CoarseClock();
        }
        return clock;
    }

    @Bean
    @ConditionalOnMissingBean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    IdFactory idFactory() {
        return new ThreadLocalRandomIdFactory();
    }

    @Bean
    @ConditionalOnMissingBean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    FunctionBroker functionBroker(EmbeddedPDPProperties properties,
            ObjectProvider<FunctionLibraryProvider> functionLibraryProviders, ApplicationContext applicationContext) {
        val libraries = new ArrayList<Object>();
        libraries.addAll(collectFunctionLibrariesFromProviders(functionLibraryProviders));
        libraries.addAll(collectFunctionLibraries(applicationContext));
        log.debug("Building FunctionBroker: {} function libraries.", libraries.size());
        return buildFunctionBroker(properties.getFunctionCacheSize(), true, libraries);
    }

    @Bean
    @ConditionalOnMissingBean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    PDPConfigurationSource pdpConfigurationSource(EmbeddedPDPProperties properties) {
        val rawPath      = PdpIdValidator.resolveHomeFolderIfPresent(properties.getPoliciesPath());
        val resolvedPath = rawPath.toAbsolutePath().normalize();

        return switch (properties.getPdpConfigType()) {
        case DIRECTORY       -> {
            log.info("Loading policies from directory: {}", resolvedPath);
            yield new DirectoryPDPConfigurationSource(rawPath);
        }
        case MULTI_DIRECTORY -> {
            log.info("Loading policies from multi-directory: {}", resolvedPath);
            yield new MultiDirectoryPDPConfigurationSource(rawPath);
        }
        case BUNDLES         -> {
            log.info("Loading policies from bundles: {}", resolvedPath);
            val securityPolicy = createBundleSecurityPolicy(properties.getBundleSecurity(), resolvedPath);
            yield new BundlePDPConfigurationSource(rawPath, securityPolicy);
        }
        case REMOTE_BUNDLES  -> {
            val props          = properties.getRemoteBundles();
            val securityPolicy = createBundleSecurityPolicy(properties.getBundleSecurity(), resolvedPath);
            log.info("Loading policies from remote bundles: {}", props.getBaseUrl());
            val sourceConfig = new RemoteBundleSourceConfig(props.getBaseUrl(), props.getPdpIds(),
                    RemoteBundleSourceConfig.FetchMode.valueOf(props.getMode().name()), props.getPollInterval(),
                    props.getLongPollTimeout(), props.getAuthHeaderName(), props.getAuthHeaderValue(),
                    props.isFollowRedirects(), securityPolicy, props.getPdpIdPollIntervals(), props.getFirstBackoff(),
                    props.getMaxBackoff());
            yield new RemoteBundlePDPConfigurationSource(sourceConfig);
        }
        case RESOURCES       -> {
            val resourcePath = properties.getPoliciesPath();
            log.info("Loading policies from resources: {}", resourcePath);
            yield new ResourcesPDPConfigurationSource(resourcePath);
        }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    SaplPluginManager saplPluginManager(EmbeddedPDPProperties properties) {
        var pluginsPath = PdpIdValidator.resolveHomeFolderIfPresent(properties.getPluginsPath());
        return new SaplPluginManager(pluginsPath);
    }

    @Bean
    @ConditionalOnMissingBean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    PluginsSource pluginsSource(SaplPluginManager pluginManager, PolicyInformationPointAttributeBroker attributeBroker,
            EmbeddedPDPProperties properties, ObjectProvider<FunctionLibraryProvider> functionLibraryProviders,
            ApplicationContext applicationContext, ObjectProvider<DecisionInterceptor> decisionInterceptorProvider,
            ObjectProvider<SubscriptionLifecycleListener> lifecycleListenerProvider) {
        val libraries = new ArrayList<Object>();
        libraries.addAll(collectFunctionLibrariesFromProviders(functionLibraryProviders));
        libraries.addAll(collectFunctionLibraries(applicationContext));

        val decisionInterceptors = decisionInterceptorProvider.orderedStream().toList();
        val lifecycleListeners   = lifecycleListenerProvider.orderedStream().toList();
        if (!decisionInterceptors.isEmpty() || !lifecycleListeners.isEmpty()) {
            log.debug("Registering {} decision interceptors and {} lifecycle listeners.", decisionInterceptors.size(),
                    lifecycleListeners.size());
        }
        return new HotReloadingPluginsSource(pluginManager, libraries, attributeBroker,
                properties.getFunctionCacheSize(), true,
                decisionInterceptors, lifecycleListeners);
    }

    @Bean
    @ConditionalOnMissingBean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    PdpVoterSource pdpVoterSource(PluginsSource pluginsSource, Clock clock,
            ObjectProvider<PDPConfigurationSource> sourceProvider) {
        val voterSource = new PdpVoterSource(pluginsSource, clock);
        val source      = sourceProvider.getIfAvailable();
        if (source != null) {
            source.subscribe(voterSource::handle);
        }
        return voterSource;
    }

    @Bean
    @ConditionalOnMissingBean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    BlockingPolicyDecisionPoint blockingPolicyDecisionPoint(PdpVoterSource pdpVoterSource,
            AttributeBroker attributeBroker, IdFactory idFactory, InstantSource timestampSource) {
        log.debug("Deploying embedded Policy Decision Point.");
        return new BlockingPolicyDecisionPoint(pdpVoterSource, attributeBroker, idFactory, timestampSource);
    }

    /**
     * Reactive adapter, only registered when {@code sapl-pdp-reactive} is on
     * the classpath. Allows consumers to exclude the reactive module without
     * breaking {@link PDPAutoConfiguration}'s class loading. The class
     * condition is evaluated by name, so the inner class (and its imports of
     * the reactive types) is not loaded when the reactive module is absent.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.sapl.reactive.api.pdp.ReactivePolicyDecisionPoint")
    static class ReactiveAdapter {

        @Bean
        @ConditionalOnMissingBean
        @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
        ReactivePolicyDecisionPoint reactivePolicyDecisionPoint(BlockingPolicyDecisionPoint blockingPdp) {
            return new DelegatingReactivePolicyDecisionPoint(blockingPdp);
        }
    }

    @Bean
    @ConditionalOnMissingBean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    PolicyInformationPointAttributeBroker policyInformationPointAttributeBroker(JsonMapper mapper, Clock clock,
            InstantSource timestampSource, ApplicationContext applicationContext,
            InMemoryAttributeRepository inMemoryAttributeRepository) {
        val pips = collectPolicyInformationPoints(applicationContext);
        log.debug("Building catalog AttributeBroker: SAPL default PIPs plus {} custom PIP instances.", pips.size());
        return buildPolicyInformationPointAttributeBroker(clock, timestampSource, mapper, true, pips,
                inMemoryAttributeRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    InMemoryAttributeRepository inMemoryAttributeRepository() {
        return new InMemoryAttributeRepository();
    }

    @Bean
    @ConditionalOnMissingBean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    AttributeRepository attributeRepository(InMemoryAttributeRepository inMemoryAttributeRepository) {
        return inMemoryAttributeRepository;
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean(name = "attributeBroker")
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    AttributeBroker attributeBroker(PolicyInformationPointAttributeBroker policyInformationPointAttributeBroker) {
        return policyInformationPointAttributeBroker;
    }

    private BundleSecurityPolicy createBundleSecurityPolicy(BundleSecurityProperties securityProps, Path policiesPath) {
        val publicKey       = loadPublicKey(securityProps);
        val keyCatalogue    = buildKeyCatalogue(securityProps.getKeys());
        val tenantTrust     = buildTenantTrust(securityProps.getTenants());
        val unsignedTenants = new HashSet<>(securityProps.getUnsignedTenants());

        if (publicKey != null || !keyCatalogue.isEmpty()) {
            log.info("Bundle signature verification enabled. Global key: {}, catalogue keys: {}, tenant bindings: {}",
                    publicKey != null, keyCatalogue.size(), tenantTrust.size());
            val builder = publicKey != null ? BundleSecurityPolicy.builder(publicKey) : BundleSecurityPolicy.builder();
            return builder.withKeyCatalogue(keyCatalogue).withTenantTrust(tenantTrust)
                    .withUnsignedTenants(unsignedTenants).build();
        }

        if (securityProps.isAllowUnsigned()) {
            return BundleSecurityPolicy.builder().disableSignatureVerification().withUnsignedTenants(unsignedTenants)
                    .build();
        }

        throw new BundleSecurityNotConfiguredException(policiesPath.toString());
    }

    private PublicKey loadPublicKey(BundleSecurityProperties securityProps) {
        if (securityProps.getPublicKeyPath() != null && !securityProps.getPublicKeyPath().isBlank()) {
            return loadPublicKeyFromFile(securityProps.getPublicKeyPath());
        }
        if (securityProps.getPublicKey() != null && !securityProps.getPublicKey().isBlank()) {
            return loadPublicKeyFromBase64(securityProps.getPublicKey());
        }
        return null;
    }

    private PublicKey loadPublicKeyFromFile(String keyPath) {
        try {
            val keyBytes = PemUtils.decodePemFromFile(Path.of(keyPath));
            return buildEd25519PublicKey(keyBytes);
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException(ERROR_FAILED_TO_READ_PUBLIC_KEY + keyPath, e);
        }
    }

    private PublicKey loadPublicKeyFromBase64(String base64Key) {
        return parsePublicKey(base64Key);
    }

    private PublicKey parsePublicKey(String keyContent) {
        try {
            byte[] keyBytes;
            if (keyContent.contains("BEGIN")) {
                keyBytes = PemUtils.decodePem(keyContent);
            } else {
                keyBytes = Base64.getDecoder().decode(keyContent.replaceAll("\\s", ""));
            }
            return buildEd25519PublicKey(keyBytes);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException(ERROR_FAILED_TO_PARSE_PUBLIC_KEY, e);
        }
    }

    private static PublicKey buildEd25519PublicKey(byte[] keyBytes)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        return KeyFactory.getInstance(ALGORITHM_ED25519).generatePublic(new X509EncodedKeySpec(keyBytes));
    }

    private Map<String, PublicKey> buildKeyCatalogue(Map<String, String> keysConfig) {
        if (keysConfig == null || keysConfig.isEmpty()) {
            return Map.of();
        }
        val catalogue = new HashMap<String, PublicKey>();
        for (val entry : keysConfig.entrySet()) {
            val keyId     = entry.getKey();
            val keyBase64 = entry.getValue();
            try {
                catalogue.put(keyId, parsePublicKey(keyBase64));
                log.debug("Loaded public key '{}' into key catalogue", keyId);
            } catch (IllegalStateException e) {
                throw new IllegalStateException(ERROR_FAILED_TO_PARSE_KEY_IN_CATALOGUE.formatted(keyId), e);
            }
        }
        return catalogue;
    }

    private Map<String, Set<String>> buildTenantTrust(Map<String, List<String>> tenantsConfig) {
        if (tenantsConfig == null || tenantsConfig.isEmpty()) {
            return Map.of();
        }
        val trust = new HashMap<String, Set<String>>();
        for (val entry : tenantsConfig.entrySet()) {
            trust.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }
        return trust;
    }

    private List<Object> collectFunctionLibraries(ApplicationContext context) {
        val libraries = new ArrayList<>();
        val beanNames = context.getBeanNamesForAnnotation(FunctionLibrary.class);
        for (val beanName : beanNames) {
            val bean = context.getBean(beanName);
            log.debug("Found function library bean: {} ({})", beanName, bean.getClass().getSimpleName());
            libraries.add(bean);
        }
        return libraries;
    }

    private List<Object> collectPolicyInformationPoints(ApplicationContext context) {
        val pips      = new ArrayList<>();
        val beanNames = context.getBeanNamesForAnnotation(PolicyInformationPoint.class);
        for (val beanName : beanNames) {
            val bean = context.getBean(beanName);
            log.debug("Found PIP bean: {} ({})", beanName, bean.getClass().getSimpleName());
            pips.add(bean);
        }
        return pips;
    }

    private List<Object> collectFunctionLibrariesFromProviders(ObjectProvider<FunctionLibraryProvider> providers) {
        val libraries = new ArrayList<>();
        providers.orderedStream().forEach(provider -> {
            val providerLibraries = provider.functionLibraries();
            log.debug("Found {} function libraries from provider {}", providerLibraries.size(),
                    provider.getClass().getSimpleName());
            libraries.addAll(providerLibraries);
        });
        return libraries;
    }

}
