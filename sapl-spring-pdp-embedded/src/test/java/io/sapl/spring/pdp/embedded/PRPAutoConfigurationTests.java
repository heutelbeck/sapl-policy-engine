/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.spring.pdp.embedded;

import io.sapl.attributes.broker.api.AttributeStreamBroker;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.prp.GenericInMemoryIndexedPolicyRetrievalPointSource;
import io.sapl.prp.PolicyRetrievalPointSource;
import io.sapl.prp.PrpUpdateEventSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PRPAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(PrpUpdateEventSource.class, () -> {
                final var mock = mock(PrpUpdateEventSource.class);
                when(mock.getUpdates()).thenReturn(Flux.empty());
                return mock;
            }).withBean(FunctionContext.class, () -> mock(FunctionContext.class))
            .withBean(AttributeStreamBroker.class, () -> mock(AttributeStreamBroker.class))
            .withConfiguration(AutoConfigurations.of(PRPAutoConfiguration.class));

    @Test
    void whenPrpWithNaiveIndexIsConfigured_thenOneIsCreated() {
        contextRunner.withPropertyValues("io.sapl.pdp.embedded.index=NAIVE").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(PolicyRetrievalPointSource.class);
            assertThat(context).hasSingleBean(GenericInMemoryIndexedPolicyRetrievalPointSource.class);
        });
    }

    @Test
    void whenPrpWithCanonicalIndexIsConfigured_thenOneIsCreated() {
        contextRunner.withPropertyValues("io.sapl.pdp.embedded.index=CANONICAL").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(PolicyRetrievalPointSource.class);
            assertThat(context).hasSingleBean(GenericInMemoryIndexedPolicyRetrievalPointSource.class);
        });
    }

    @Test
    void whenAnotherPRPIsAlreadyPresent_thenDoNotLoadANewOne() {
        contextRunner.withBean(PolicyRetrievalPointSource.class, () -> mock(PolicyRetrievalPointSource.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(PolicyRetrievalPointSource.class);
                    assertThat(context).doesNotHaveBean(GenericInMemoryIndexedPolicyRetrievalPointSource.class);
                });
    }

}
