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

import io.sapl.api.attributes.PolicyInformationPoint;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.functions.FunctionLibraryClassProvider;
import io.sapl.api.pdp.PdpIdExtractor;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.pdp.PolicyDecisionPointBuilder;
import io.sapl.pdp.PolicyDecisionPointBuilder.PDPComponents;
import io.sapl.pdp.VoteInterceptor;
import io.sapl.pdp.configuration.source.PdpIdValidator;
import io.sapl.pdp.configuration.bundle.BundleSecurityPolicy;
import io.sapl.spring.pdp.embedded.EmbeddedPDPProperties.BundleSecurityProperties;
import jakarta.annotation.PreDestroy;
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
import org.springframework.context.annotation.Role;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
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
 * This configuration creates a {@link PolicyDecisionPoint} using the
 * {@link PolicyDecisionPointBuilder}. It automatically discovers and registers:
 * <ul>
 * <li>Custom function libraries (beans annotated with
 * {@link FunctionLibrary})</li>
 * <li>Custom policy information points (beans annotated with
 * {@link PolicyInformationPoint})</li>
 * <li>Decision interceptors (beans implementing
 * {@link VoteInterceptor})</li>
 * <li>PDP ID extractor for multi-tenant routing (beans implementing
 * {@link PdpIdExtractor})</li>
 * </ul>
 * <p>
 * Configuration is controlled via {@link EmbeddedPDPProperties} with prefix
 * {@code io.sapl.pdp.embedded}.
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(name = "io.sapl.pdp.PolicyDecisionPointBuilder")
@ConditionalOnProperty(prefix = "io.sapl.pdp.embedded", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(EmbeddedPDPProperties.class)
public class PDPAutoConfiguration {

    private PDPComponents pdpComponents;

    @Bean
    @ConditionalOnMissingBean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    PolicyDecisionPoint policyDecisionPoint(JsonMapper mapper, Clock clock,
            ObjectProvider<VoteInterceptor> interceptorProvider,
            ObjectProvider<FunctionLibraryClassProvider> functionLibraryClassProviders,
            ObjectProvider<PdpIdExtractor> pdpIdExtractorProvider, ApplicationContext applicationContext,
            EmbeddedPDPProperties properties) {

        log.info("Deploying embedded Policy Decision Point. Source: {}, Path: {}", properties.getPdpConfigType(),
                properties.getPoliciesPath());

        val builder = PolicyDecisionPointBuilder.withDefaults(mapper, clock);

        // Configure PDP ID extractor for multi-tenant routing
        pdpIdExtractorProvider.ifAvailable(extractor -> {
            log.debug("Registering custom PDP ID extractor: {}", extractor.getClass().getSimpleName());
            builder.withPdpIdExtractor(extractor.extract());
        });

        // Collect static function library classes from providers
        val functionLibraryClasses = collectFunctionLibraryClasses(functionLibraryClassProviders);
        if (!functionLibraryClasses.isEmpty()) {
            log.debug("Registering {} static function library classes", functionLibraryClasses.size());
            builder.withFunctionLibraries(functionLibraryClasses);
        }

        // Collect custom function libraries from application context
        val functionLibraries = collectFunctionLibraries(applicationContext);
        if (!functionLibraries.isEmpty()) {
            log.debug("Registering {} custom function library instances", functionLibraries.size());
            builder.withFunctionLibraryInstances(functionLibraries);
        }

        // Collect custom PIPs from application context
        val pips = collectPolicyInformationPoints(applicationContext);
        if (!pips.isEmpty()) {
            log.debug("Registering {} custom policy information points", pips.size());
            builder.withPolicyInformationPoints(pips);
        }

        // Collect interceptors
        val interceptors = interceptorProvider.orderedStream().toList();
        if (!interceptors.isEmpty()) {
            log.debug("Registering {} decision interceptors: {}.", interceptors.size(),
                    interceptors.stream().map(i -> i.getClass().getSimpleName()).toList());
            builder.withInterceptors(interceptors);
        }

        // Configure policy source based on properties
        configureSource(builder, properties);

        // Build and store components for cleanup
        this.pdpComponents = builder.build();

        return pdpComponents.pdp();
    }

    private void configureSource(PolicyDecisionPointBuilder builder, EmbeddedPDPProperties properties) {
        val path         = PdpIdValidator.resolveHomeFolderIfPresent(properties.getPoliciesPath());
        val resolvedPath = path.toAbsolutePath().normalize();

        switch (properties.getPdpConfigType()) {
        case DIRECTORY       -> {
            log.info("Loading policies from directory: {}", resolvedPath);
            builder.withDirectorySource(path);
        }
        case MULTI_DIRECTORY -> {
            log.info("Loading policies from multi-directory: {}", resolvedPath);
            builder.withMultiDirectorySource(path);
        }
        case BUNDLES         -> {
            log.info("Loading policies from bundles: {}", resolvedPath);
            val securityPolicy = createBundleSecurityPolicy(properties.getBundleSecurity());
            builder.withBundleDirectorySource(path, securityPolicy);
        }
        case RESOURCES       -> {
            val resourcePath = properties.getPoliciesPath();
            log.info("Loading policies from resources: {}", resourcePath);
            builder.withResourcesSource(resourcePath);
        }
        }
    }

    private BundleSecurityPolicy createBundleSecurityPolicy(BundleSecurityProperties securityProps) {
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

        if (securityProps.isAllowUnsigned() && securityProps.isAcceptRisks()) {
            return BundleSecurityPolicy.builder().disableSignatureVerification().acceptUnsignedBundleRisks()
                    .withUnsignedTenants(unsignedTenants).build();
        }

        throw new IllegalStateException("Bundle security not configured. Either provide a public key via "
                + "bundle-security.public-key-path or bundle-security.public-key, "
                + "or configure bundle-security.keys with tenant bindings, "
                + "or explicitly disable verification by setting both "
                + "bundle-security.allow-unsigned=true and bundle-security.accept-risks=true");
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
            val path       = Path.of(keyPath);
            val keyBytes   = Files.readAllBytes(path);
            val keyContent = new String(keyBytes, java.nio.charset.StandardCharsets.UTF_8).trim();
            return parsePublicKey(keyContent);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read public key from: " + keyPath, e);
        }
    }

    private PublicKey loadPublicKeyFromBase64(String base64Key) {
        return parsePublicKey(base64Key);
    }

    private PublicKey parsePublicKey(String keyContent) {
        try {
            val cleanKey   = keyContent.replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "").replaceAll("\\s", "");
            val keyBytes   = Base64.getDecoder().decode(cleanKey);
            val keySpec    = new X509EncodedKeySpec(keyBytes);
            val keyFactory = KeyFactory.getInstance("Ed25519");
            return keyFactory.generatePublic(keySpec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Failed to parse Ed25519 public key", e);
        }
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
                throw new IllegalStateException("Failed to parse public key '%s' in key catalogue".formatted(keyId), e);
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

    private List<Class<?>> collectFunctionLibraryClasses(ObjectProvider<FunctionLibraryClassProvider> providers) {
        val classes = new ArrayList<Class<?>>();
        providers.orderedStream().forEach(provider -> {
            val providerClasses = provider.functionLibraryClasses();
            log.debug("Found {} function library classes from provider {}", providerClasses.size(),
                    provider.getClass().getSimpleName());
            classes.addAll(providerClasses);
        });
        return classes;
    }

    @PreDestroy
    void cleanup() {
        if (pdpComponents != null) {
            log.debug("Disposing PDP resources");
            pdpComponents.dispose();
        }
    }

}
