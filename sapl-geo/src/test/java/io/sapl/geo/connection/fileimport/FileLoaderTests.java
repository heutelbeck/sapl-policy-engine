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

import static org.hamcrest.CoreMatchers.notNullValue;

import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.interpreter.Val;
import reactor.test.StepVerifier;

@TestInstance(Lifecycle.PER_CLASS)
public class FileLoaderTests {

	
	final String GEOJSON = "GEOJSON";
	
	String path;
    String       resourceDirectory;
    ObjectMapper mapper;

    String template = """
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
        path = resourceDirectory.concat("\\\\fileimport\\\\%s");
    }

    @Test
    void geoJsonSingleTest() throws JsonProcessingException {

    	var ex = "{\"type\":\"Polygon\",\"coordinates\":[[[0.0,0.0],[10,0.0],[10,10],[0.0,10],[0.0,0.0]]],\"crs\":{\"type\":\"name\",\"properties\":{\"name\":\"EPSG:4326\"}}}";
    	var exp = Val.ofJson(ex);
        var pth = mapper.writeValueAsString(String.format(path, "geoJsonSingle.json"));
        var node = mapper.readValue(String.format(template, pth, GEOJSON ), JsonNode.class);
        var res  = FileLoader.connect(node, mapper);

        StepVerifier.create(res).expectNext(exp).expectNext(exp).expectComplete().verify();
    }

    @Test
    void geoJsonCollectionTest() throws JsonProcessingException {

    	var ex = "[{\"type\":\"Polygon\",\"coordinates\":[[[0.0,0.0],[10,0.0],[10,10],[0.0,10],[0.0,0.0]]],\"crs\":{\"type\":\"name\",\"properties\":{\"name\":\"EPSG:3857\"}}},{\"type\":\"Point\",\"coordinates\":[5,5],\"crs\":{\"type\":\"name\",\"properties\":{\"name\":\"EPSG:3857\"}}}]";
    	var exp = Val.ofJson(ex);
        var pth = mapper.writeValueAsString(String.format(path, "geojsonCollection.json"));
        var node = mapper.readValue(String.format(template2, pth, GEOJSON ), JsonNode.class);
        var res  = FileLoader.connect(node, mapper);

        StepVerifier.create(res).expectNext(exp).expectNext(exp).expectComplete().verify();
    }
    
    @Test
    void geoJsonMultipleTest() throws JsonProcessingException {

    	var ex = "[{\"type\":\"Point\",\"coordinates\":[13.404954,52.520008],\"crs\":{\"type\":\"name\",\"properties\":{\"name\":\"EPSG:4326\"}}},{\"type\":\"LineString\",\"coordinates\":[[13.404954,52.520008],[8.682127,50.110924]],\"crs\":{\"type\":\"name\",\"properties\":{\"name\":\"EPSG:4326\"}}},{\"type\":\"Polygon\",\"coordinates\":[[[13.404954,52.520008],[13.405537,52.520079],[13.405313,52.519505],[13.404743,52.519446],[13.404954,52.520008]]],\"crs\":{\"type\":\"name\",\"properties\":{\"name\":\"EPSG:4326\"}}}]";
    	var exp = Val.ofJson(ex);
        var pth = mapper.writeValueAsString(String.format(path, "geojsonMultiple.json"));
        var node = mapper.readValue(String.format(template, pth, GEOJSON ), JsonNode.class);
        var res  = FileLoader.connect(node, mapper);
	
        StepVerifier.create(res).expectNext(exp).expectNext(exp).expectComplete().verify();
    }
    
}
