/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package io.sapl.geo.connection.mysql;

import io.sapl.geo.connection.shared.ConnectionBase;
import java.time.Duration;
import java.time.ZoneId;
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

import io.asyncer.r2dbc.mysql.MySqlConnectionConfiguration;
import io.asyncer.r2dbc.mysql.MySqlConnectionFactory;
import io.asyncer.r2dbc.mysql.MySqlConnectionFactoryProvider;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.Row;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;

import io.sapl.geo.functions.GeometryConverter;
import io.sapl.geo.functions.JsonConverter;
import io.sapl.geo.pip.GeoPipResponseFormat;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.retry.Repeat;

public class MySqlConnection extends ConnectionBase {

	private static final String PORT          = "port";
    private static final String DATABASE      = "dataBase";
    private static final String TABLE         = "table";
    private static final String GEOCOLUMN     = "geoColumn";
    private static final String COLUMNS       = "columns";
    private static final String WHERE         = "where";
    private static final String DEFAULTCRS    = "defaultCRS";
    private static final String SINGLE_RESULT = "singleResult";

    private ConnectionFactory           connectionFactory;
    private ObjectMapper                mapper;
    private AtomicReference<Connection> connectionReference;

    private MySqlConnection(String user, String password, String serverName, int port, String dataBase,
            ObjectMapper mapper) {
        this.mapper = mapper;
//        connectionFactory = ConnectionFactories.get(
//                ConnectionFactoryOptions.builder()
//                		.option(ConnectionFactoryOptions.DRIVER, "mysql")
//                        .option(ConnectionFactoryOptions.USER, user)
//                        .option(ConnectionFactoryOptions.PASSWORD, password)
//                        .option(ConnectionFactoryOptions.HOST, serverName)
//                        .option(ConnectionFactoryOptions.PORT, port)
//                        .option(ConnectionFactoryOptions.DATABASE, dataBase)
//                        .build());
        connectionFactory = MySqlConnectionFactory.from(
                	MySqlConnectionConfiguration.builder()
                        .username(user)
                        .password(password)
                        .host(serverName)
                        .port(port)
                        .database(dataBase)
                        .serverZoneId(ZoneId.of("UTC"))
                        .build());


    }

    public static MySqlConnection getNew(String user, String password, String server, int port, String dataBase,
            ObjectMapper mapper) {

        return new MySqlConnection(user, password, server, port, dataBase, mapper);
    }

    public static Flux<Val> connect(JsonNode settings, ObjectMapper mapper) {

        try {
            var connection = getNew(getUser(settings), getPassword(settings), getServer(settings), getPort(settings),
                    getDataBase(settings), mapper);
            var columns    = getColumns(settings, mapper);
            return connection
                    .getFlux(getResponseFormat(settings, mapper),
                            buildSql(getGeoColumn(settings), columns, getTable(settings), getWhere(settings)), columns,
                            getSingleResult(settings), getDefaultCRS(settings),
                            longOrDefault(settings, REPEAT_TIMES, DEFAULT_REPETITIONS),
                            longOrDefault(settings, POLLING_INTERVAL, DEFAULT_POLLING_INTERVALL_MS))
                    .map(Val::of).onErrorResume(e -> Flux.just(Val.error(e)));

        } catch (Exception e) {
            return Flux.just(Val.error(e));
        }

    }

    public Flux<JsonNode> getFlux(GeoPipResponseFormat format, String sql, String[] columns, boolean singleResult,
            int defaultCrs, long repeatTimes, long pollingInterval) {
        var connection = createConnection();

        if (singleResult) {

            sql = sql.concat(" LIMIT 1");
            return poll(getResults(connection, sql, columns, format, defaultCrs).next(), repeatTimes, pollingInterval);
        } else {

            return poll(collectAndMapResults(getResults(connection, sql, columns, format, defaultCrs)), repeatTimes,
                    pollingInterval);
        }

    }

    private Mono<? extends Connection> createConnection() {
        connectionReference = new AtomicReference<>();
        return Mono.from(connectionFactory.create()).doOnNext(connectionReference::set);
    }

    private Flux<JsonNode> getResults(Mono<? extends Connection> connection, String sql, String[] columns,
            GeoPipResponseFormat format, int defaultCrs) {

        return connection.flatMapMany(conn -> Flux.from(conn.createStatement(sql).execute())
                .flatMap(result -> result.map((row, rowMetadata) -> mapResult(row, columns, format, defaultCrs))));

    }

    private JsonNode mapResult(Row row, String[] columns, GeoPipResponseFormat format, int defaultCrs) {
        ObjectNode resultNode = mapper.createObjectNode();
        JsonNode   geoNode;

        String  resValue = row.get("res", String.class);
        Integer srid     = row.get("srid", Integer.class);

        if (srid != 0) {
            geoNode = convertResponse(resValue, format, srid);
        } else {
            geoNode = convertResponse(resValue, format, defaultCrs);
        }

        resultNode.put("srid", srid);
        resultNode.set("geo", geoNode);
        for (String c : columns) {

            resultNode.put(c, row.get(c, String.class));
        }
        return resultNode;
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

    private Mono<JsonNode> collectAndMapResults(Flux<JsonNode> resultFlux) {

        return resultFlux.collect(ArrayList::new, List::add).map(results -> {
            ArrayNode arrayNode = mapper.createArrayNode();
            for (var node : results) {
                arrayNode.add((JsonNode) node);
            }
            return (JsonNode) arrayNode;
        });

    }

    private Flux<JsonNode> poll(Mono<JsonNode> mono, long repeatTimes, long pollingInterval) {
        return mono.onErrorResume(Mono::error)
                .repeatWhen((Repeat.times(repeatTimes - 1).fixedBackoff(Duration.ofMillis(pollingInterval))))
                .doOnCancel(() -> connectionReference.get().close())
                .doOnTerminate(() -> connectionReference.get().close());
    }

    private static String buildSql(String geoColumn, String[] columns, String table, String where) {
        String frmt = "ST_AsGeoJSON";

        StringBuilder builder = new StringBuilder();
        for (String c : columns) {
            builder.append(String.format(", %s AS %s", c, c));
        }
        String clms = builder.toString();
        String str  = "SELECT %s(%s) AS res, ST_SRID(%s) AS srid%s FROM %s %s";

        return String.format(str, frmt, geoColumn, geoColumn, clms, table, where);
    }

    private static int getPort(JsonNode requestSettings) throws PolicyEvaluationException {
        if (requestSettings.has(PORT)) {
            return requestSettings.findValue(PORT).asInt();
        } else {

            return 5432;
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

    private static String getGeoColumn(JsonNode requestSettings) throws PolicyEvaluationException {
        if (requestSettings.has(GEOCOLUMN)) {
            return requestSettings.findValue(GEOCOLUMN).asText();
        } else {
            throw new PolicyEvaluationException("No geoColumn-name found");

        }

    }

    private static String[] getColumns(JsonNode requestSettings, ObjectMapper mapper) throws PolicyEvaluationException {
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

    private static boolean getSingleResult(JsonNode requestSettings) {
        if (requestSettings.has(SINGLE_RESULT)) {
            return requestSettings.findValue(SINGLE_RESULT).asBoolean();
        } else {
            return false;
        }

    }

}

