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
package io.sapl.spring.testsupport;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

import io.sapl.spring.pdp.embedded.PDPAutoConfiguration;

/**
 * Marks an inner test app as a SAPL PEP-side Spring Boot test configuration.
 * <p>
 * Equivalent to {@code @SpringBootConfiguration} +
 * {@code @EnableAutoConfiguration} with {@link PDPAutoConfiguration}
 * excluded. Tests that exercise PEP / HTTP / method-security wiring and supply
 * the PDP via {@code @MockitoBean} would
 * otherwise trigger the embedded PDP auto-config, which fails the context when
 * the test classpath has no
 * {@code /policies} resource.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootConfiguration
@EnableAutoConfiguration(exclude = PDPAutoConfiguration.class)
public @interface SaplPepTestApp {
}
