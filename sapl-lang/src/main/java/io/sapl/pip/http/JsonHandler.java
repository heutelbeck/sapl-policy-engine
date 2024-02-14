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
package io.sapl.pip.http;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.MediaType;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.experimental.UtilityClass;

@UtilityClass
class JsonHandler {

    private static final String BASE_URL         = "baseUrl";
    private static final String PATH             = "path";
    private static final String URL_PARAMS       = "urlParams";
    private static final String HEADERS          = "headers";
    private static final String BODY             = "body";
    private static final String POLLING_INTERVAL = "pollingInterval";
    private static final String REPEAT_TIMES     = "repeatTimes";

    public String getJsonBaseUrl(JsonNode requestSettings) throws RequestSettingException {
        if (requestSettings.has(BASE_URL)) {
            return requestSettings.findValue(BASE_URL).asText();
        }
        throw new RequestSettingException("No BaseUrl found");
    }

    public String getJsonPath(JsonNode requestSettings) {
        if (requestSettings.has(PATH)) {
            return requestSettings.findValue(PATH).asText();
        }
        return "";
    }

    public Map<String, String> getJsonUrlParams(JsonNode requestSettings, ObjectMapper mapper) {
        if (requestSettings.has(URL_PARAMS)) {
            return mapper.convertValue(requestSettings.findValue(URL_PARAMS), new TypeReference<Map<String, String>>() {
            });
        }
        return new HashMap<>();
    }

    public Map<String, String> getJsonHeaders(JsonNode requestSettings, ObjectMapper mapper) {
        if (requestSettings.has(HEADERS)) {
            return mapper.convertValue(requestSettings.findValue(HEADERS), new TypeReference<Map<String, String>>() {
            });
        }
        return new HashMap<>();
    }

    public JsonNode getJsonBody(JsonNode requestSettings, ObjectMapper mapper) {
        if (requestSettings.has(BODY)) {

            return requestSettings.findValue(BODY);
        }
        return mapper.createObjectNode();
    }

    public MediaType getJsonMediaType(JsonNode requestSettings, String param) {

        if (requestSettings.has(param)) {
            return MediaType.parseMediaType(requestSettings.findValue(param).asText());
        }
        return MediaType.APPLICATION_JSON;
    }

    public int getJsonPollingInterval(JsonNode requestSettings) {
        if (requestSettings.has(POLLING_INTERVAL)) {
            return requestSettings.findValue(POLLING_INTERVAL).asInt();
        }
        return 10;
    }

    public long getJsonRepeatTimes(JsonNode requestSettings) {
        if (requestSettings.has(REPEAT_TIMES)) {
            return requestSettings.findValue(REPEAT_TIMES).asLong();
        }
        return Long.MAX_VALUE;
    }

}
