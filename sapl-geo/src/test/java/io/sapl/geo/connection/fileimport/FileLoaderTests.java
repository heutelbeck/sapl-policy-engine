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
package io.sapl.geo.connection.fileimport;

import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.sapl.api.interpreter.Val;
import io.sapl.geo.fileimport.FileLoader;

import reactor.test.StepVerifier;

@TestInstance(Lifecycle.PER_CLASS)
class FileLoaderTests {

    final String GEOJSON = "GEOJSON";
    final String WKT     = "WKT";
    final String GML     = "GML";
    final String KML     = "KML";

    String       path;
    String       resourceDirectory;
    ObjectMapper mapper;

    String template  = """
                     {
                     "path":%s,
                     "responseFormat":"%s",
            "repetitions":2
                 }
                 """;
    String template2 = """
                     {
                     "path":%s,
                     "responseFormat":"%s",
                     "crs":3857,
            "repetitions":2
                 }
                 """;

    @BeforeAll
    void setup() throws JsonProcessingException {
        mapper            = new ObjectMapper();
        resourceDirectory = Paths.get("src", "test", "resources").toFile().getAbsolutePath();
        path              = resourceDirectory.concat("\\\\fileimport\\\\%s");
    }


    @Test
    void geoJsonSingleTest() throws JsonProcessingException {

        var ex   = "{\"type\":\"Polygon\",\"coordinates\":[[[0.0,0.0],[10,0.0],[10,10],[0.0,10],[0.0,0.0]]],\"crs\":{\"type\":\"name\",\"properties\":{\"name\":\"EPSG:4326\"}}}";
        var exp  = Val.ofJson(ex);
        var pth  = mapper.writeValueAsString(String.format(path, "geoJsonSingle.json"));
        var node = mapper.readValue(String.format(template, pth, GEOJSON), JsonNode.class);
        var res  = new FileLoader(mapper).connect(node);

        StepVerifier.create(res).expectNext(exp).expectNext(exp).expectComplete().verify();
    }

    @Test
    void geoJsonCollectionTest() throws JsonProcessingException {

        var ex   = "[{\"type\":\"Polygon\",\"coordinates\":[[[0.0,0.0],[10,0.0],[10,10],[0.0,10],[0.0,0.0]]],\"crs\":{\"type\":\"name\",\"properties\":{\"name\":\"EPSG:3857\"}}},{\"type\":\"Point\",\"coordinates\":[5,5],\"crs\":{\"type\":\"name\",\"properties\":{\"name\":\"EPSG:3857\"}}}]";
        var exp  = Val.ofJson(ex);
        var pth  = mapper.writeValueAsString(String.format(path, "geojsonCollection.json"));
        var node = mapper.readValue(String.format(template2, pth, GEOJSON), JsonNode.class);
        var res  = new FileLoader(mapper).connect(node);

        StepVerifier.create(res).expectNext(exp).expectNext(exp).expectComplete().verify();
    }

    @Test
    void geoJsonMultipleTest() throws JsonProcessingException {

        var ex   = "[{\"type\":\"Point\",\"coordinates\":[13.404954,52.520008],\"crs\":{\"type\":\"name\",\"properties\":{\"name\":\"EPSG:4326\"}}},{\"type\":\"LineString\",\"coordinates\":[[13.404954,52.520008],[8.682127,50.110924]],\"crs\":{\"type\":\"name\",\"properties\":{\"name\":\"EPSG:4326\"}}},{\"type\":\"Polygon\",\"coordinates\":[[[13.404954,52.520008],[13.405537,52.520079],[13.405313,52.519505],[13.404743,52.519446],[13.404954,52.520008]]],\"crs\":{\"type\":\"name\",\"properties\":{\"name\":\"EPSG:4326\"}}}]";
        var exp  = Val.ofJson(ex);
        var pth  = mapper.writeValueAsString(String.format(path, "geojsonMultiple.json"));
        var node = mapper.readValue(String.format(template, pth, GEOJSON), JsonNode.class);
        var res  = new FileLoader(mapper).connect(node);

        StepVerifier.create(res).expectNext(exp).expectNext(exp).expectComplete().verify();
    }

    @Test
    void wktSingleTest() throws JsonProcessingException {

        var ex   = "POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))";
        var exp  = Val.of(ex);
        var pth  = mapper.writeValueAsString(String.format(path, "wktSingle.wkt"));
        var node = mapper.readValue(String.format(template, pth, WKT), JsonNode.class);
        var res  = new FileLoader(mapper).connect(node);

        StepVerifier.create(res).expectNext(exp).expectNext(exp).expectComplete().verify();
    }

    @Test
    void wktCollectionTest() throws JsonProcessingException {

        // var ex = "[\"POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))\",\"POINT (5 5)\"]";
        ArrayNode arrayNode = mapper.createArrayNode();
        arrayNode.add("POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0))");
        arrayNode.add("POINT (5 5)");
        var exp  = Val.ofJson(arrayNode.toString());
        var pth  = mapper.writeValueAsString(String.format(path, "wktCollection.wkt"));
        var node = mapper.readValue(String.format(template2, pth, WKT), JsonNode.class);
        var res  = new FileLoader(mapper).connect(node);

        StepVerifier.create(res).expectNext(exp).expectNext(exp).expectComplete().verify();
    }

    // nur die erste geometrie wird erkannt
//    @Test
//    void wktMultipleTest() throws JsonProcessingException {
//
//    	var ex = "[{\"type\":\"Point\",\"coordinates\":[13.404954,52.520008],\"crs\":{\"type\":\"name\",\"properties\":{\"name\":\"EPSG:4326\"}}},{\"type\":\"LineString\",\"coordinates\":[[13.404954,52.520008],[8.682127,50.110924]],\"crs\":{\"type\":\"name\",\"properties\":{\"name\":\"EPSG:4326\"}}},{\"type\":\"Polygon\",\"coordinates\":[[[13.404954,52.520008],[13.405537,52.520079],[13.405313,52.519505],[13.404743,52.519446],[13.404954,52.520008]]],\"crs\":{\"type\":\"name\",\"properties\":{\"name\":\"EPSG:4326\"}}}]";
//    	var exp = Val.of(ex);
//        var pth = mapper.writeValueAsString(String.format(path, "wktMultiple.wkt"));
//        var node = mapper.readValue(String.format(template, pth, WKT ), JsonNode.class);
//        var res  = FileLoader.connect(node, mapper);
//
//        StepVerifier.create(res).expectNext(exp).expectNext(exp).expectComplete().verify();
//    }

    @Test
    void gmlSingleTest() throws JsonProcessingException {

        var ex   = "<gml:Polygon>\n  <gml:outerBoundaryIs>\n    <gml:LinearRing>\n      <gml:coordinates>\n        0.0,0.0 10.0,0.0 10.0,10.0 0.0,10.0 0.0,0.0 \n      </gml:coordinates>\n    </gml:LinearRing>\n  </gml:outerBoundaryIs>\n</gml:Polygon>\n";
        var exp  = Val.of(ex);
        var pth  = mapper.writeValueAsString(String.format(path, "gmlSingle.gml"));
        var node = mapper.readValue(String.format(template, pth, GML), JsonNode.class);
        var res  = new FileLoader(mapper).connect(node);

        StepVerifier.create(res).expectNext(exp).expectNext(exp).expectComplete().verify();
    }

    @Test
    void gmlCollectionTest() throws JsonProcessingException {

        ArrayNode arrayNode = mapper.createArrayNode();
        arrayNode.add(
                "<gml:Polygon>\n  <gml:outerBoundaryIs>\n    <gml:LinearRing>\n      <gml:coordinates>\n        0.0,0.0 10.0,0.0 10.0,10.0 0.0,10.0 0.0,0.0 \n      </gml:coordinates>\n    </gml:LinearRing>\n  </gml:outerBoundaryIs>\n</gml:Polygon>\n");
        arrayNode.add("<gml:Point>\n  <gml:coordinates>\n    5.0,5.0 \n  </gml:coordinates>\n</gml:Point>\n");
        var exp  = Val.ofJson(arrayNode.toString());
        var pth  = mapper.writeValueAsString(String.format(path, "gmlCollection.gml"));
        var node = mapper.readValue(String.format(template2, pth, GML), JsonNode.class);
        var res  = new FileLoader(mapper).connect(node);

        StepVerifier.create(res).expectNext(exp).expectNext(exp).expectComplete().verify();
    }

    // kennt keine
//	@Test
//	void gmlMultipleTest() throws JsonProcessingException {
//
//		var ex = "[{\"type\":\"Point\",\"coordinates\":[13.404954,52.520008],\"crs\":{\"type\":\"name\",\"properties\":{\"name\":\"EPSG:4326\"}}},{\"type\":\"LineString\",\"coordinates\":[[13.404954,52.520008],[8.682127,50.110924]],\"crs\":{\"type\":\"name\",\"properties\":{\"name\":\"EPSG:4326\"}}},{\"type\":\"Polygon\",\"coordinates\":[[[13.404954,52.520008],[13.405537,52.520079],[13.405313,52.519505],[13.404743,52.519446],[13.404954,52.520008]]],\"crs\":{\"type\":\"name\",\"properties\":{\"name\":\"EPSG:4326\"}}}]";
//		var exp = Val.of(ex);
//	    var pth = mapper.writeValueAsString(String.format(path, "gmlMultiple.gml"));
//	    var node = mapper.readValue(String.format(template, pth, GML ), JsonNode.class);
//	    var res  = FileLoader.connect(node, mapper);
//
//	    StepVerifier.create(res).expectNext(exp).expectNext(exp).expectComplete().verify();
//	}

    @Test
    void kmlSingleTest() throws JsonProcessingException {

        var ex   = "<Polygon>\n  <outerBoundaryIs>\n  <LinearRing>\n    <coordinates>0.0,0.0 10.0,0.0 10.0,10.0 0.0,10.0 0.0,0.0</coordinates>\n  </LinearRing>\n  </outerBoundaryIs>\n</Polygon>\n";
        var exp  = Val.of(ex);
        var pth  = mapper.writeValueAsString(String.format(path, "kmlSingle.kml"));
        var node = mapper.readValue(String.format(template, pth, KML), JsonNode.class);
        var res  = new FileLoader(mapper).connect(node);

        StepVerifier.create(res).expectNext(exp).expectNext(exp).expectComplete().verify();
    }

    @Test
    void kmlCollectionTest() throws JsonProcessingException {

        ArrayNode arrayNode = mapper.createArrayNode();
        arrayNode.add(
                "<Polygon>\n  <outerBoundaryIs>\n  <LinearRing>\n    <coordinates>0.0,0.0 10.0,0.0 10.0,10.0 0.0,10.0 0.0,0.0</coordinates>\n  </LinearRing>\n  </outerBoundaryIs>\n</Polygon>\n");
        arrayNode.add("<Point>\n  <coordinates>5.0,5.0</coordinates>\n</Point>\n");

        var exp  = Val.ofJson(arrayNode.toString());
        var pth  = mapper.writeValueAsString(String.format(path, "kmlCollection.kml"));
        var node = mapper.readValue(String.format(template2, pth, KML), JsonNode.class);
        var res  = new FileLoader(mapper).connect(node);

        StepVerifier.create(res).expectNext(exp).expectNext(exp).expectComplete().verify();
    }

    // nur die erste geometrie wird erkannt
//	@Test
//	void kmlMultipleTest() throws JsonProcessingException {
//
//		var ex = "[{\"type\":\"Point\",\"coordinates\":[13.404954,52.520008],\"crs\":{\"type\":\"name\",\"properties\":{\"name\":\"EPSG:4326\"}}},{\"type\":\"LineString\",\"coordinates\":[[13.404954,52.520008],[8.682127,50.110924]],\"crs\":{\"type\":\"name\",\"properties\":{\"name\":\"EPSG:4326\"}}},{\"type\":\"Polygon\",\"coordinates\":[[[13.404954,52.520008],[13.405537,52.520079],[13.405313,52.519505],[13.404743,52.519446],[13.404954,52.520008]]],\"crs\":{\"type\":\"name\",\"properties\":{\"name\":\"EPSG:4326\"}}}]";
//		var exp = Val.of(ex);
//	    var pth = mapper.writeValueAsString(String.format(path, "kmlMultiple.kml"));
//	    var node = mapper.readValue(String.format(template, pth, KML ), JsonNode.class);
//	    var res  = FileLoader.connect(node, mapper);
//
//
//      res.subscribe(
//	      		 content ->{
//			 var b = content.get().toString();
//			 System.out.println("fileImport content: " + b);
//
//		 },
//	      error -> System.out.println(String.format("Error receiving file: {%s}", error)),
//	      () -> System.out.println("Completed!!!")
//	      );
//		try {
//			Thread.sleep(50000);
//		} catch (InterruptedException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//
//	    StepVerifier.create(res).expectNext(exp).expectNext(exp).expectComplete().verify();
//	}

}
