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
package io.sapl.geo.connection.shared;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.Row;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.geo.connection.mysql.MySqlConnection;
import io.sapl.geo.connection.postgis.PostGisConnection;
import io.sapl.geo.functions.GeoProjector;
import io.sapl.geo.functions.GeometryConverter;
import io.sapl.geo.functions.JsonConverter;
import io.sapl.geo.pip.GeoPipResponseFormat;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.retry.Repeat;

public abstract class DatabaseConnection extends ConnectionBase {
	
	protected static final String PORT          = "port";
	protected static final String DATABASE      = "dataBase";
	protected static final String TABLE         = "table";
	protected static final String GEOCOLUMN     = "geoColumn";
	protected static final String COLUMNS       = "columns";
	protected static final String WHERE         = "where";
	protected static final String DEFAULTCRS    = "defaultCRS";
	protected static final String SINGLE_RESULT = "singleResult";

	private static final String EPSG = "EPSG:";
	
	protected ConnectionFactory           connectionFactory;
	protected ObjectMapper                mapper;
	protected AtomicReference<Connection> connectionReference;
	
	protected static DatabaseConnection instance;
	
	protected DatabaseConnection(ObjectMapper mapper) {
		
		this.mapper = mapper;
	}
	
	
	
    public Flux<JsonNode> getFlux(GeoPipResponseFormat format, String sql, String[] columns, boolean singleResult,
            int defaultCrs, long repeatTimes, long pollingInterval, boolean latitudeFirst) {
        var connection = createConnection();

        if (singleResult) {

            sql = sql.concat(" LIMIT 1");
            return poll(getResults(connection, sql, columns, format, defaultCrs, latitudeFirst).next(), repeatTimes, pollingInterval);
        } else {

            return poll(collectAndMapResults(getResults(connection, sql, columns, format, defaultCrs, latitudeFirst)), repeatTimes,
                    pollingInterval);
        }

    }

    protected Mono<? extends Connection> createConnection() {
        connectionReference = new AtomicReference<>();
        return Mono.from(connectionFactory.create()).doOnNext(connectionReference::set);
    }

    protected Flux<JsonNode> getResults(Mono<? extends Connection> connection, String sql, String[] columns,
            GeoPipResponseFormat format, int defaultCrs, boolean latitudeFirst) {

        return connection.flatMapMany(conn -> Flux.from(conn.createStatement(sql).execute())
                .flatMap(result -> result.map((row, rowMetadata) -> mapResult(row, columns, format, defaultCrs, latitudeFirst))));

    }

    protected JsonNode mapResult(Row row, String[] columns, GeoPipResponseFormat format, int defaultCrs, boolean latitudeFirst) {
        ObjectNode resultNode = mapper.createObjectNode();
        JsonNode   geoNode;

        String  resValue = row.get("res", String.class);
        Integer srid     = row.get("srid", Integer.class);

        if (srid != 0) {
            geoNode = convertResponse(resValue, format, srid, latitudeFirst);
        } else {
            geoNode = convertResponse(resValue, format, defaultCrs, latitudeFirst);
        }

        resultNode.put("srid", srid);
        resultNode.set("geo", geoNode);
        for (String c : columns) {

            resultNode.put(c, row.get(c, String.class));
        }
        return resultNode;
    }

    protected JsonNode convertResponse(String in, GeoPipResponseFormat format, int srid, boolean latitudeFirst) {

        JsonNode res = mapper.createObjectNode();
        var crs = EPSG+srid;
        
        try {
        	
            Geometry geo = JsonConverter.geoJsonToGeometry(in, new GeometryFactory(new PrecisionModel(), srid));
        	
	        if(this.getClass() == MySqlConnection.class && !latitudeFirst) {
	        	var geoProjector = new GeoProjector(crs, false, crs, true);
	        	geo = geoProjector.project(geo);
	        }else if (this.getClass() == PostGisConnection.class && latitudeFirst) {
	        	
	        	var geoProjector = new GeoProjector(crs, true, crs, false);
	        	geo = geoProjector.project(geo);
	        }
        
            switch (format) {
            case GEOJSON:

                res = GeometryConverter.geometryToGeoJsonNode(geo).get();
                break;

            case WKT:
                
                res = GeometryConverter.geometryToWKT(geo).get();
                break;

            case GML:
                
                res = GeometryConverter.geometryToGML(geo).get();
                break;

            case KML:
               
                res = GeometryConverter.geometryToKML(geo).get();
                break;

            default:
                break;
            }
            
              
        } catch (Exception e) {
            throw new PolicyEvaluationException(e.getMessage());
        }
        return res;
    }

    
    protected Mono<JsonNode> collectAndMapResults(Flux<JsonNode> resultFlux) {

        return resultFlux.collect(ArrayList::new, List::add).map(results -> {
            ArrayNode arrayNode = mapper.createArrayNode();
            for (var node : results) {
                arrayNode.add((JsonNode) node);
            }
            return (JsonNode) arrayNode;
        });

    }

    protected Flux<JsonNode> poll(Mono<JsonNode> mono, long repeatTimes, long pollingInterval) {
        return mono.onErrorResume(Mono::error)
                .repeatWhen((Repeat.times(repeatTimes - 1).fixedBackoff(Duration.ofMillis(pollingInterval))))
                .doOnCancel(() -> connectionReference.get().close())
                .doOnTerminate(() -> connectionReference.get().close());
    }

    protected static String buildSql(String geoColumn, String[] columns, String table, String where) {
        String frmt = "ST_AsGeoJSON";

        StringBuilder builder = new StringBuilder();
        for (String c : columns) {
            builder.append(String.format(", %s AS %s", c, c));
        }
        String clms = builder.toString();
        String str  = "SELECT %s(%s) AS res, ST_SRID(%s) AS srid%s FROM %s %s";

        return String.format(str, frmt, geoColumn, geoColumn, clms, table, where);
    }

    protected static int getPort(JsonNode requestSettings) throws PolicyEvaluationException {
        if (requestSettings.has(PORT)) {
            return requestSettings.findValue(PORT).asInt();
        } else {

            return 5432;
        }

    }

    protected static String getDataBase(JsonNode requestSettings) throws PolicyEvaluationException {
        if (requestSettings.has(DATABASE)) {
            return requestSettings.findValue(DATABASE).asText();
        } else {
            throw new PolicyEvaluationException("No database-name found");

        }

    }

    protected static String getTable(JsonNode requestSettings) throws PolicyEvaluationException {
        if (requestSettings.has(TABLE)) {
            return requestSettings.findValue(TABLE).asText();
        } else {
            throw new PolicyEvaluationException("No table-name found");

        }

    }

    protected static String getGeoColumn(JsonNode requestSettings) throws PolicyEvaluationException {
        if (requestSettings.has(GEOCOLUMN)) {
            return requestSettings.findValue(GEOCOLUMN).asText();
        } else {
            throw new PolicyEvaluationException("No geoColumn-name found");

        }

    }

    protected static String[] getColumns(JsonNode requestSettings, ObjectMapper mapper) throws PolicyEvaluationException {
        if (requestSettings.has(COLUMNS)) {
            var columns = requestSettings.findValue(COLUMNS);
            if (columns.isArray()) {

                return mapper.convertValue((ArrayNode) columns, String[].class);
            }

            return new String[] { columns.asText() };
        } else {
            return new String[] {};

        }

    }

    protected static String getWhere(JsonNode requestSettings) {
        if (requestSettings.has(WHERE)) {
            return String.format("WHERE %s", requestSettings.findValue(WHERE).asText());
        } else {
            return "";
        }

    }

    protected static int getDefaultCRS(JsonNode requestSettings) {
        if (requestSettings.has(DEFAULTCRS)) {
            return requestSettings.findValue(DEFAULTCRS).asInt();
        } else {
            return 4326;
        }

    }

    protected static boolean getSingleResult(JsonNode requestSettings) {
        if (requestSettings.has(SINGLE_RESULT)) {
            return requestSettings.findValue(SINGLE_RESULT).asBoolean();
        } else {
            return false;
        }

    }

	
}
