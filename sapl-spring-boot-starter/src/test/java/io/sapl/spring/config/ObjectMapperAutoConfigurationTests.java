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
package io.sapl.spring.config;

import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.spring.serialization.SaplReactiveJacksonModule;
import io.sapl.spring.serialization.SaplServletJacksonModule;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ObjectMapperAutoConfigurationTests {

    @Test
    void whenRan_thenModulesAreRegistered() {
        final var contextRunner = new ApplicationContextRunner().withConfiguration(
                AutoConfigurations.of(JacksonAutoConfiguration.class, ObjectMapperAutoConfiguration.class));
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(JsonMapper.class);
            assertThat(context).hasSingleBean(SaplJacksonModule.class);
            assertThat(context).hasSingleBean(SaplServletJacksonModule.class);
            assertThat(context).hasSingleBean(SaplReactiveJacksonModule.class);
        });
    }

    @Test
    void whenRan_thenJsonMapperCanSerializeServletRequest() {
        final var contextRunner = new ApplicationContextRunner().withConfiguration(
                AutoConfigurations.of(JacksonAutoConfiguration.class, ObjectMapperAutoConfiguration.class));
        contextRunner.run(context -> {
            final var mapper  = context.getBean(JsonMapper.class);
            final var request = new MockHttpServletRequest("GET", "/test");
            assertThatCode(() -> mapper.writeValueAsString(request)).doesNotThrowAnyException();
        });
    }

    @Test
    void whenRan_thenJsonMapperCanSerializeReactiveRequest() {
        final var contextRunner = new ApplicationContextRunner().withConfiguration(
                AutoConfigurations.of(JacksonAutoConfiguration.class, ObjectMapperAutoConfiguration.class));
        contextRunner.run(context -> {
            final var mapper  = context.getBean(JsonMapper.class);
            final var request = MockServerHttpRequest.get("/test").build();
            assertThatCode(() -> mapper.writeValueAsString(request)).doesNotThrowAnyException();
        });
    }

}
