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
package io.sapl.attributeapigui.config;

import lombok.Data;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "io.sapl.attribute-api-gui")
public class AttributeApiGuiSecurityProperties implements InitializingBean {

    private static final String ERROR_MISSING_ADMIN_PASSWORD = "io.sapl.attribute-api-gui.admin-password is not set. Set it explicitly.";

    private String adminUsername = "admin";
    private String adminPassword;

    @Override
    public void afterPropertiesSet() {
        if (adminPassword == null || adminPassword.isBlank()) {
            throw new IllegalStateException(ERROR_MISSING_ADMIN_PASSWORD);
        }
    }
}
