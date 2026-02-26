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
package io.sapl.spring.config;

import io.sapl.spring.method.blocking.SaplAuthorizationInterceptorsOrder;
import lombok.val;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.transaction.interceptor.BeanFactoryTransactionAttributeSourceAdvisor;

/**
 * Automatically adjusts the transaction interceptor order so that the
 * transaction boundary wraps SAPL Policy Enforcement Points. This ensures that
 * when a SAPL constraint handler fails after a transactional method succeeds,
 * the resulting exception propagates through the {@code TransactionInterceptor}
 * and triggers a rollback.
 * <p>
 * This configuration is imported by both {@link EnableSaplMethodSecurity} and
 * {@link EnableReactiveSaplMethodSecurity}.
 * <p>
 * The adjustment only applies when the transaction advisor still has the
 * default order ({@link Ordered#LOWEST_PRECEDENCE}). If the user has
 * explicitly configured a custom order via
 * {@code @EnableTransactionManagement(order = ...)}, it is left unchanged.
 * <p>
 * To disable this automatic adjustment entirely, set the property
 * {@code io.sapl.method-security.adjust-transaction-order=false}.
 * <p>
 * A {@link BeanFactoryPostProcessor} is used instead of a simple
 * {@link BeanPostProcessor} {@code @Bean} because the transaction advisor is
 * created during the {@code BeanPostProcessor} registration phase and would
 * not be intercepted by a regular {@code BeanPostProcessor} declared via
 * {@code @Bean}.
 *
 * @since 4.0.0
 */
@Slf4j
@ConditionalOnClass(name = "org.springframework.transaction.interceptor.BeanFactoryTransactionAttributeSourceAdvisor")
@Configuration(proxyBeanMethods = false)
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
class SaplTransactionManagementConfiguration {

    private static final String PROPERTY_ADJUST_TRANSACTION_ORDER = "io.sapl.method-security.adjust-transaction-order";

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    static BeanFactoryPostProcessor saplTransactionAdvisorOrderBfpp(Environment environment) {
        return beanFactory -> {
            val enabled = environment.getProperty(PROPERTY_ADJUST_TRANSACTION_ORDER, Boolean.class, Boolean.TRUE);
            if (!enabled) {
                return;
            }
            beanFactory.addBeanPostProcessor(new BeanPostProcessor() {
                @Override
                public @NonNull Object postProcessAfterInitialization(@NonNull Object bean, @NonNull String beanName) {
                    if (bean instanceof BeanFactoryTransactionAttributeSourceAdvisor advisor
                            && advisor.getOrder() == Ordered.LOWEST_PRECEDENCE) {
                        log.debug(
                                "Adjusting transaction advisor order from {} to {} for SAPL transaction-safe enforcement.",
                                Ordered.LOWEST_PRECEDENCE, SaplAuthorizationInterceptorsOrder.TRANSACTION_ORDER);
                        advisor.setOrder(SaplAuthorizationInterceptorsOrder.TRANSACTION_ORDER);
                    }
                    return bean;
                }
            });
        };
    }

}
