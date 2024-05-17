/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.springdatacommon.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;

import io.sapl.springdatacommon.services.ConstraintQueryEnforcementService;
import io.sapl.springdatacommon.services.MethodSecurityExpressionEvaluator;
import io.sapl.springdatacommon.services.QueryEnforceAuthorizationSubscriptionService;
import io.sapl.springdatacommon.services.RepositoryInformationCollectorService;
import io.sapl.springdatacommon.services.SecurityExpressionService;

@SpringBootTest(classes = SaplSpringDataCommonAutoConfiguration.class)
class SaplSpringDataCommonAutoConfigurationTests {

    @MockBean
    BeanFactory beanFactoryMock;

    @MockBean
    ObjectProvider<MethodSecurityExpressionHandler> securityExpressionHandlerMock;

    @Autowired
    ConstraintQueryEnforcementService constraintQueryEnforcementService;

    @Autowired
    QueryEnforceAuthorizationSubscriptionService queryEnforceAnnotationService;

    @Autowired
    SecurityExpressionService securityExpressionService;

    @Autowired
    MethodSecurityExpressionEvaluator securityExpressionEvaluator;

    @Autowired
    RepositoryInformationCollectorService repositoryInformationCollectorService;

    @Test
    void when_constraintQueryEnforcementService_then_createBeans() {
        // GIVEN

        // WHEN

        // THEN
        assertNotNull(constraintQueryEnforcementService);
        assertNotNull(queryEnforceAnnotationService);
        assertNotNull(securityExpressionService);
        assertNotNull(securityExpressionEvaluator);
        assertNotNull(repositoryInformationCollectorService);
    }

}
