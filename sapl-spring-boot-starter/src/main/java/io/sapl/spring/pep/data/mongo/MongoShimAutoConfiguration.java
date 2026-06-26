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
package io.sapl.spring.pep.data.mongo;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;

import io.sapl.spring.pep.constraints.providers.MongoDbQueryRewritingProvider;

/**
 * Activates the Mongo arm of the shim-signal architecture: registers the
 * {@code mongo:queryRewriting} constraint handler provider and wraps every
 * Mongo
 * template bean in a CGLIB proxy so its data-reaching operations fire the shim.
 * Both stacks are covered: {@link MongoShimBeanPostProcessor} proxies a
 * {@code ReactiveMongoTemplate} and {@link MongoBlockingShimBeanPostProcessor}
 * proxies a {@code MongoTemplate}. Each proxy introduces the
 * {@code ShimSignalContributor} interface, so {@code MongoDbQueryShimSignal} is
 * advertised to the planner only for the template(s) that are actually present
 * and shimmed. Active when Spring Data MongoDB is on the classpath and not
 * explicitly disabled by
 * {@code io.sapl.method-security.mongo-shim.enabled=false}.
 */
@AutoConfiguration
@ConditionalOnClass(MongoOperations.class)
@ConditionalOnProperty(name = "io.sapl.method-security.mongo-shim.enabled", matchIfMissing = true)
public class MongoShimAutoConfiguration {

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    MongoDbQueryRewritingProvider mongoDbQueryRewritingProvider() {
        return new MongoDbQueryRewritingProvider();
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnClass(ReactiveMongoTemplate.class)
    MongoShimBeanPostProcessor mongoShimBeanPostProcessor() {
        return new MongoShimBeanPostProcessor();
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnClass(MongoTemplate.class)
    MongoBlockingShimBeanPostProcessor mongoBlockingShimBeanPostProcessor() {
        return new MongoBlockingShimBeanPostProcessor();
    }
}
