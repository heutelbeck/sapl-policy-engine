/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.interpreter.pip.geo;

import static org.springframework.http.HttpMethod.GET;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.geotools.geometry.jts.JTSFactoryFinder;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.functions.FunctionException;
import io.sapl.api.pip.AttributeException;
import io.sapl.functions.GeometryBuilder;
import io.sapl.webclient.RequestSpecification;
import io.sapl.webclient.WebClientRequestExecutor;
import lombok.Getter;

public class TraccarConnection {

	private static final String TRACCAR_POSITIONS = "positions";

	private static final String TRACCAR_DEVICES = "devices";

	private static final String TRACCAR_GEOFENCES = "geofences";

	private static final String EMPTY_STRING = "";

	private static final char QUESTIONMARK = '?';

	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	private static final ObjectMapper MAPPER = new ObjectMapper();

	protected static final String UNABLE_TO_READ_FROM_SERVER = "Unable to make connection or retrieve data from tracking server.";

	protected static final String NO_SUCH_DEVICE_FOUND = "Unable to find (single) device with uniqueId='%s'.";

	protected static final String AF_TEST = "AF_TEST";

	protected static final String TEST_OKAY = "ok";

	private RequestSpecification requestSpec = new RequestSpecification();

	private WebClientRequestExecutor requestExecutor;

	@Getter
	private TraccarConfig config;

	public TraccarConnection(TraccarConfig conf) {
		this(conf, new WebClientRequestExecutor());
	}

	public TraccarConnection(JsonNode conf) {
		if (!AF_TEST.equals(conf.asText())) {
			this.config = MAPPER.convertValue(conf, TraccarConfig.class);
			this.requestSpec.setHeaders(getTraccarHTTPHeader());
			this.requestExecutor = new WebClientRequestExecutor();
		}
	}

	// Used by unit tests to mock the requestExecutor
	TraccarConnection(TraccarConfig conf, WebClientRequestExecutor requestExecutor) {
		this.config = conf;
		this.requestSpec.setHeaders(getTraccarHTTPHeader());
		this.requestExecutor = requestExecutor;
	}

	public JsonNode toGeoPIPResponse() throws AttributeException, FunctionException {
		if (config == null) {
			return JSON.textNode(TEST_OKAY);
		}
		else {
			TraccarDevice device = getTraccarDevice(config.getDeviceID());
			TraccarPosition position = getTraccarPosition(device);
			TraccarGeofence[] geofences = getTraccarGeofences(device);

			return buildGeoPIPesponse(device, position, geofences).toJsonNode();
		}
	}

	public TraccarDevice getTraccarDevice(String uniqueID) throws AttributeException {
		requestSpec.setUrl(JSON.textNode(buildTraccarApiGetUrl(TRACCAR_DEVICES, null)));
		try {
			final JsonNode response = requestExecutor.executeBlockingRequest(requestSpec, GET);
			TraccarDevice[] devices = MAPPER.convertValue(response, TraccarDevice[].class);
			return findDevice(devices, uniqueID);
		}
		catch (IOException e) {
			throw new AttributeException(e);
		}

	}

	public TraccarPosition getTraccarPosition(TraccarDevice device) throws AttributeException {
		HashMap<String, String> httpGetArguments = new HashMap<>();
		httpGetArguments.put("deviceId", String.valueOf(device.getId()));
		httpGetArguments.put("from",
				Instant.now().minus(config.getPosValidityTimespan(), ChronoUnit.MINUTES).toString());
		httpGetArguments.put("to", Instant.now().toString());

		requestSpec.setUrl(JSON.textNode(buildTraccarApiGetUrl(TRACCAR_POSITIONS, httpGetArguments)));

		try {
			final JsonNode response = requestExecutor.executeBlockingRequest(requestSpec, GET);
			TraccarPosition[] traccarPositions = MAPPER.convertValue(response, TraccarPosition[].class);
			if (traccarPositions.length == 0) {
				throw new AttributeException(UNABLE_TO_READ_FROM_SERVER);
			}

			// Highest ID is most current position
			Arrays.sort(traccarPositions, TraccarPosition::compareDescending);
			return traccarPositions[0];
		}
		catch (IOException e) {
			throw new AttributeException(e);
		}
	}

	public TraccarGeofence[] getTraccarGeofences(TraccarDevice device) throws AttributeException {
		HashMap<String, String> httpGetArguments = new HashMap<>();
		httpGetArguments.put("deviceId", String.valueOf(device.getId()));

		requestSpec.setUrl(JSON.textNode(buildTraccarApiGetUrl(TRACCAR_GEOFENCES, httpGetArguments)));

		try {
			final JsonNode response = requestExecutor.executeBlockingRequest(requestSpec, GET);
			return MAPPER.convertValue(response, TraccarGeofence[].class);
		}
		catch (IOException e) {
			throw new AttributeException(e);
		}
	}

	protected static GeoPIPResponse buildGeoPIPesponse(TraccarDevice device, TraccarPosition position,
			TraccarGeofence... geofences) throws FunctionException {
		return GeoPIPResponse.builder().identifier(device.getName()).position(formatPositionForPIPResponse(position))
				.altitude(position.getAltitude()).geofences(formatGeofencesForPIPResponse(geofences))
				.lastUpdate(device.getLastUpdate()).accuracy(position.getAccuracy()).build();
	}

	private static ObjectNode formatGeofencesForPIPResponse(TraccarGeofence... geofences) throws FunctionException {
		ObjectNode returnGeofences = JSON.objectNode();
		for (TraccarGeofence fence : geofences) {
			returnGeofences.set(fence.getName(), GeometryBuilder.wktToJsonNode(fence.getArea()));
		}
		return returnGeofences;
	}

	private static JsonNode formatPositionForPIPResponse(TraccarPosition position) throws FunctionException {
		GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
		Point jtsPosition = geometryFactory
				.createPoint(new Coordinate(position.getLatitude(), position.getLongitude()));

		return GeometryBuilder.toJsonNode(jtsPosition);
	}

	protected final Map<String, String> getTraccarHTTPHeader() {
		// Standard HTTP Authorization Header
		HashMap<String, String> headerProperties = new HashMap<>();
		headerProperties.put("Accept", "application/json");

		if (config.getCredentials() == null || config.getCredentials().isEmpty()) {
			byte[] encodedBytes = Base64.getEncoder()
					.encode((config.getUsername() + ":" + config.getPassword()).getBytes(StandardCharsets.UTF_8));
			headerProperties.put("Authorization", "Basic " + new String(encodedBytes, StandardCharsets.UTF_8));
		}
		else {
			headerProperties.put("Authorization", "Basic " + config.getCredentials());
		}

		return headerProperties;
	}

	private String buildTraccarApiGetUrl(String service, Map<String, String> httpGetArguments) {
		return config.getUrl() + service + formatQueryString(httpGetArguments);
	}

	private static String formatQueryString(Map<String, String> httpGetArguments) {
		if (httpGetArguments == null || httpGetArguments.size() < 0) {
			return EMPTY_STRING;
		}
		StringBuilder params = new StringBuilder();
		params.append(QUESTIONMARK);
		httpGetArguments.forEach((key, val) -> params.append(key).append('=').append(val).append('&'));
		params.setLength(params.length() - 1); // Cut last "&"

		return params.toString();
	}

	private static TraccarDevice findDevice(TraccarDevice[] devices, String uniqueID) throws AttributeException {
		if (devices == null) {
			throw new AttributeException(String.format(NO_SUCH_DEVICE_FOUND, uniqueID));
		}

		TraccarDevice returnDevice = null;
		for (TraccarDevice device : devices) {
			if (uniqueID.equals(device.getUniqueId())) {
				returnDevice = device;
				break;
			}
		}

		if (returnDevice == null) {
			throw new AttributeException(String.format(NO_SUCH_DEVICE_FOUND, uniqueID));
		}
		return returnDevice;
	}

}
