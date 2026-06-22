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

import io.sapl.spring.pep.method.blocking.SaplAuthorizationInterceptorsOrder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.Ordered;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.interceptor.BeanFactoryTransactionAttributeSourceAdvisor;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SAPL transaction-management configuration")
class SaplTransactionManagementConfigurationTests {

    private static int adjustAndGetOrder(BeanFactoryTransactionAttributeSourceAdvisor advisor, boolean enabled) {
        final var                      beanFactory = new DefaultListableBeanFactory();
        final var                      environment = new MockEnvironment()
                .withProperty("io.sapl.method-security.adjust-transaction-order", Boolean.toString(enabled));
        final BeanFactoryPostProcessor bfpp        = SaplTransactionManagementConfiguration
                .saplTransactionAdvisorOrderBfpp(environment);
        bfpp.postProcessBeanFactory(beanFactory);
        beanFactory.getBeanPostProcessors()
                .forEach(processor -> processor.postProcessAfterInitialization(advisor, "transactionAdvisor"));
        return advisor.getOrder();
    }

    @Test
    @DisplayName("an advisor at the default LOWEST_PRECEDENCE order is adjusted to the SAPL transaction order")
    void whenAdvisorOrderExplicitlyLowestPrecedenceThenStillAdjusted() {
        final var advisor = new BeanFactoryTransactionAttributeSourceAdvisor();
        advisor.setOrder(Ordered.LOWEST_PRECEDENCE);
        assertThat(adjustAndGetOrder(advisor, true)).isEqualTo(SaplAuthorizationInterceptorsOrder.TRANSACTION_ORDER);
    }

    @Test
    @DisplayName("an advisor with a distinct custom order is left unchanged")
    void whenAdvisorHasCustomOrderThenLeftUnchanged() {
        final var customOrder = 42;
        final var advisor     = new BeanFactoryTransactionAttributeSourceAdvisor();
        advisor.setOrder(customOrder);
        assertThat(adjustAndGetOrder(advisor, true)).isEqualTo(customOrder);
    }

    @Test
    @DisplayName("no adjustment is performed when the feature is disabled")
    void whenDisabledThenAdvisorLeftAtDefaultOrder() {
        final var advisor = new BeanFactoryTransactionAttributeSourceAdvisor();
        advisor.setOrder(Ordered.LOWEST_PRECEDENCE);
        assertThat(adjustAndGetOrder(advisor, false)).isEqualTo(Ordered.LOWEST_PRECEDENCE);
    }

}
