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
package io.sapl.vaadin;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;

import io.sapl.reactive.api.pdp.ReactivePolicyDecisionPoint;
import io.sapl.spring.pep.constraints.ConstraintHandlerProvider;
import io.sapl.vaadin.annotation.LifecycleNavigationConfiguration;
import io.sapl.vaadin.annotation.VaadinNavigationPepService;
import io.sapl.vaadin.base.VaadinAuthorizationSubscriptionBuilderService;
import io.sapl.vaadin.constraint.VaadinFunctionConstraintHandlerProvider;
import io.sapl.vaadin.constraint.providers.VaadinConfirmationDialogConstraintHandlerProvider;
import io.sapl.vaadin.constraint.providers.VaadinNotificationConstraintHandlerProvider;
import io.sapl.vaadin.constraint.providers.VaadinProConfirmationDialogConstraintHandlerProvider;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Registers the Vaadin policy enforcement point services, the shipped
 * constraint handler providers and the navigation enforcement listener.
 * <p>
 * Every bean yields to a bean of the same type defined by the application.
 * Navigation enforcement through {@code @OnDenyNavigate} can be switched off
 * with {@code io.sapl.vaadin.navigation.enabled=false}.
 */
@AutoConfiguration
public class VaadinPepAutoConfiguration {

    private static final String CONFIRM_DIALOG_CLASS = "com.vaadin.flow.component.confirmdialog.ConfirmDialog";

    @Bean
    @ConditionalOnMissingBean
    VaadinAuthorizationSubscriptionBuilderService vaadinAuthorizationSubscriptionBuilderService(
            ObjectProvider<MethodSecurityExpressionHandler> expressionHandlerProvider, JsonMapper mapper) {
        return new VaadinAuthorizationSubscriptionBuilderService(
                expressionHandlerProvider.getIfAvailable(DefaultMethodSecurityExpressionHandler::new), mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    VaadinConstraintEnforcementService vaadinConstraintEnforcementService(
            ObjectProvider<ConstraintHandlerProvider> constraintHandlerProviders,
            ObjectProvider<VaadinFunctionConstraintHandlerProvider> vaadinFunctionProviders,
            ObjectMapper objectMapper) {
        return new VaadinConstraintEnforcementService(constraintHandlerProviders.orderedStream().toList(),
                vaadinFunctionProviders.orderedStream().toList(), objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    PepBuilderService pepBuilderService(ReactivePolicyDecisionPoint pdp,
            VaadinConstraintEnforcementService vaadinConstraintEnforcementService) {
        return new PepBuilderService(pdp, vaadinConstraintEnforcementService);
    }

    @Bean
    @ConditionalOnMissingBean
    VaadinNavigationPepService vaadinNavigationPepService(PepBuilderService pepBuilderService,
            VaadinAuthorizationSubscriptionBuilderService authorizationSubscriptionBuilderService) {
        return new VaadinNavigationPepService(pepBuilderService, authorizationSubscriptionBuilderService);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(value = "io.sapl.vaadin.navigation.enabled", havingValue = "true", matchIfMissing = true)
    LifecycleNavigationConfiguration lifecycleNavigationConfiguration(
            VaadinNavigationPepService vaadinNavigationPepService) {
        return new LifecycleNavigationConfiguration(vaadinNavigationPepService);
    }

    @Bean
    @ConditionalOnMissingBean
    VaadinNotificationConstraintHandlerProvider vaadinNotificationConstraintHandlerProvider() {
        return new VaadinNotificationConstraintHandlerProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = CONFIRM_DIALOG_CLASS)
    VaadinProConfirmationDialogConstraintHandlerProvider vaadinProConfirmationDialogConstraintHandlerProvider() {
        return new VaadinProConfirmationDialogConstraintHandlerProvider();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnMissingClass(CONFIRM_DIALOG_CLASS)
    VaadinConfirmationDialogConstraintHandlerProvider vaadinConfirmationDialogConstraintHandlerProvider() {
        return new VaadinConfirmationDialogConstraintHandlerProvider();
    }

}
