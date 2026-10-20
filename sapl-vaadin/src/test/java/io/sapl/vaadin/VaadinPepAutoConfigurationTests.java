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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import io.sapl.reactive.api.pdp.ReactivePolicyDecisionPoint;
import io.sapl.vaadin.annotation.LifecycleNavigationConfiguration;
import io.sapl.vaadin.annotation.VaadinNavigationPepService;
import io.sapl.vaadin.base.VaadinAuthorizationSubscriptionBuilderService;
import io.sapl.vaadin.constraint.providers.VaadinNotificationConstraintHandlerProvider;
import io.sapl.vaadin.constraint.providers.VaadinProConfirmationDialogConstraintHandlerProvider;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("Vaadin PEP auto configuration")
class VaadinPepAutoConfigurationTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(VaadinPepAutoConfiguration.class))
            .withBean(JsonMapper.class, () -> JsonMapper.builder().build())
            .withBean(ReactivePolicyDecisionPoint.class, () -> mock(ReactivePolicyDecisionPoint.class));

    @Test
    void whenApplicationHasPdpAndMapperThenPepServicesAndProvidersAreAvailable() {
        runner.run(context -> assertThat(context).hasSingleBean(PepBuilderService.class)
                .hasSingleBean(VaadinConstraintEnforcementService.class)
                .hasSingleBean(VaadinAuthorizationSubscriptionBuilderService.class)
                .hasSingleBean(VaadinNavigationPepService.class).hasSingleBean(LifecycleNavigationConfiguration.class)
                .hasSingleBean(VaadinNotificationConstraintHandlerProvider.class)
                .hasSingleBean(VaadinProConfirmationDialogConstraintHandlerProvider.class));
    }

    @Test
    void whenNavigationEnforcementIsDisabledThenNoNavigationListenerIsRegistered() {
        runner.withPropertyValues("io.sapl.vaadin.navigation.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(LifecycleNavigationConfiguration.class)
                        .hasSingleBean(VaadinNavigationPepService.class));
    }

    @Test
    void whenApplicationDefinesItsOwnPepBuilderServiceThenItIsKept() {
        var own = mock(PepBuilderService.class);
        runner.withBean(PepBuilderService.class, () -> own)
                .run(context -> assertThat(context).getBean(PepBuilderService.class).isSameAs(own));
    }

    @Test
    void whenNoPdpIsAvailableThenContextFailsToStart() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(VaadinPepAutoConfiguration.class))
                .withBean(JsonMapper.class, () -> JsonMapper.builder().build())
                .run(context -> assertThat(context).hasFailed());
    }

}
