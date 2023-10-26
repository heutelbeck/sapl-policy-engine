/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.pdp.EmbeddedPolicyDecisionPoint;
import io.sapl.pdp.config.PDPConfigurationProvider;
import io.sapl.prp.PolicyRetrievalPoint;

class PDPAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(PolicyRetrievalPoint.class, () -> mock(PolicyRetrievalPoint.class))
            .withBean(PDPConfigurationProvider.class, () -> mock(PDPConfigurationProvider.class))
            .withConfiguration(AutoConfigurations.of(PDPAutoConfiguration.class));

    @Test
    void whenContextLoads_thenOneIsCreated() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(PolicyDecisionPoint.class);
            assertThat(context).hasSingleBean(EmbeddedPolicyDecisionPoint.class);
        });
    }

    @Test
    void whenAnotherPDPIsAlreadyPresent_thenDoNotLoadANewOne() {
        contextRunner.withBean(PolicyDecisionPoint.class, () -> mock(PolicyDecisionPoint.class)).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(PolicyDecisionPoint.class);
            assertThat(context).doesNotHaveBean(EmbeddedPolicyDecisionPoint.class);
        });
    }

}
