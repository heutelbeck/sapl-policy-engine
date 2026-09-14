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
import lombok.extern.slf4j.Slf4j;
import lombok.val;
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
    /**
     * @Primay because the {@code PDPAutoConfiguration} also defines an AttributeRepository bean.
     * Without @Primary, injecting an AttributeRepository would be ambiguous. Declared with the
     * concrete return type (not AttributeRepository) so attributeRepositoryExtensionsProcessor()
     * below can inject it by its concrete type.
     * @return An attribute repository.
     */
    @Bean
    @Primary
    RoutingAttributeRepository attributeRepository() {
        return new RoutingAttributeRepository();
    }

    // Replaces the former raw subscription to the shared pdpConfigurationSource bean.
    // PdpVoterSource picks this bean up automatically (see PDPAutoConfiguration,
    // ObjectProvider<ExtensionsProcessor>) and calls prepare()/commit()/remove() on it
    // as part of its own, already race-free configuration handling.
    @Bean
    ExtensionsProcessor attributeRepositoryExtensionsProcessor(RoutingAttributeRepository repository) {
        return new AttributeRepositoryExtensionsProcessor(repository);
    }

    /**
     * Wires all Spring beans that are annotated with @PolicyInformationPoint and wires them
     * also into the broker with the RoutingAttributeRepository as fallback.
     *
     * @param repository The fallback repository
     * @param ctx
     * @return The concrete PoliyInformationPointAttributeBroker to override the bean from the auto configuration and
     * avoid duplicates.
     */
    @Bean
    PolicyInformationPointAttributeBroker attributeBroker(AttributeRepository repository, ApplicationContext ctx) {
        val pipBeans = Arrays.stream(ctx.getBeanNamesForAnnotation(PolicyInformationPoint.class)).map(ctx::getBean)
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));

        return PolicyDecisionPointBuilder.buildPolicyInformationPointAttributeBroker(Clock.systemUTC(),
                JsonMapper.builder().build(), true, pipBeans, repository);
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
