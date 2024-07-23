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
package io.sapl.geo.mysql;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.geo.shared.DatabaseConnectionBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class MySql extends DatabaseConnectionBase {

    /**
     * @param auth   a {@link JsonNode} containing the settings for authorization
     * @param mapper a {@link ObjectMapper}
     */
    public MySql(JsonNode auth, ObjectMapper mapper) {

        createMySqlConnectionFactory(auth, getPort(auth));
        this.mapper = mapper;
    }

    protected static int getPort(JsonNode requestSettings) throws PolicyEvaluationException {
        if (requestSettings.has(PORT)) {
            return requestSettings.findValue(PORT).asInt();
        } else {

            return 3306;
        }
    }

}
