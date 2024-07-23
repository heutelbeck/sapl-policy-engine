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
package io.sapl.geo.shared;

import java.util.ArrayList;
import java.util.List;

import org.geotools.api.geometry.MismatchedDimensionException;
import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.operation.TransformException;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.io.ParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.interpreter.Val;
import io.sapl.geo.functions.GeoProjector;
import io.sapl.geo.functions.GeometryConverter;
import io.sapl.geo.functions.WktConverter;
import io.sapl.geo.model.Geofence;
import io.sapl.geo.pip.GeoPipResponse;
import io.sapl.geo.pip.GeoPipResponseFormat;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class GeoMapper {

    private String       latitude;
    private String       longitude;
    private String       altitude;
    private String       lastUpdate;
    private String       accuracy;
    private ObjectMapper mapper;

    private static final String FENCENAME   = "name";
    private static final String AREA        = "area";
    private static final String ATTRIBUTES  = "attributes";
    private static final String DESCRIPTION = "description";
    private static final String CALENDARID  = "calendarId";
    private static final String ID          = "id";
    private static final String EPSG        = "EPSG:4326";

    /**
     * @param in            a {@link JsonNode} containing the latutide/longitude
     * @param format        a {@link GeoPipResponseFormat}
     * @param latitudeFirst a {@link Boolean} to set latitude/longitude as first
     *                      coordinate
     * @throws JsonProcessingException
     */
    public GeoPipResponse mapPosition(int deviceId, JsonNode in, GeoPipResponseFormat format, boolean latitudeFirst)
            throws JsonProcessingException {

        var   geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
        Point position;

        var lat = in.findValue(latitude).asDouble();
        var lon = in.findValue(longitude).asDouble();

        if (!latitudeFirst) {
            position = geometryFactory.createPoint(new Coordinate(lon, lat));

        } else {
            position = geometryFactory.createPoint(new Coordinate(lat, lon));
        }

        var posRes = (JsonNode) mapper.createObjectNode();

        switch (format) {
        case GEOJSON:
            posRes = GeometryConverter.geometryToGeoJsonNode(position).get();
            break;

        case WKT:
            posRes = GeometryConverter.geometryToWKT(position).get();
            break;

        case GML:
            posRes = GeometryConverter.geometryToGML(position).get();
            break;

        case KML:
            posRes = GeometryConverter.geometryToKML(position).get();
            break;

        default:
            break;
        }

        return GeoPipResponse.builder().deviceId(deviceId).position(posRes).altitude(in.findValue(altitude).asDouble())
                .lastUpdate(in.findValue(lastUpdate).asText()).accuracy(in.findValue(accuracy).asDouble()).build();
    }

    /**
     * @param in            a {@link JsonNode} containing the traccar geofences
     * @param format        a {@link GeoPipResponseFormat}
     * @param mapper        a {@link ObjectMapper}
     * @param latitudeFirst a {@link Boolean} to set latitude/longitude as first
     *                      coordinate
     * @throws JsonProcessingException
     * @throws JsonMappingException
     * @throws ParseException
     * @throws FactoryException
     * @throws TransformException
     * @throws MismatchedDimensionException
     */
    public List<Geofence> mapTraccarGeoFences(JsonNode in, GeoPipResponseFormat format, ObjectMapper mapper,
            boolean latitudeFirst) throws JsonProcessingException, ParseException, FactoryException,
            MismatchedDimensionException, TransformException {

        List<Geofence> fenceRes = new ArrayList<>();

        var fences = mapper.readTree(in.toString());

        for (JsonNode geoFence : fences) {
            var      factory = new GeometryFactory(new PrecisionModel(), 4326);
            Geometry geo     = WktConverter.wktToGeometry(Val.of(geoFence.findValue(AREA).asText()), factory);

            if (!latitudeFirst) {
                var geoProjector = new GeoProjector(EPSG, false, EPSG, true);
                geo = geoProjector.project(geo);
            }

            switch (format) {

            case GEOJSON:
                fenceRes.add(mapFence(geoFence, GeometryConverter.geometryToGeoJsonNode(geo).get()));
                break;

            case WKT:
                fenceRes.add(mapFence(geoFence, GeometryConverter.geometryToWKT(geo).get()));
                break;

            case GML:
                fenceRes.add(mapFence(geoFence, GeometryConverter.geometryToGML(geo).get()));
                break;

            case KML:
                fenceRes.add(mapFence(geoFence, GeometryConverter.geometryToKML(geo).get()));
                break;
            default:

                break;
            }

        }

        return fenceRes;

    }

    private Geofence mapFence(JsonNode geoFence, JsonNode area) {

        return Geofence.builder().id(geoFence.findValue(ID).asInt()).attributes(geoFence.findValue(ATTRIBUTES))
                .calendarId(geoFence.findValue(CALENDARID).asText()).name(geoFence.findValue(FENCENAME).asText())
                .description(geoFence.findValue(DESCRIPTION).asText()).area(area).build();
    }

    /**
     * @param in     a {@link JsonNode} containing the owntracks in-regions
     * @param mapper a {@link ObjectMapper}
     * @throws JsonProcessingException
     */
    public List<Geofence> mapOwnTracksInRegions(JsonNode in, ObjectMapper mapper) throws JsonProcessingException {

        List<Geofence> fenceRes = new ArrayList<>();

        var fences = mapper.readTree(in.toString());

        for (var geoFence : fences) {

            fenceRes.add(Geofence.builder().name(geoFence.asText()).build());

        }

        return fenceRes;

    }

}
