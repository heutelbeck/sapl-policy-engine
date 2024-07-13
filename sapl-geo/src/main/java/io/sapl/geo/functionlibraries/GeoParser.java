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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.kml.v22.KML;
import org.geotools.kml.v22.KMLConfiguration;
import org.geotools.xsd.PullParser;
import org.locationtech.jts.geom.Geometry;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.TextNode;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.geo.functions.GeometryConverter;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@FunctionLibrary(name = "geoParser", description = "")
public class GeoParser {

    private static final JsonNodeFactory JSON      = JsonNodeFactory.instance;
    private static final String          PARSE_KML = "parses kml to Geometries";
    private static final String          ERROR     = "Error while parsing kml";
    private static final String          NAME      = "name";
    private static final String          GEOM      = "Geometry";

    private final ObjectMapper mapper;

    @Function(name = "parseKml", docs = PARSE_KML)
    public Val parseKML(Val kml) {

        return Val.of(parseKML(kml.getText()));

    }

    public ArrayNode parseKML(String kmlString) {

        var features = new ArrayList<SimpleFeature>();
        try {
            var           stream = new ByteArrayInputStream(kmlString.getBytes(StandardCharsets.UTF_8));
            var           config = new KMLConfiguration();
            var           parser = new PullParser(config, stream, KML.Placemark);
            SimpleFeature f      = null;

            while ((f = (SimpleFeature) parser.parse()) != null) {

                features.add(f);
            }

        } catch (Exception e) {

            throw new PolicyEvaluationException(ERROR, e);
        }
        return convertToObjects(features);
    }

    protected ArrayNode convertToObjects(Collection<?> placeMarks) {
        var arrayNode = mapper.createArrayNode();

        for (Object obj : placeMarks) {

            if (!(obj instanceof SimpleFeature feature)) {
                throw new PolicyEvaluationException(ERROR);
            } else {
                var name         = "unnamed geometry";
                var nameProperty = feature.getAttribute(NAME);
                if (nameProperty != null) {
                    name = nameProperty.toString();
                }
                var geom = (Geometry) feature.getAttribute(GEOM);
                var geo  = JSON.objectNode();

                if (geom != null) {
                    geo.set(NAME, new TextNode(name));
                    geo.set(GEOM, GeometryConverter.geometryToKML(geom).get());
                    arrayNode.add(geo);
                }
            }
        }
        return arrayNode;
    }

}