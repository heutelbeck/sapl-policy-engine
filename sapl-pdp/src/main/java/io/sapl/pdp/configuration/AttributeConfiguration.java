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
package io.sapl.pdp.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.attributes.PolicyInformationPoint;
import io.sapl.attributes.broker.AttributeRepository;
import io.sapl.attributes.broker.pip.PolicyInformationPointAttributeBroker;
import io.sapl.pdp.PolicyDecisionPointBuilder;
import io.sapl.pdp.configuration.source.PDPConfigurationSource;
import io.sapl.pdp.configuration.source.ReplayingPDPConfigurationSource;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.json.JsonMapper;
import java.time.Clock;
import java.util.Arrays;

/**
 * Configuration for the attribute repository layer withing the PDP. Overrides
 * three beans that {@code PDPAutoconfiguration} ({@code sapl-spring-pdp}) would
 * provide by default. Ensures that the {@code sapl-node} is using this multi-tenant
 * and multi-backend configuration instead of a single InMemoryRepository.
 * <p>
 * This class can be replaced or set as default as soon as it's accepted into master.
 */
@Slf4j
@Configuration
public class AttributeConfiguration {

    private static final String PDP_CONFIGURATION_SOURCE_BEAN_NAME = "pdpConfigurationSource";
    
    /**
     * @Primay because the {@code PDPAutoConfiguration} also defines an AttributeRepository bean.
     * Without @Primary, injecting an AttributeRepository would be ambiguous.
     * @param source The given configuration source.
     * @return An attribute repository.
     */
    @Bean
    @Primary
    AttributeRepository attributeRepository(PDPConfigurationSource source) {
        return new RoutingAttributeRepository(source);
    }

    /**
     * Wires all Spring beans that are annotated with @PolicyInformationPoint and wires them 
     * also into the broker with the RoutingAttributeRepository as fallback.
     * @param repository The fallback repository
     * @param ctx 
     * @return The concrete PoliyInformationPointAttributeBroker to override the bean from the auto configuration and avoid duplicates.
     */
    @Bean
    PolicyInformationPointAttributeBroker attributeBroker(AttributeRepository repository, ApplicationContext ctx) {
        val pipBeans = Arrays.stream(ctx.getBeanNamesForAnnotation(PolicyInformationPoint.class)).map(ctx::getBean)
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));

        return PolicyDecisionPointBuilder.buildPolicyInformationPointAttributeBroker(Clock.systemUTC(),
                JsonMapper.builder().build(), true, pipBeans, repository);
    }
    
    /**
     * Wraps the shared pdpConfigurationSource bean so every subscriber sees atleas the last known event per pdp id.
     * Avoids that the first subscriber only sees the event. Static is needed because @Bean methods by BeanPostProcessor
     * are built early by Spring. Without static this config class would be instantiated too early.
     * @return The BeanPostProcessor used by this configuration
     */
    @Bean
    static BeanPostProcessor pdpConfigurationSourceReplayPostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (PDP_CONFIGURATION_SOURCE_BEAN_NAME.equals(beanName)
                        && bean instanceof PDPConfigurationSource source) {
                    return new ReplayingPDPConfigurationSource(source);
                }
                return bean;
            }
        };
    }
    
    // Builds an own object mapper for Spring that is missed because of the
    // @ConditionalOnMissingBean override.
    @Bean
    ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        return mapper;
    }

}
