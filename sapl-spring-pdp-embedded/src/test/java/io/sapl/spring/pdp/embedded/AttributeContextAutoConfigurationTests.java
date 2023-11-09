/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import io.sapl.api.pip.Attribute;
import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.api.pip.PolicyInformationPointSupplier;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.interpreter.pip.AttributeContext;

class AttributeContextAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AttributeContextAutoConfiguration.class));

    @Test
    void whenContextLoaded_thenAFunctionContextIsPresent() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(AttributeContext.class);
        });
    }

    @Test
    void whenAttributeContextIsPresent_thenDoNotLoadANewOne() {
        contextRunner.withBean(AttributeContext.class, () -> mock(AttributeContext.class)).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(AttributeContext.class);
            assertThat(context).doesNotHaveBean(AnnotationAttributeContext.class);
        });
    }

    @Test
    void whenDefaultLibrariesArePresent_thenAFunctionContextIsPresentAndLoadedThem() {
        contextRunner.withConfiguration(AutoConfigurations.of(PolicyInformationPointsAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AttributeContext.class);
                    assertThat(context.getBean(AttributeContext.class).isProvidedFunction("time.now")).isTrue();
                });
    }

    @Test
    void whenBadLibraryIsPresent_thenContextFailsToLoad() {
        contextRunner
                .withBean("badLibrary", PolicyInformationPointSupplier.class,
                        () -> (() -> List.of(new BadPolicyInformationPointLibrary())))
                .run(context -> assertThat(context).hasFailed());
    }

    @PolicyInformationPoint
    protected static class BadPolicyInformationPointLibrary {

        @Attribute
        void iAmABadSignatureAttribute(Integer i, Float f) {
            /* NOOP */
        }

    }

}
