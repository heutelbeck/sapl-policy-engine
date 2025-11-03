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
package io.sapl.spring.pdp.embedded;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.api.pip.PolicyInformationPointSupplier;
import io.sapl.api.pip.StaticPolicyInformationPointSupplier;
import io.sapl.attributes.broker.api.AttributeRepository;
import io.sapl.attributes.broker.api.AttributeStreamBroker;
import io.sapl.attributes.broker.impl.AnnotationPolicyInformationPointLoader;
import io.sapl.attributes.broker.impl.CachingAttributeStreamBroker;
import io.sapl.attributes.broker.impl.InMemoryAttributeRepository;
import io.sapl.attributes.broker.impl.InMemoryPolicyInformationPointDocumentationProvider;
import io.sapl.attributes.documentation.api.PolicyInformationPointDocumentationProvider;
import io.sapl.interpreter.InitializationException;
import io.sapl.validation.ValidatorFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;

import java.time.Clock;
import java.util.Collection;

@Slf4j
@AutoConfiguration
@RequiredArgsConstructor
@AutoConfigureAfter(PolicyInformationPointsAutoConfiguration.class)
public class AttributeContextAutoConfiguration {

    private final ObjectMapper                                     mapper;
    private final Collection<PolicyInformationPointSupplier>       pipSuppliers;
    private final Collection<StaticPolicyInformationPointSupplier> staticPipSuppliers;
    private final ConfigurableApplicationContext                   applicationContext;

    @Bean
    @ConditionalOnMissingBean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    InMemoryAttributeRepository inMemoryAttributeRepository(Clock clock) {
        return new InMemoryAttributeRepository(clock);
    }

    @Bean
    @ConditionalOnMissingBean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    AttributeStreamBroker attributestreamBroker(AttributeRepository attributeRepository) {
        return new CachingAttributeStreamBroker(attributeRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    PolicyInformationPointDocumentationProvider policyInformationPointDocumentationProvider() {
        return new InMemoryPolicyInformationPointDocumentationProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    AnnotationPolicyInformationPointLoader annotationPolicyInformationPointLoader(
            AttributeStreamBroker attributeStreamBroker, PolicyInformationPointDocumentationProvider docsProvider)
            throws InitializationException {
        final var loader = new AnnotationPolicyInformationPointLoader(attributeStreamBroker, docsProvider,
                new ValidatorFactory(mapper));
        for (var supplier : pipSuppliers) {
            for (var pip : supplier.get()) {
                log.trace("loading Policy Information Point: {}", pip.getClass().getSimpleName());
                loader.loadPolicyInformationPoint(pip);
            }
        }
        for (var supplier : staticPipSuppliers) {
            for (var pip : supplier.get()) {
                log.trace("loading static Policy Information Point: {}", pip.getSimpleName());
                loader.loadStaticPolicyInformationPoint(pip);
            }
        }
        Collection<Object> beanPips = applicationContext.getBeansWithAnnotation(PolicyInformationPoint.class).values();
        for (var pip : beanPips) {
            log.trace("loading Spring bean Policy Information Point: {}", pip.getClass().getSimpleName());
            loader.loadPolicyInformationPoint(pip);
        }
        return loader;
    }

}
