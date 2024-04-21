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
import org.locationtech.jts.io.kml.KMLReader;
import org.springframework.stereotype.Component;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import lombok.NoArgsConstructor;

@Component
@NoArgsConstructor
@FunctionLibrary(name = GeoFunctions.NAME, description = GeoFunctions.DESCRIPTION)
public class KmlConverter {

    
    public static Geometry kmlToGeometry(Val kml) throws ParseException {

        return kmlToGeometry(kml.getText());
    }

    public static Geometry kmlToGeometry(Val kml, GeometryFactory factory) throws ParseException {

        return kmlToGeometry(kml.getText(), factory);
    }

    public static Geometry kmlToGeometry(String kml, GeometryFactory factory) throws ParseException {

        return (new KMLReader(factory)).read(kml);
    }

    public static Geometry kmlToGeometry(String kml) throws ParseException {

        return (new KMLReader()).read(kml);
    }

}
