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
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.functions.LibraryDocumentation;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.interpreter.pip.PolicyInformationPointDocumentation;

class DocumentationAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DocumentationAutoConfiguration.class));

    @Test
    void whenContextLoaded_thenDocumentationBeansArePresent() {

        final var mockAttributeContext = mock(AttributeContext.class);
        final var mockPipDoc           = new PolicyInformationPointDocumentation("PIP name", "PIP description",
                "PIP documentation");
        when(mockAttributeContext.getDocumentation()).thenReturn(List.of(mockPipDoc));

        final var functionContext = mock(FunctionContext.class);
        final var mockFunDoc      = new LibraryDocumentation("Library name", "Library description",
                "Library documentation");
        when(functionContext.getDocumentation()).thenReturn(List.of(mockFunDoc));

        contextRunner.withBean(AttributeContext.class, () -> mockAttributeContext)
                .withBean(FunctionContext.class, () -> functionContext).run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(PolicyInformationPointsDocumentation.class);
                    assertThat(context).hasSingleBean(FunctionLibrariesDocumentation.class);
                });
    }

}
