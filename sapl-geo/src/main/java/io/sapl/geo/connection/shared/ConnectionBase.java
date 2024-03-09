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
package io.sapl.geo.connection.shared;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.geo.pip.GeoPipResponseFormat;

public abstract class ConnectionBase {

    protected static final String USER           = "user";
    protected static final String PASSWORD       = "password";
    protected static final String SERVER         = "server";
    protected static final String RESPONSEFORMAT = "responseFormat";

    protected static String getUser(JsonNode requestSettings) throws PolicyEvaluationException {
        if (requestSettings.has(USER)) {
            return requestSettings.findValue(USER).asText();
        } else {
            throw new PolicyEvaluationException("No User found");

        }

    }

    protected static String getPassword(JsonNode requestSettings) throws PolicyEvaluationException {
        if (requestSettings.has(PASSWORD)) {
            return requestSettings.findValue(PASSWORD).asText();
        } else {

            throw new PolicyEvaluationException("No Password found");
        }

    }

    protected static String getServer(JsonNode requestSettings) throws PolicyEvaluationException {
        if (requestSettings.has(SERVER)) {
            return requestSettings.findValue(SERVER).asText();
        } else {
            throw new PolicyEvaluationException("No Server found");

        }

    }

    protected static GeoPipResponseFormat getResponseFormat(JsonNode requestSettings, ObjectMapper mapper)
            throws PolicyEvaluationException {
        if (requestSettings.has(RESPONSEFORMAT)) {
            try {
            	return mapper.convertValue(requestSettings.findValue(RESPONSEFORMAT), GeoPipResponseFormat.class);
            }catch(Exception e) {
            	throw new PolicyEvaluationException(e);
            }
        } else {

            return GeoPipResponseFormat.GEOJSON;
        }

    }

}
