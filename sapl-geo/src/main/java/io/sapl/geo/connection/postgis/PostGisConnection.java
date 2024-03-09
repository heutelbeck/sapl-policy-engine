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

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.geo.connection.shared.ConnectionBase;
import io.sapl.geo.pip.GeoPipResponseFormat;
import io.sapl.geofunctions.GeometryConverter;
import io.sapl.geofunctions.JsonConverter;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.retry.Repeat;

public class PostGisConnection extends ConnectionBase {

    private static final String PORT             = "port";
    private static final String DATABASE         = "dataBase";
    private static final String TABLE            = "table";
    private static final String COLUMN           = "column";
    private static final String WHERE            = "where";
    private static final String DEFAULTCRS       = "defaultCRS";
    static final String         POLLING_INTERVAL = "pollingIntervalMs";
    static final String         REPEAT_TIMES     = "repetitions";

    private static final long DEFAULT_POLLING_INTERVALL_MS = 1000L;
    private static final long DEFAULT_REPETITIONS          = Long.MAX_VALUE;

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

    public static Flux<Val> connect(JsonNode settings, ObjectMapper mapper) {

        try {
            var connection = getNew(getUser(settings), getPassword(settings), getServer(settings), getPort(settings),
                    getDataBase(settings), mapper);
            return connection
                    .getFlux(getResponseFormat(settings, mapper), getTable(settings), getColumn(settings),
                            getWhere(settings), getDefaultCRS(settings),
                            longOrDefault(settings, REPEAT_TIMES, DEFAULT_REPETITIONS),
                            longOrDefault(settings, POLLING_INTERVAL, DEFAULT_POLLING_INTERVALL_MS))
                    .map(Val::of).onErrorResume(e -> Flux.just(Val.error(e)));

        } catch (Exception e) {
            return Flux.just(Val.error(e));
        }

    }

    public Flux<ObjectNode> getFlux(GeoPipResponseFormat format, String table, String column, String where,
            int defaultCrs, long repeatTimes, long pollingInterval) {

        String frmt = "ST_AsGeoJSON";

        String str                 = "SELECT %s(%s) AS res, ST_SRID(%s) AS srid FROM %s %s";
        var    sql                 = String.format(str, frmt, column, column, table, where);
        var    connectionReference = new AtomicReference<Connection>();
        return Mono.from(connectionFactory.create()).doOnNext(connectionReference::set)
                .flatMapMany(connection -> Flux.from(connection.createStatement(sql).execute())

                        .flatMap(result -> result.map((row, rowMetadata) -> {
                            String resValue = row.get("res", String.class);
                            Integer srid = row.get("srid", Integer.class);
                            JsonNode geoNode;
                            if (srid != 0) {
                                geoNode = convertResponse(resValue, format, srid);
                            } else {
                                geoNode = convertResponse(resValue, format, defaultCrs);
                            }
                            ObjectNode resultNode = mapper.createObjectNode();
                            resultNode.put("srid", srid);
                            resultNode.set("geo", geoNode);
                            return resultNode;
                        })))
                .collect(ArrayList::new, List::add).map(results -> {
                    ObjectNode combinedNode = mapper.createObjectNode();
                    ArrayNode arrayNode = mapper.createArrayNode();
                    for (var node : results) {
                        arrayNode.add((JsonNode) node);
                    }
                    combinedNode.set("results", arrayNode);
                    return combinedNode;
                }).doOnNext(node -> System.out.println("Result from DB: " + node.toString()))
                .onErrorResume(Mono::error)
                .repeatWhen((Repeat.times(repeatTimes - 1).fixedBackoff(Duration.ofMillis(pollingInterval))))
                .doOnCancel(() -> connectionReference.get().close())
                .doOnTerminate(() -> connectionReference.get().close());

    }

    private JsonNode convertResponse(String in, GeoPipResponseFormat format, int srid) {

        Geometry geo;
        JsonNode res = mapper.createObjectNode();

        try {
            switch (format) {
            case GEOJSON:

                geo = JsonConverter.geoJsonToGeometry(in, new GeometryFactory(new PrecisionModel(), srid));
                res = GeometryConverter.geometryToGeoJsonNode(geo).get();
                break;

            case WKT:
                geo = JsonConverter.geoJsonToGeometry(in, new GeometryFactory(new PrecisionModel(), srid));
                res = GeometryConverter.geometryToWKT(geo).get();
                break;

            case GML:
                geo = JsonConverter.geoJsonToGeometry(in, new GeometryFactory(new PrecisionModel(), srid));
                res = GeometryConverter.geometryToGML(geo).get();
                break;

            case KML:
                geo = JsonConverter.geoJsonToGeometry(in, new GeometryFactory(new PrecisionModel(), srid));
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

    private static String getWhere(JsonNode requestSettings) {
        if (requestSettings.has(WHERE)) {
            return String.format("WHERE %s", requestSettings.findValue(WHERE).asText());
        } else {
            return "";
        }

    }

    private static int getDefaultCRS(JsonNode requestSettings) {
        if (requestSettings.has(DEFAULTCRS)) {
            return requestSettings.findValue(DEFAULTCRS).asInt();
        } else {
            return 4326;
        }

    }

    private static long longOrDefault(JsonNode requestSettings, String fieldName, long defaultValue) {

        if (requestSettings.has(fieldName)) {
            var value = requestSettings.findValue(fieldName);

            if (!value.isNumber())
                throw new PolicyEvaluationException(fieldName + " must be an integer, but was: " + value.getNodeType());

            return value.asLong();
        }

        return defaultValue;
    }
}
