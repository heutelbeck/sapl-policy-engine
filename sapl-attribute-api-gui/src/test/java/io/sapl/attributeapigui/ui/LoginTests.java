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
package io.sapl.attributeapigui.ui;

import io.sapl.attributeapigui.GuiApplication;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;

@SpringBootTest(classes = GuiApplication.class, properties = { "io.sapl.attribute-api-gui.admin-username=admin",
        "io.sapl.attribute-api-gui.admin-password=admin",
        "spring.autoconfigure.exclude=org.springframework.boot.r2dbc.autoconfigure.R2dbcAutoConfiguration" })

// POST towards /login to trigger the security filter chain.
@AutoConfigureMockMvc
class LoginTests {
    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("When the credentials for admin within the login form are correct then login succeeds")
    void whenValidCredentialsThenLoginSucceeds() throws Exception {
        mockMvc.perform(formLogin("/login").user("admin").password("admin"))
                .andExpect(authenticated().withRoles("ADMIN"));
    }

    @Test
    @DisplayName("When the credentials for admin within the login form are invalid then login not succeeds")
    void whenInvalidCredentialsThenLoginFails() throws Exception {
        mockMvc.perform(formLogin("/login").user("user").password("user")).andExpect(unauthenticated());
    }

    @Test
    @DisplayName("When a instance of the login view is created the login view is successfully created")
    void whenLoginViewInstancedThenViewIsCreated() {
        LoginView login = new LoginView();
        assertNotNull(login);
    }
}
