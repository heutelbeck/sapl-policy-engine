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
package io.sapl.geo.functions;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.geojson.GeoJsonReader;
import org.springframework.stereotype.Component;

import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import io.sapl.geo.functionlibraries.GeoFunctions;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class JsonConverter {

	/**
     * @param 		geoJson   	 a {@link Val} containing the GML-String 
     * @return		a {@link Geometry}
     */
    public static Geometry geoJsonToGeometry(Val geoJson) throws ParseException {

        return geoJsonToGeometry(geoJson.getText());
    }

    /**
     * @param 		geoJson   	 a {@link Val} containing the GML-String
     * @param		factory  a {@link GeometryFactory}
     * @return		a {@link Geometry}
     */
    public static Geometry geoJsonToGeometry(Val geoJson, GeometryFactory factory) throws ParseException {

        return geoJsonToGeometry(geoJson.getText(), factory);
    }

    /**
     * @param 		geoJson   	 a GeoJson-String 
     * @return		a {@link Geometry}
     */
    public static Geometry geoJsonToGeometry(String geoJson, GeometryFactory factory) throws ParseException {

        return (new GeoJsonReader(factory)).read(geoJson);
    }

    /**
     * @param 		geoJson   	 a GeoJson-String
     * @param		factory  a {@link GeometryFactory} 
     * @return		a {@link Geometry}
     */
    public static Geometry geoJsonToGeometry(String geoJson) throws ParseException {

        return (new GeoJsonReader()).read(geoJson);
    }

}
