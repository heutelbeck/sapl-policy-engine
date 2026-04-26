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
package io.sapl.spring.pep.method.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.interceptor.BeanFactoryTransactionAttributeSourceAdvisor;

import io.sapl.spring.config.EnableSaplMethodSecurity;
import io.sapl.spring.pep.method.blocking.SaplAuthorizationInterceptorsOrder;

/**
 * Verifies the {@code SaplTransactionManagementConfiguration} ordering
 * adjustment in three regimes: default (SAPL adjusts), escape hatch enabled
 * (SAPL leaves the order alone), and user-supplied custom order (SAPL leaves
 * the explicit choice alone).
 */
class TransactionAdvisorOrderingTests {

    @SpringBootTest(classes = OrderingTestApp.class)
    @DisplayName("Default: SAPL adjusts the transaction advisor order so it nests outside the PEPs")
    static class Default {

        @Autowired
        BeanFactoryTransactionAttributeSourceAdvisor advisor;

        @Test
        @DisplayName("Order equals SaplAuthorizationInterceptorsOrder.TRANSACTION_ORDER")
        void whenDefaultThenOrderAdjustedToSaplValue() {
            assertThat(advisor.getOrder()).isEqualTo(SaplAuthorizationInterceptorsOrder.TRANSACTION_ORDER);
        }
    }

    @SpringBootTest(classes = OrderingTestApp.class)
    @TestPropertySource(properties = "io.sapl.method-security.adjust-transaction-order=false")
    @DisplayName("Escape hatch: with adjust-transaction-order=false, SAPL leaves the advisor at LOWEST_PRECEDENCE")
    static class EscapeHatch {

        @Autowired
        BeanFactoryTransactionAttributeSourceAdvisor advisor;

        @Test
        @DisplayName("Order remains Ordered.LOWEST_PRECEDENCE (no adjustment)")
        void whenEscapeHatchSetThenOrderUnchanged() {
            assertThat(advisor.getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE);
        }
    }

    @SpringBootTest(classes = CustomOrderTestApp.class)
    @DisplayName("Custom order: when the user has set a non-default order via @EnableTransactionManagement(order = ...)")
    static class CustomOrder {

        static final int USER_CHOSEN_ORDER = 42;

        @Autowired
        BeanFactoryTransactionAttributeSourceAdvisor advisor;

        @Test
        @DisplayName("SAPL leaves the user's chosen order alone")
        void whenCustomOrderConfiguredThenLeftAlone() {
            assertThat(advisor.getOrder()).isEqualTo(USER_CHOSEN_ORDER);
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableSaplMethodSecurity
    @EnableTransactionManagement
    static class OrderingTestApp {

        @Bean
        DataSource dataSource() {
            return new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).setName("sapl-ordering").build();
        }

        @Bean
        @Primary
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableSaplMethodSecurity
    @EnableTransactionManagement(order = CustomOrder.USER_CHOSEN_ORDER)
    static class CustomOrderTestApp {

        @Bean
        DataSource dataSource() {
            return new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).setName("sapl-ordering-custom")
                    .build();
        }

        @Bean
        @Primary
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }
}
