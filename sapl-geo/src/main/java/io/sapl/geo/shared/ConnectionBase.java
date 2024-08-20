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
package io.sapl.geo.shared;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.geo.pip.GeoPipResponseFormat;

public abstract class ConnectionBase {

	protected static final String USER_CONST = "user";
	protected static final String PASSWORD_CONST = "password";
	protected static final String SERVER_CONST = "server";
	protected static final String RESPONSEFORMAT_CONST = "responseFormat";
	protected static final String POLLING_INTERVAL_CONST = "pollingIntervalMs";
	protected static final String REPEAT_TIMES_CONST = "repetitions";
	protected static final String PROTOCOL_CONST = "protocol";
	protected static final String LATITUDE_FIRST_CONST = "latitudeFirst";
	protected static final long DEFAULT_POLLING_INTERVALL_MS_CONST = 1000L;
	protected static final long DEFAULT_REPETITIONS_CONST = Long.MAX_VALUE;

	protected ObjectMapper mapper;

	protected static String getUser(JsonNode requestSettings) throws PolicyEvaluationException {
		if (requestSettings.has(USER_CONST)) {

			return requestSettings.findValue(USER_CONST).asText();
		} else {

			throw new PolicyEvaluationException("No User found");

		}

	}

	protected static String getPassword(JsonNode requestSettings) throws PolicyEvaluationException {
		if (requestSettings.has(PASSWORD_CONST)) {
			return requestSettings.findValue(PASSWORD_CONST).asText();
		} else {

			throw new PolicyEvaluationException("No Password found");
		}

	}

	protected static String getServer(JsonNode requestSettings) throws PolicyEvaluationException {
		if (requestSettings.has(SERVER_CONST)) {
			return requestSettings.findValue(SERVER_CONST).asText();
		} else {
			throw new PolicyEvaluationException("No Server found");

		}

	}

	protected static GeoPipResponseFormat getResponseFormat(JsonNode requestSettings, ObjectMapper mapper) {
		if (requestSettings.has(RESPONSEFORMAT_CONST)) {

			return mapper.convertValue(requestSettings.findValue(RESPONSEFORMAT_CONST), GeoPipResponseFormat.class);

		} else {

			return GeoPipResponseFormat.GEOJSON;
		}

	}

	protected static boolean getLatitudeFirst(JsonNode requestSettings) {
		if (requestSettings.has(LATITUDE_FIRST_CONST)) {
			return requestSettings.findValue(LATITUDE_FIRST_CONST).asBoolean();
		} else {
			return true;
		}

	}

}
