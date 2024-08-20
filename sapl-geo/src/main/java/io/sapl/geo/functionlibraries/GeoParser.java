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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import javax.xml.stream.XMLStreamException;

import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.kml.v22.KML;
import org.geotools.kml.v22.KMLConfiguration;
import org.geotools.xsd.PullParser;
import org.locationtech.jts.geom.Geometry;
import org.xml.sax.SAXException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.TextNode;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import io.sapl.geo.functions.GeometryConverter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@FunctionLibrary(name = "geoParser", description = "")
public class GeoParser {

    private static final JsonNodeFactory JSON      = JsonNodeFactory.instance;
    private static final String          PARSE_KML = "parses kml to Geometries";
    private static final String          NAME      = "name";
    private static final String          GEOM      = "Geometry";

    private final ObjectMapper mapper;

    @Function(name = "parseKml", docs = PARSE_KML)
    public Val parseKML(Val kml) throws XMLStreamException, IOException, SAXException {

        return Val.of(parseKML(kml.getText()));

    }

    public ArrayNode parseKML(String kmlString) throws XMLStreamException, IOException, SAXException {

        var features = new ArrayList<SimpleFeature>();

        var           stream = new ByteArrayInputStream(kmlString.getBytes(StandardCharsets.UTF_8));
        var           config = new KMLConfiguration();
        var           parser = new PullParser(config, stream, KML.Placemark);
        SimpleFeature f      = null;

        while ((f = (SimpleFeature) parser.parse()) != null) {

            features.add(f);
        }

        return convertToObjects(features);

    }

    protected ArrayNode convertToObjects(ArrayList<SimpleFeature> placeMarks) {
        var arrayNode = mapper.createArrayNode();

        for (SimpleFeature feature : placeMarks) {
            var name         = "unnamed geometry";
            var nameProperty = feature.getAttribute(NAME);
            if (nameProperty != null) {
                name = nameProperty.toString();
            }
            var geom = (Geometry) feature.getAttribute(GEOM);
            var geo  = JSON.objectNode();

            if (geom != null) {

                geo.set(NAME, new TextNode(name));

                var json = new TextNode(GeometryConverter.geometryToKML(geom).getText());

                geo.set(GEOM, json);
                arrayNode.add(geo);
            }
        }
        return arrayNode;
    }

}
