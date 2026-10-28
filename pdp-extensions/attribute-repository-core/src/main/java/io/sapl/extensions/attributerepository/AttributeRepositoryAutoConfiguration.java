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
package io.sapl.extensions.attributerepository;

import io.sapl.api.attributes.PolicyInformationPoint;
import io.sapl.api.model.Value;
import io.sapl.attributes.broker.AttributeRepository;
import io.sapl.attributes.broker.pip.PolicyInformationPointAttributeBroker;
import io.sapl.pdp.PolicyDecisionPointBuilder;
import lombok.val;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.json.JsonMapper;
import java.time.Clock;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import io.sapl.pdp.configuration.ExtensionsProcessor;

/**
 * Spring Boot auto configuration for the attribute repository layer within the PDP. Overrides
 * the attribute repository and the attribute broker that {@code PDPAutoConfiguration}
 * ({@code sapl-spring-pdp}) would provide by default, so every PDP ID uses the backend
 * configured in its {@code ext-attributerepository.json} instead of a single in-memory repository.
 * <p>
 * It is active as soon as this module is on the classpath. The module is pulled in by a backend
 * module such as {@code attribute-repository-redis}.
 *
 * @since 4.2.0
 */
@AutoConfiguration(beforeName = "io.sapl.spring.pdp.embedded.PDPAutoConfiguration")
public class AttributeRepositoryAutoConfiguration {
    /**
     * {@code @Primary} because {@code PDPAutoConfiguration} also defines an
     * {@code InMemoryAttributeRepository} bean. Without it, injecting an
     * {@code AttributeRepository} would be ambiguous. Declared with the concrete return type so
     * {@link #attributeRepositoryExtensionsProcessor} can inject it by that type.
     *
     * @return The routing attribute repository.
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
     * Builds the attribute broker with all Spring beans annotated with
     * {@code @PolicyInformationPoint} and the routing attribute repository as fallback.
     *
     * @param repository The fallback repository.
     * @param ctx The application context to look up the policy information point beans.
     * @return The attribute broker. It replaces the broker of {@code PDPAutoConfiguration} to
     * avoid two brokers.
     */
    @Bean
    PolicyInformationPointAttributeBroker attributeBroker(AttributeRepository repository, ApplicationContext ctx) {
        val pipBeans = Arrays.stream(ctx.getBeanNamesForAnnotation(PolicyInformationPoint.class)).map(ctx::getBean)
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));

        return PolicyDecisionPointBuilder.buildPolicyInformationPointAttributeBroker(Clock.systemUTC(),
                JsonMapper.builder().build(), true, pipBeans, repository);
    }

    @Bean
    @ConditionalOnProperty(name = "io.sapl.attribute-api.embedded", havingValue = "true")
    Function<String, Optional<Value>> attributeRepositoryConfigResolver(RoutingAttributeRepository repository) {
        return repository::currentRawConfig;
    }
}
