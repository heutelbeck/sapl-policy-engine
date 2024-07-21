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
package io.sapl.geo.common;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.w3c.dom.Document;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;

public class SourceProvider {

    private static SourceProvider instance;

    GeometryFactory geometryFactory = new GeometryFactory();

    Coordinate[] coordinates = new Coordinate[] { new Coordinate(10, 12), new Coordinate(10, 14),
            new Coordinate(12, 10), new Coordinate(13, 14), new Coordinate(10, 12) };

    @Getter
    Point   point   = geometryFactory.createPoint(coordinates[0]);
    @Getter
    Polygon polygon = geometryFactory.createPolygon(coordinates);

    @Getter
    Document    xmlSource;
    @Getter
    JsonNode    jsonSource;
    @Getter
    Transformer transform;

    ObjectMapper MAPPER = new ObjectMapper();

    final String resourceDirectory = Paths.get("src", "test", "resources").toFile().getAbsolutePath();

    private SourceProvider() {
        setUp();
    }

    public static SourceProvider getInstance() {

        if (instance == null) {
            instance = new SourceProvider();
        }
        return instance;
    }

    public void setUp() {

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {

            transform = TransformerFactory.newInstance().newTransformer();
            transform.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

            File            f       = new File(resourceDirectory + "/xmlSource.xml");
            DocumentBuilder builder = factory.newDocumentBuilder();
            xmlSource = builder.parse(f);

        } catch (Exception e) {

            e.printStackTrace();
        }
        try {
            jsonSource = MAPPER.readTree(new File(resourceDirectory + "/jsonSource.json"));

        } catch (IOException e) {

            e.printStackTrace();
        }
    }

}
