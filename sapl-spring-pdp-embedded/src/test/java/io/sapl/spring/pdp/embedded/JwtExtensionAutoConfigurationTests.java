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
package io.sapl.spring.pdp.embedded;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.functions.FunctionLibrarySupplier;
import io.sapl.extension.jwt.JWTFunctionLibrary;
import io.sapl.extension.jwt.JWTKeyProvider;
import io.sapl.extension.jwt.JWTPolicyInformationPoint;

class JwtExtensionAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JwtExtensionAutoConfiguration.class));

    @Test
    void whenContextLoaded_thenDefaultLibrariesArePresent() {
        contextRunner.withBean(WebClient.Builder.class, () -> mock(WebClient.Builder.class))
                .withBean(JWTKeyProvider.class, () -> mock(JWTKeyProvider.class))
                .withBean(ObjectMapper.class, () -> mock(ObjectMapper.class)).run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JWTPolicyInformationPoint.class);
                    assertThat(context).hasBean("jwtFunctionLibrarySupplier");
                    assertThat(context.getBean("jwtFunctionLibrarySupplier", FunctionLibrarySupplier.class).get())
                            .anyMatch(x -> x instanceof JWTFunctionLibrary);
                    ;
                });
    }

}
