package io.sapl.interpreter.pip.geo;
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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.GET;

import java.io.IOException;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.functions.FunctionException;
import io.sapl.api.pip.AttributeException;
import io.sapl.webclient.WebClientRequestExecutor;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

@Ignore
@RunWith(MockitoJUnitRunner.class)
public class TraccarTest {

	private static String positionsJson = "[{\"id\":16,\"attributes\":{\"batteryLevel\":66.0,\"distance\":1.0,"
			+ "\"totalDistance\":2,\"ip\":\"192.168.2.1\",\"motion\":false},\"deviceId\":1,"
			+ "\"type\":null,\"protocol\":\"osmand\",\"serverTime\":\"2017-09-16T11:00:00.000+0000\","
			+ "\"deviceTime\":\"2017-09-16T11:00:00.000+0000\",\"fixTime\":\"2017-09-16T11:00:00.000+0000\","
			+ "\"outdated\":false,\"valid\":true,\"latitude\":50.0,\"longitude\":4.4,"
			+ "\"altitude\":70.0,\"speed\":0.0,\"course\":0.0,\"address\":\"Sample Adress\",\"accuracy\":0.0,"
			+ "\"network\":null}]";

	private static String devicesJson = "[{\"id\":1,\"attributes\":{},\"name\":\"TestDevice\",\"uniqueId\":\"123456\","
			+ "\"status\":\"offline\",\"lastUpdate\":\"\",\"positionId\":0,\"groupId\":0,"
			+ "\"geofenceIds\":[],\"phone\":\"\",\"model\":\"\",\"contact\":\"\",\"category\":null}]";

	private static String geofencesJson = "[{\"id\":1,\"attributes\":{},\"name\":\"Kastel\",\"description\":\"\","
			+ "\"area\":\"POLYGON((50.0 8.2, 50.0 8.2, 50.0 8.3, 50.0 8.3, 50.0 8.2))\",\"calendarId\":0},"
			+ "{\"id\":3,\"attributes\":{},\"name\":\"Mainz\",\"description\":\"\",\"area\":"
			+ "\"POLYGON((50.03 8.23, 50.0 8.18, 50.0 8.23, 50.0 8.3, 50.0 8.4, 50.03 8.4, 50.05 8.3, 50.03 8.23))\","
			+ "\"calendarId\":0}]";

	private static String configJson = "{\"deviceID\": \"123456\", \"url\": \"http://lcl:00/api/\","
			+ "\"credentials\": \"YWRtaW46YWRtaW4=\", \"posValidityTimespan\": 10}";

	private static String expectedGeoPIPResponse = "{\"identifier\":\"testname\",\"position\":{\"type\":\"Point\","
			+ "\"coordinates\":[1,0.0]},\"altitude\":0.0,\"lastUpdate\":\"\",\"accuracy\":0.0,\"trust\":0.0,"
			+ "\"geofences\":{\"Kastel\":{\"type\":\"Polygon\",\"coordinates\":[[[50,8.2],[50,8.2],[50,8.3],[50,8.3],[50,8.2]]]}"
			+ ",\"Mainz\":{\"type\":\"Polygon\",\"coordinates\":[[[50.03,8.23],[50,8.18],[50,8.23],[50,8.3],[50,8.4],[50.03,8.4]"
			+ ",[50.05,8.3],[50.03,8.23]]]}}}";

	private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

	private static final String DEVICE_ID = "123456";

	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	private TraccarDevice trDevice;

	private TraccarConnection trConn;

	private TraccarGeofence[] trFences;

	private JsonNode jsonDumpOne;

	private JsonNode jsonDumpTwo;

	private WebClientRequestExecutor requestExecutor;

	@Before
	public void init() throws IOException {
		// mockStatic(HttpClientRequestExecutor.class);
		requestExecutor = mock(WebClientRequestExecutor.class);
		trConn = new TraccarConnection(MAPPER.readValue(configJson, TraccarConfig.class), requestExecutor);
		trDevice = MAPPER.readValue(devicesJson, TraccarDevice[].class)[0];
		trFences = MAPPER.readValue(geofencesJson, TraccarGeofence[].class);
		jsonDumpOne = JSON.textNode("1");
		jsonDumpTwo = JSON.textNode("2");
	}

	@Test
	public void jsonConstructor() throws IOException {
		JsonNode jsonConfig = MAPPER.readValue(configJson, JsonNode.class);
		assertEquals("Same parameters used in different constructor formats result in different results.",
				new TraccarConnection(jsonConfig).getConfig(), trConn.getConfig());
	}

	@Test
	public void getDeviceTest() throws AttributeException, IOException {
		when(requestExecutor.executeBlockingRequest(any(), eq(GET))).thenReturn(MAPPER.readTree(devicesJson));

		assertEquals("Traccar devices not correctly obtained.", "TestDevice",
				trConn.getTraccarDevice(DEVICE_ID).getName());
	}

	@Test
	public void findDeviceNullArgumentTest() throws IOException {
		when(requestExecutor.executeBlockingRequest(any(), eq(GET))).thenReturn(null);

		try {
			trConn.getTraccarDevice(DEVICE_ID).getUniqueId();
			fail("No exception thrown for empty device answer..");
		}
		catch (AttributeException e) {
			assertEquals("Wrong exception thrown for empty device answer.", e.getMessage(),
					String.format(TraccarConnection.NO_SUCH_DEVICE_FOUND, DEVICE_ID));
		}
	}

	@Test
	public void getDeviceIdNotExistingTest() throws IOException {
		when(requestExecutor.executeBlockingRequest(any(), eq(GET))).thenReturn(MAPPER.readTree(devicesJson));

		try {
			trConn.getTraccarDevice("0");
			fail("No exception thrown when looking up a device that is not in the server answer.");
		}
		catch (AttributeException e) {
			assertEquals("Wrong exception thrown when looking up a device that is not in the server answer.",
					e.getMessage(), String.format(TraccarConnection.NO_SUCH_DEVICE_FOUND, "0"));
		}
	}

	@Test
	public void getPositionTest() throws AttributeException, IOException {
		when(requestExecutor.executeBlockingRequest(any(), eq(GET))).thenReturn(MAPPER.readTree(positionsJson));

		TraccarPosition expectedPosition = MAPPER.readValue(positionsJson, TraccarPosition[].class)[0];
		assertEquals("Traccar position not correctly obtained.", expectedPosition, trConn.getTraccarPosition(trDevice));
	}

	@Test
	public void getPositionExceptionTest() throws IOException {
		try {
			when(requestExecutor.executeBlockingRequest(any(), eq(GET))).thenReturn(MAPPER.readTree("[]"));
			trConn.getTraccarPosition(trDevice);

			fail("No error message is thrown when zero positions are returned from server.");
		}
		catch (AttributeException e) {
			assertEquals("Wrong error message gets thrown when zero positions are returned from server.",
					TraccarConnection.UNABLE_TO_READ_FROM_SERVER, e.getMessage());
		}
	}

	@Test
	public void getGeofencesTest() throws AttributeException, IOException {
		when(requestExecutor.executeBlockingRequest(any(), eq(GET)))
				.thenReturn(MAPPER.convertValue(trFences, JsonNode.class));

		assertArrayEquals("Traccar geofences not correctly obtained.", trFences, trConn.getTraccarGeofences(trDevice));
	}

	@Test
	public void toGeoPIPResponseTest() throws IOException, FunctionException, AttributeException {
		TraccarConnection connSpy = spy(trConn);

		TraccarDevice tDev = mock(TraccarDevice.class);
		TraccarPosition tPos = mock(TraccarPosition.class);

		when(tDev.getName()).thenReturn("testname");
		when(tDev.getLastUpdate()).thenReturn("");
		when(tPos.getLatitude()).thenReturn(1.0);
		when(tPos.getLongitude()).thenReturn(0.0);

		doReturn(tDev).when(connSpy).getTraccarDevice(anyString());
		doReturn(tPos).when(connSpy).getTraccarPosition(any(TraccarDevice.class));
		doReturn(trFences).when(connSpy).getTraccarGeofences(any(TraccarDevice.class));

		assertEquals("Generation of GeoPIPResponse works not as expected.", connSpy.toGeoPIPResponse(),
				MAPPER.readValue(expectedGeoPIPResponse, JsonNode.class));
	}

	@Test
	public void httpHeaderGenerationTest() {
		assertEquals("HTTP-Header is not correctly created.",
				"{Authorization=Basic YWRtaW46YWRtaW4=, Accept=application/json}",
				trConn.getTraccarHTTPHeader().toString());
	}

	@Test
	public void createCredentialsTest() throws IOException {
		String config = "{\"deviceID\": \"123456\", \"url\": \"http://lcl:00/api/\","
				+ "\"username\": \"admin\", \"password\": \"admin\"}";
		TraccarConnection conn = new TraccarConnection(MAPPER.readValue(config, TraccarConfig.class));
		assertEquals("Base64 encoding of username and password is not correct.",
				"{Authorization=Basic YWRtaW46YWRtaW4=, Accept=application/json}",
				conn.getTraccarHTTPHeader().toString());
	}

	@Test
	public void positionEqualsTest() {
		EqualsVerifier.forClass(TraccarPosition.class).suppress(Warning.STRICT_INHERITANCE, Warning.NONFINAL_FIELDS)
				.withPrefabValues(JsonNode.class, jsonDumpOne, jsonDumpTwo).verify();
	}

	@Test
	public void geofenceEqualsTest() {
		EqualsVerifier.forClass(TraccarGeofence.class).suppress(Warning.STRICT_INHERITANCE, Warning.NONFINAL_FIELDS)
				.withPrefabValues(JsonNode.class, jsonDumpOne, jsonDumpTwo).verify();
	}

	@Test
	public void deviceEqualsTest() {
		EqualsVerifier.forClass(TraccarDevice.class).suppress(Warning.STRICT_INHERITANCE, Warning.NONFINAL_FIELDS)
				.withPrefabValues(JsonNode.class, jsonDumpOne, jsonDumpTwo).verify();
	}

	@Test
	public void configEqualsTest() {
		EqualsVerifier.forClass(TraccarConfig.class).suppress(Warning.STRICT_INHERITANCE, Warning.NONFINAL_FIELDS)
				.verify();
	}

	@Test
	public void positionCompareAscendingTest() {
		TraccarPosition a = mock(TraccarPosition.class);
		TraccarPosition b = mock(TraccarPosition.class);

		when(a.getId()).thenReturn(0);
		when(b.getId()).thenReturn(1);

		assertTrue("Sorting TraccarPositions in ascending order does not work properly.",
				TraccarPosition.compareAscending(a, b) == -1 && TraccarPosition.compareAscending(b, a) == 1
						&& TraccarPosition.compareAscending(a, a) == 0);
	}

	@Test
	public void positionCompareDescendingTest() {
		TraccarPosition a = mock(TraccarPosition.class);
		TraccarPosition b = mock(TraccarPosition.class);

		when(a.getId()).thenReturn(0);
		when(b.getId()).thenReturn(1);

		assertTrue("Sorting TraccarPositions in descending order does not work properly.",
				TraccarPosition.compareDescending(a, b) == 1 && TraccarPosition.compareDescending(b, a) == -1
						&& TraccarPosition.compareDescending(a, a) == 0);
	}

}
