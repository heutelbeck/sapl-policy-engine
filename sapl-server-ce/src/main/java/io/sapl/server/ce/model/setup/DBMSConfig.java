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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@NoArgsConstructor
public class DBMSConfig {
    static final String DRIVERCLASSNAME_PATH = "spring.datasource.driverClassName";
    static final String URL_PATH             = "spring.datasource.url";
    static final String USERNAME_PATH        = "spring.datasource.username";
    static final String PASSWORD_PATH        = "spring.datasource.password";

    private SupportedDatasourceTypes dbms;
    private String                   url;
    private String                   username;
    private String                   password;
    private boolean                  validConfig = false;
    @Setter
    private boolean                  saved       = false;

    public void setDbms(SupportedDatasourceTypes dbms) {
        this.dbms        = dbms;
        this.validConfig = false;
    }

    public void setUrl(String url) {
        this.url         = url;
        this.validConfig = false;
    }

    public void setUsername(String username) {
        this.username    = username;
        this.validConfig = false;
    }

    public void setPassword(String password) {
        this.password    = password;
        this.validConfig = false;
    }

    public void testConnection(boolean createDbFileForSupportedDbms) throws SQLException {
        this.validConfig = false;
        Connection connection;
        if (this.dbms == SupportedDatasourceTypes.H2 && !createDbFileForSupportedDbms) {
            connection = DriverManager.getConnection(this.url + ";IFEXISTS=TRUE", this.username, this.password);
        } else {
            connection = DriverManager.getConnection(this.url, this.username, this.password);
        }
        connection.close();
        this.validConfig = true;
    }

}
