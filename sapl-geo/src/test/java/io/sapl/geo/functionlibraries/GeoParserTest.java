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
package io.sapl.geo.functionlibraries;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.xml.stream.XMLStreamException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.xml.sax.SAXException;
import io.sapl.api.interpreter.Val;
import io.sapl.pip.http.HttpPolicyInformationPoint;
import io.sapl.pip.http.ReactiveWebClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import reactor.test.StepVerifier;

@TestInstance(Lifecycle.PER_CLASS)
class GeoParserTest {

    String        path;
    String        resourceDirectory;
    ObjectMapper  mapper;
    GeoParser     parser;
    MockWebServer mockBackEnd;

    String exp = "[{\"name\":\"Simple placemark\",\"Geometry\":\"<Point>\\n  <coordinates>-122.0822035425683,37.42228990140251,0.0</coordinates>\\n</Point>\\n\"},{\"name\":\"Extruded placemark\",\"Geometry\":\"<Point>\\n  <coordinates>-122.0857667006183,37.42156927867553,50.0</coordinates>\\n</Point>\\n\"},{\"name\":\"Roll over this icon\",\"Geometry\":\"<Point>\\n  <coordinates>-122.0856545755255,37.42243077405461,0.0</coordinates>\\n</Point>\\n\"},{\"name\":\"Tessellated\",\"Geometry\":\"<LineString>\\n  <coordinates>-112.0814237830345,36.10677870477137,0.0 -112.0870267752693,36.0905099328766,0.0</coordinates>\\n</LineString>\\n\"},{\"name\":\"Untessellated\",\"Geometry\":\"<LineString>\\n  <coordinates>-112.080622229595,36.10673460007995,0.0 -112.085242575315,36.09049598612422,0.0</coordinates>\\n</LineString>\\n\"},{\"name\":\"Building 41\",\"Geometry\":\"<Polygon>\\n  <outerBoundaryIs>\\n  <LinearRing>\\n    <coordinates>-122.0857412771483,37.42227033155257,17.0 -122.0858169768481,37.42231408832346,17.0 -122.085852582875,37.42230337469744,17.0 -122.0858799945639,37.42225686138789,17.0 -122.0858860101409,37.4222311076138,17.0 -122.0858069157288,37.42220250173855,17.0 -122.0858379542653,37.42214027058678,17.0 -122.0856732640519,37.42208690214408,17.0 -122.0856022926407,37.42214885429042,17.0 -122.0855902778436,37.422128290487,17.0 -122.0855841672237,37.42208171967246,17.0 -122.0854852065741,37.42210455874995,17.0 -122.0855067264352,37.42214267949824,17.0 -122.0854430712915,37.42212783846172,17.0 -122.0850990714904,37.42251282407603,17.0 -122.0856769818632,37.42281815323651,17.0 -122.0860162273783,37.42244918858722,17.0 -122.0857260327004,37.42229239604253,17.0 -122.0857412771483,37.42227033155257,17.0</coordinates>\\n  </LinearRing>\\n  </outerBoundaryIs>\\n</Polygon>\\n\"},{\"name\":\"The Pentagon\",\"Geometry\":\"<Polygon>\\n  <outerBoundaryIs>\\n  <LinearRing>\\n    <coordinates>-77.05788457660967,38.87253259892824,100.0 -77.05465973756702,38.87291016281703,100.0 -77.0531553685479,38.87053267794386,100.0 -77.05552622493516,38.868757801256,100.0 -77.05844056290393,38.86996206506943,100.0 -77.05788457660967,38.87253259892824,100.0</coordinates>\\n  </LinearRing>\\n  </outerBoundaryIs>\\n  <innerBoundaryIs>\\n  <LinearRing>\\n    <coordinates>-77.05668055019126,38.87154239798456,100.0 -77.05542625960818,38.87167890344077,100.0 -77.05485125901023,38.87076535397792,100.0 -77.05577677433152,38.87008686581446,100.0 -77.05691162017543,38.87054446963351,100.0 -77.05668055019126,38.87154239798456,100.0</coordinates>\\n  </LinearRing>\\n  </innerBoundaryIs>\\n</Polygon>\\n\"}]";
    Val    kml;

    @BeforeAll
    void setup() throws IOException {
        mapper            = new ObjectMapper();
        resourceDirectory = Paths.get("src", "test", "resources").toFile().getAbsolutePath();
        path              = resourceDirectory.concat("/geoparser/example.kml");
        parser            = new GeoParser(mapper);

        try (var reader = Files.newBufferedReader(Path.of(path), StandardCharsets.UTF_8)) {
            var    stringBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
            }
            kml = Val.of(stringBuilder.toString());
        }

    }

    @AfterEach
    void stopBackEnd() throws IOException {
        mockBackEnd.shutdown();
    }

    @Test
    void kmlTest() throws XMLStreamException, IOException, SAXException {
        var res       = parser.parseKML(kml);
        var parsedKml = res.getText();
        assertEquals(exp, parsedKml);
    }

    @Test
    void kmlTest2() throws IOException {

        mockBackEnd = new MockWebServer();
        mockBackEnd.start();
        MockResponse mockResponse = new MockResponse().setBody(kml.getText()).addHeader("Content-Type",
                MediaType.APPLICATION_XML_VALUE);
        mockBackEnd.enqueue(mockResponse);
        mockBackEnd.enqueue(mockResponse);

        var baseUrl  = String.format("http://localhost:%s", mockBackEnd.getPort());
        var template = """
                {
                    "baseUrl" : "%s",
                    "pollingIntervalMs" : 1000,
                    "accept" : "%s",
                    "repetitions" : 2
                }
                """;
        var request  = Val.ofJson(String.format(template, baseUrl, MediaType.APPLICATION_XML_VALUE));

        var pip      = new HttpPolicyInformationPoint(new ReactiveWebClient(mapper));
        var response = pip.get(request);

        StepVerifier.create(response.map(x -> {
            try {
                return parser.parseKML(x);
            } catch (XMLStreamException | IOException | SAXException e) {
                return Val.ofEmptyObject();
            }
        })).expectNext(Val.ofJson(exp)).expectNext(Val.ofJson(exp)).expectComplete().verify();
    }

    @Test
    void errorTest() {

        assertThrows(XMLStreamException.class, () -> parser.parseKML("invalid KML string"));
    }
}
