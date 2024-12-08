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

import org.geotools.api.geometry.MismatchedDimensionException;
import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.api.referencing.operation.TransformException;
import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Geometry;

public final class GeoProjector {

    private final MathTransform mathTransform;

    public GeoProjector() throws FactoryException {
        // standard configuration
        this(CrsConst.WGS84_CRS.getValue(), true, CrsConst.WEB_MERCATOR_CRS.getValue(), false);
    }

    /**
     * @param srcCrs sets the coordinate reference system for the source geometry,
     * e. g. "EPSG:4326". see {@link #project(Geometry)} and
     * <a href="https://epsg.io/?q=">epsg.io</a>
     * @param srcLongitudeFirst a {@link Boolean} to set latitude/longitude as first
     * coordinate
     * @param destCrs sets the coordinate reference system for the destination
     * geometry,e. g. "EPSG:4326". see {@link #project(Geometry)} and
     * <a href="https://epsg.io/?q=">epsg.io</a>
     * @param destLongitudeFirst a {@link Boolean} to set latitude/longitude as
     * first coordinate
     */
    public GeoProjector(String srcCrs, Boolean srcLongitudeFirst, String destCrs, Boolean destLongitudeFirst)
            throws FactoryException {
        this(CRS.getAuthorityFactory(srcLongitudeFirst).createCoordinateReferenceSystem(srcCrs),
                CRS.getAuthorityFactory(destLongitudeFirst).createCoordinateReferenceSystem(destCrs));
    }

    /**
     * @param srcCrs a {@link CoordinateReferenceSystem} to set the coordinate
     * reference system for the source geometry. see {@link #project(Geometry)}
     * @param destCrs a {@link CoordinateReferenceSystem} to set the coordinate
     * reference system for the destination geometry. see {@link #project(Geometry)}
     */
    public GeoProjector(CoordinateReferenceSystem srcCrs, CoordinateReferenceSystem destCrs) throws FactoryException {
        mathTransform = CRS.findMathTransform(srcCrs, destCrs, false);
    }

    /**
     * @param geometry a {@link Geometry} containing the settings
     * @return a transformed {@link Geometry}
     * @throws TransformException
     * @throws MismatchedDimensionException
     */
    public Geometry project(Geometry geometry) throws MismatchedDimensionException, TransformException {
        return JTS.transform(geometry, mathTransform);
    }

    /**
     * @param geometry a {@link Geometry} containing the settings
     * @return a transformed {@link Geometry}
     * @throws TransformException
     * @throws MismatchedDimensionException
     */
    public Geometry reProject(Geometry geometry) throws MismatchedDimensionException, TransformException {
        return JTS.transform(geometry, mathTransform.inverse());
    }
}