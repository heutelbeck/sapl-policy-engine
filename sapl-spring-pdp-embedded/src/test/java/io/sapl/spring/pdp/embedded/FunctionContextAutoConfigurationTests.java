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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.functions.FunctionLibrarySupplier;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.functions.FunctionContext;

class FunctionContextAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FunctionContextAutoConfiguration.class));

    @Test
    void whenContextLoaded_thenAFunctionContextIsPresent() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(FunctionContext.class);
        });
    }

    @Test
    void whenDefaultLibrariesArePresent_thenAFunctionContextIsPresentAndLoadedThem() {
        contextRunner.withConfiguration(AutoConfigurations.of(FunctionLibrariesAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(FunctionContext.class);
                    assertThat(context.getBean(FunctionContext.class).isProvidedFunction("filter.blacken")).isTrue();
                    assertThat(context.getBean(FunctionContext.class).isProvidedFunction("standard.length")).isTrue();
                    assertThat(context.getBean(FunctionContext.class).isProvidedFunction("time.after")).isTrue();
                });
    }

    @Test
    void whenFunctionContextIsPresent_thenDoNotLoadANewOne() {
        contextRunner.withBean(FunctionContext.class, () -> mock(FunctionContext.class)).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(FunctionContext.class);
            assertThat(context).doesNotHaveBean(AnnotationFunctionContext.class);
        });
    }

    @Test
    void whenBadLibraryIsPresent_thenContextFailsToLoad() {
        contextRunner
                .withBean("badLibrary", FunctionLibrarySupplier.class, () -> (() -> List.of(new BadFunctionLibrary())))
                .run(context -> assertThat(context).hasFailed());
    }

    @FunctionLibrary
    protected static class BadFunctionLibrary {

        @Function
        void iAmABadSignatureFunction(Integer i, Float f) {
            // NOOP test dummy
        }

    }

}
