/**
 * Copyright © 2017 Dominic Heutelbeck (dheutelbeck@ftk.de)
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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.geotools.geometry.jts.JTSFactoryFinder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;

import io.sapl.api.functions.FunctionException;
import io.sapl.api.pip.AttributeException;
import io.sapl.functions.GeometryBuilder;
import io.sapl.pip.http.RequestExecutor;
import io.sapl.pip.http.RequestSpecification;
import lombok.Getter;

public class TraccarConnection {

	private static final String TRACCAR_POSITIONS = "positions";
	private static final String TRACCAR_DEVICES = "devices";
	private static final String TRACCAR_GEOFENCES = "geofences";
	private static final String EMPTY_STRING = "";
	private static final String QUESTIONMARK = "?";
	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
	private static final ObjectMapper MAPPER = new ObjectMapper();
	protected static final String UNABLE_TO_READ_FROM_SERVER = "Unable to make connection or retrieve data from tracking server.";
	protected static final String NO_SUCH_DEVICE_FOUND = "Unable to find (single) device with uniqueId='%s'.";
	protected static final String AF_TEST = "AF_TEST";
	protected static final String TEST_OKAY = "ok";

	private RequestSpecification requestSpec = new RequestSpecification();
	@Getter
	private TraccarConfig config;

	public TraccarConnection(TraccarConfig conf) {
		config = conf;
		requestSpec.setHeader(getTraccarHTTPHeader());
	}

	public TraccarConnection(JsonNode conf) {
		if (!AF_TEST.equals(conf.asText())) {
			config = MAPPER.convertValue(conf, TraccarConfig.class);
			requestSpec.setHeader(getTraccarHTTPHeader());
		}
	}

	public JsonNode toGeoPIPResponse() throws AttributeException, FunctionException {
		if (config == null) {
			return JSON.textNode(TEST_OKAY);
		} else {
			TraccarDevice device = getTraccarDevice(config.getDeviceID());
			TraccarPosition position = getTraccarPosition(device);
			TraccarGeofence[] geofences = getTraccarGeofences(device);

			return buildGeoPIPesponse(device, position, geofences).toJsonNode();
		}
	}

	public TraccarDevice getTraccarDevice(String uniqueID) throws AttributeException {
		requestSpec.setUrl(JSON.textNode(buildTraccarApiGetUrl(TRACCAR_DEVICES, null)));
		TraccarDevice[] devices = MAPPER.convertValue(
				RequestExecutor.executeUriRequest(requestSpec, RequestSpecification.HTTP_GET), TraccarDevice[].class);

		return findDevice(devices, uniqueID);
	}

	public TraccarPosition getTraccarPosition(TraccarDevice device) throws AttributeException {
		HashMap<String, String> httpGetArguments = new HashMap<>();
		httpGetArguments.put("deviceId", String.valueOf(device.getId()));
		httpGetArguments.put("from",
				Instant.now().minus(config.getPosValidityTimespan(), ChronoUnit.MINUTES).toString());
		httpGetArguments.put("to", Instant.now().toString());

		requestSpec.setUrl(JSON.textNode(buildTraccarApiGetUrl(TRACCAR_POSITIONS, httpGetArguments)));

		TraccarPosition[] traccarPositions = MAPPER.convertValue(
				RequestExecutor.executeUriRequest(requestSpec, RequestSpecification.HTTP_GET), TraccarPosition[].class);
		if (traccarPositions.length == 0) {
			throw new AttributeException(UNABLE_TO_READ_FROM_SERVER);
		}

		// Highest ID is most current position
		Arrays.sort(traccarPositions, TraccarPosition::compareDescending);
		return traccarPositions[0];
	}

	public TraccarGeofence[] getTraccarGeofences(TraccarDevice device) throws AttributeException {
		HashMap<String, String> httpGetArguments = new HashMap<>();
		httpGetArguments.put("deviceId", String.valueOf(device.getId()));

		requestSpec.setUrl(JSON.textNode(buildTraccarApiGetUrl(TRACCAR_GEOFENCES, httpGetArguments)));

		return MAPPER.convertValue(RequestExecutor.executeUriRequest(requestSpec, RequestSpecification.HTTP_GET),
				TraccarGeofence[].class);
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
		} else {
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
