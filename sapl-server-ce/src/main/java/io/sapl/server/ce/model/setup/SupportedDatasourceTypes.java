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
package io.sapl.server.ce.model.setup;

import lombok.Getter;

@Getter
public enum SupportedDatasourceTypes {
    H2("H2", "org.h2.Driver", "jdbc:h2:file:~/sapl/db"),
    MARIADB("MariaDB", "org.mariadb.jdbc.Driver", "jdbc:mariadb://127.0.0.1:3306/saplserver");

    private final String displayName;
    private final String driverClassName;
    private final String defaultUrl;

    SupportedDatasourceTypes(String displayName, String driverClassName, String defaultUrl) {
        this.displayName     = displayName;
        this.driverClassName = driverClassName;
        this.defaultUrl      = defaultUrl;
    }

    public static SupportedDatasourceTypes getByDisplayName(String displayName) {
        for (SupportedDatasourceTypes datasourceTypes : SupportedDatasourceTypes.values()) {
            if (datasourceTypes.displayName.equals(displayName)) {
                return datasourceTypes;
            }
        }
        return null;
    }

    public static SupportedDatasourceTypes getByDriverClassName(String driverClassName) {
        for (SupportedDatasourceTypes datasourceTypes : SupportedDatasourceTypes.values()) {
            if (datasourceTypes.driverClassName.equals(driverClassName)) {
                return datasourceTypes;
            }
        }
        return null;

    }
}
