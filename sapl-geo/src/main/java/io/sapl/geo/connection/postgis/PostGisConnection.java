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
package io.sapl.geo.connection.postgis;

import java.io.IOException;
import java.time.Duration;

import javax.xml.parsers.ParserConfigurationException;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.xml.sax.SAXException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.ConnectionFactory;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.geo.connection.shared.ConnectionBase;
import io.sapl.geo.pip.GeoPipResponseFormat;
import io.sapl.geofunctions.GeometryConverter;
import io.sapl.geofunctions.GmlConverter;
import io.sapl.geofunctions.JsonConverter;
import io.sapl.geofunctions.KmlConverter;
import io.sapl.geofunctions.WktConverter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.retry.Repeat;

public class PostGisConnection extends ConnectionBase {

    private static final String PORT     = "port";
    private static final String DATABASE = "dataBase";
    private static final String TABLE    = "table";
    private static final String COLUMN   = "column";

    private ConnectionFactory connectionFactory;
    private ObjectMapper      mapper;

    private PostGisConnection(String user, String password, String serverName, int port, String dataBase,
            ObjectMapper mapper) {
        this.mapper       = mapper;
        connectionFactory = new PostgresqlConnectionFactory(PostgresqlConnectionConfiguration.builder().username(user)
                .password(password).host(serverName).port(port).database(dataBase).build());

    }

    public static PostGisConnection getNew(String user, String password, String server, int port, String dataBase,
            ObjectMapper mapper) {

        return new PostGisConnection(user, password, server, port, dataBase, mapper);
    }

    public static Flux<Val> connectToPostGis(JsonNode settings, ObjectMapper mapper) {

        try {
            var connection = getNew(getUser(settings), getPassword(settings), getServer(settings), getPort(settings),
                    getDataBase(settings), mapper);
            return connection
                    .connect(getResponseFormat(settings, mapper), getTable(settings), getColumn(settings), mapper)
                    .map(Val::of).onErrorResume(e -> {
                        return Flux.just(Val.error(e));
                    });

        } catch (Exception e) {
            return Flux.just(Val.error(e));
        }

    }

    public Flux<ObjectNode> connect(GeoPipResponseFormat format, String table, String column, ObjectMapper mapper) {

        String frmt;
        switch (format) {
        case GEOJSON:
            frmt = "ST_AsGeoJSON";
            break;

        case WKT:
            frmt = "ST_AsText";
            break;

        case GML:
            frmt = "ST_AsGml";
            break;

        case KML:
            frmt = "ST_AsKML";
            break;

        default:
            frmt = "ST_AsText";
            break;
        }

        var str = "SELECT %s(%s) AS res FROM %s";         // WHERE %s";
        var sql = String.format(str, frmt, column, table);

        return Mono.from(connectionFactory.create()).flatMapMany(connection -> {
            return Flux.from(connection.createStatement(sql).execute())
                    .flatMap(result -> result.map((row, rowMetadata) -> row.get("res", String.class)))
                    //.doOnNext(s -> System.out.println(s))
                    .doOnNext(s ->{ 
                    	
                    	var json = convertResponse(s, format);
                    	System.out.println("json: " + json.toString());
                    })
                    
                    .map(s -> {
                        try {

                            var json = JsonNodeFactory.instance.objectNode();
                            ;
                            return (ObjectNode) json;
                        } catch (Exception e) {
                            throw new PolicyEvaluationException(e.getMessage());
                        }
                    }).repeatWhen((Repeat.times(5 - 1).fixedBackoff(Duration.ofMillis(1000))));
        });

        // return Flux.just(123).map(f -> mapper.convertValue(f, ObjectNode.class));
    }

    private JsonNode convertResponse(String in, GeoPipResponseFormat format) {

        Geometry geo;
        JsonNode res = mapper.createObjectNode();
        switch (format) {
        case GEOJSON:
            try {
                geo = JsonConverter.geoJsonToGeometry(in);
                res = GeometryConverter.geometryToGeoJsonNode(geo).get();
                var a = 1;
            } catch (ParseException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            break;

        case WKT:
            try {
                geo = WktConverter.wktToGeometry(Val.of(in));
                res = GeometryConverter.geometryToWKT(geo).get();
                var a = 1;
            } catch (ParseException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            break;

        case GML:
            try {
                geo = GmlConverter.gmlToGeometry(Val.of(in));
                res = GeometryConverter.geometryToGML(geo).get();
                var a = 1;
            } catch (SAXException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (ParserConfigurationException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            break;

        case KML:
            try {
                geo = KmlConverter.kmlToGeometry(Val.of(in));
                res = GeometryConverter.geometryToKML(geo).get();
                var a = 1;
            } catch (ParseException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            break;

        default:

            break;
        }

        return res;
    }

    private static int getPort(JsonNode requestSettings) throws PolicyEvaluationException {
        if (requestSettings.has(PORT)) {
            return requestSettings.findValue(PORT).asInt();
        } else {

            throw new PolicyEvaluationException("No port found");
        }

    }

    private static String getDataBase(JsonNode requestSettings) throws PolicyEvaluationException {
        if (requestSettings.has(DATABASE)) {
            return requestSettings.findValue(DATABASE).asText();
        } else {
            throw new PolicyEvaluationException("No database-name found");

        }

    }

    private static String getTable(JsonNode requestSettings) throws PolicyEvaluationException {
        if (requestSettings.has(TABLE)) {
            return requestSettings.findValue(TABLE).asText();
        } else {
            throw new PolicyEvaluationException("No table-name found");

        }

    }

    private static String getColumn(JsonNode requestSettings) throws PolicyEvaluationException {
        if (requestSettings.has(COLUMN)) {
            return requestSettings.findValue(COLUMN).asText();
        } else {
            throw new PolicyEvaluationException("No column-name found");

        }

    }
}
