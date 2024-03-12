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
package io.sapl.languageserver;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class SAPLLanguageServerTest {

    @Test
    void test() {
        SpringApplication springApplication = SAPLLanguageServer.configureSpringApplication();
        assertThat(springApplication.getWebApplicationType(), is(WebApplicationType.NONE));
    }

    @Test
    void testCommandLineRunner() {
        var saplLanguageServer = new SAPLLanguageServer();
        assertDoesNotThrow(saplLanguageServer::commandLineRunner);
    }
}
