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
package io.sapl.attributeapigui.connection;

import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import io.sapl.attributeapigui.config.AttributeApiConnectionProperties;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.io.Serializable;

@Data
@Component
@VaadinSessionScope
public class ConnectionSettings implements Serializable {
    private ConnectionMode mode;
    private String         baseUrl;
    private String         username;
    private String         password;
    private String         apiKey;

    public ConnectionSettings(AttributeApiConnectionProperties properties) {
        this.mode     = properties.getMethod();
        this.baseUrl  = properties.getBaseUrl();
        this.username = properties.getBasic().getUsername();
        this.password = properties.getBasic().getPassword();
        this.apiKey   = properties.getApi().getKey();
    }

    public boolean isConfigured() {
        if (baseUrl == null || baseUrl.isBlank()) {
            return false;
        }
        return switch (mode) {
        case NONE, OIDC -> true;
        case BASIC      -> username != null && !username.isBlank() && password != null && !password.isBlank();
        case API        -> apiKey != null && !apiKey.isBlank();
        };
    }
}
