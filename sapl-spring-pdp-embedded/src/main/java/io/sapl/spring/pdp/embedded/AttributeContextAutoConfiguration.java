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

import java.util.Collection;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;

import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.api.pip.PolicyInformationPointSupplier;
import io.sapl.api.pip.StaticPolicyInformationPointSupplier;
import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.interpreter.pip.AttributeContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AutoConfiguration
@RequiredArgsConstructor
@AutoConfigureAfter(PolicyInformationPointsAutoConfiguration.class)
public class AttributeContextAutoConfiguration {

    private final Collection<PolicyInformationPointSupplier>       pipSuppliers;
    private final Collection<StaticPolicyInformationPointSupplier> staticPipSuppliers;
    private final ConfigurableApplicationContext                   applicationContext;

    @Bean
    @ConditionalOnMissingBean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    AttributeContext attributeContext() throws InitializationException {
        final var ctx = new AnnotationAttributeContext();
        for (var supplier : pipSuppliers) {
            for (var pip : supplier.get()) {
                log.trace("loading Policy Information Point: {}", pip.getClass().getSimpleName());
                ctx.loadPolicyInformationPoint(pip);
            }
        }
        for (var supplier : staticPipSuppliers) {
            for (var pip : supplier.get()) {
                log.trace("loading static Policy Information Point: {}", pip.getSimpleName());
                ctx.loadPolicyInformationPoint(pip);
            }
        }
        Collection<Object> beanPips = applicationContext.getBeansWithAnnotation(PolicyInformationPoint.class).values();
        for (var pip : beanPips) {
            log.trace("loading Spring bean Policy Information Point: {}", pip.getClass().getSimpleName());
            ctx.loadPolicyInformationPoint(pip);
        }
        return ctx;
    }

}
