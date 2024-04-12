package io.sapl.geo.connection.mysql;

import java.time.ZoneId;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.asyncer.r2dbc.mysql.MySqlConnectionConfiguration;
import io.asyncer.r2dbc.mysql.MySqlConnectionFactory;
import io.r2dbc.spi.ConnectionFactory;
import io.sapl.api.interpreter.Val;
import io.sapl.geo.connection.postgis.PostGisConnection;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@TestInstance(Lifecycle.PER_CLASS)
@Testcontainers
public class MySqlConnectionTests {

	String template = """
            {
            "user":"%s",
            "password":"%s",
        	"server":"%s",
        	"port": %s,
        	"dataBase":"%s",
   "responseFormat":"GEOJSON",
        	"defaultCRS": 4326,
        	"pollingIntervalMs":1000,
        	"repetitions":2
        """;

	@Container
	private static final MySQLContainer<?> mySqlContainer = new MySQLContainer<>(
	   DockerImageName.parse("mysql:8.2.0")) //8.3.0 is buggy
	   .withUsername("test").withPassword("test").withDatabaseName("test");
	
	@BeforeAll
	public void setUp() throws Exception {
	
	template = String.format(template, mySqlContainer.getUsername(), mySqlContainer.getPassword(),
			mySqlContainer.getHost(), mySqlContainer.getMappedPort(3306), mySqlContainer.getDatabaseName());
	
	var connectionFactory = MySqlConnectionFactory.from(
        	MySqlConnectionConfiguration.builder()
            .username(mySqlContainer.getUsername())
            .password(mySqlContainer.getPassword())
            .host(mySqlContainer.getHost())
            .port(mySqlContainer.getMappedPort(3306))
            .database(mySqlContainer.getDatabaseName())
            .serverZoneId(ZoneId.of("UTC"))
            .build());
 
	createTable(connectionFactory);
	insert(connectionFactory);
	}

	@Test
    public void Test01MySqlConnection() throws JsonProcessingException {

        var tmp = template.concat("""
                    ,
                    "table":"%s",
                    "geoColumn":"%s",
                	"singleResult": false,
                	"columns": ["name"]
                }
                """);
        var str = String.format(tmp, "geometries", "geom");

        var exp = Val.ofJson(
                "[{\"srid\":4326,\"geo\":{\"type\":\"Point\",\"coordinates\":[1,1],\"crs\":{\"type\":\"name\",\"properties\":{\"name\":\"EPSG:4326\"}}},\"name\":\"point\"},{\"srid\":4326,\"geo\":{\"type\":\"Polygon\",\"coordinates\":[[[0.0,0.0],[0.0,1],[1,1],[1,0.0],[0.0,0.0]]],\"crs\":{\"type\":\"name\",\"properties\":{\"name\":\"EPSG:4326\"}}},\"name\":\"polygon\"}]");

        var mysql = MySqlConnection.connect(Val.ofJson(str).get(), new ObjectMapper());
        //mysql.doOnNext(x -> System.out.println(x.get().toString())).subscribe();
        StepVerifier.create(mysql).expectNext(exp).expectNext(exp).verifyComplete();
    }

    @Test
    public void Test02MySqlConnectionSingleResult() throws JsonProcessingException {

        var tmp = template.concat("""
                    ,
                    "table":"%s",
                    "geoColumn":"%s",
                	"singleResult": true,
                	"columns": ["name"],
                	"where": "name = 'point'"
                }
                """);
        var str = String.format(tmp, "geometries", "geom");

        var exp = Val.ofJson(
                "{\"srid\":4326,\"geo\":{\"type\":\"Point\",\"coordinates\":[1,1],\"crs\":{\"type\":\"name\",\"properties\":{\"name\":\"EPSG:4326\"}}},\"name\":\"point\"}");

        var mysql = MySqlConnection.connect(Val.ofJson(str).get(), new ObjectMapper());
        StepVerifier.create(mysql).expectNext(exp).expectNext(exp).verifyComplete();
    }

    
    @Test
    public void Test03Error() throws JsonProcessingException {

        var tmp = template.concat("""
                    ,
                    "table":"%s",
                    "geoColumn":"%s",
                	"singleResult": true,
                	"columns": ["name", "text"],
                	"where": "name = 'point'"
                }
                """);
        var str = String.format(tmp, "nonExistant", "geog");

        var mysql = MySqlConnection.connect(Val.ofJson(str).get(), new ObjectMapper());
        StepVerifier.create(mysql).expectError();
    }

    private void createTable(ConnectionFactory connectionFactory) {
        Mono.from(connectionFactory.create()).flatMap(connection -> {
            String createTableQuery = "CREATE TABLE geometries (id SERIAL PRIMARY KEY, geom GEOMETRY, name CHARACTER VARYING(25), text CHARACTER VARYING(25) );";

            return Mono.from(connection.createStatement(createTableQuery).execute());
        }).block();

        Mono.from(connectionFactory.create()).flatMap(connection -> {
            String createTableQuery = "CREATE TABLE geographies (id SERIAL PRIMARY KEY, geog GEOGRAPHY, name CHARACTER VARYING(25), text CHARACTER VARYING(25) );";

            return Mono.from(connection.createStatement(createTableQuery).execute());
        }).block();
    }

    private void insert(ConnectionFactory connectionFactory) {

        Mono.from(connectionFactory.create()).flatMap(connection -> {
            String insertPointQuery = "INSERT INTO geometries VALUES (1, ST_GeomFromText('POINT(1 1)', 4326), 'point', 'text point'), (2, ST_GeomFromText('POLYGON((0 0, 1 0, 1 1, 0 1, 0 0))', 4326), 'polygon', 'text polygon');";

            return Mono.from(connection.createStatement(insertPointQuery).execute());
        }).block();

        Mono.from(connectionFactory.create()).flatMap(connection -> {
            String insertPointQuery = "INSERT INTO geographies VALUES (1, ST_GeogFromText('SRID=4326; POINT(1 1)'), 'point', 'text point'), (2, ST_GeogFromText('SRID=4326; POLYGON((0 0, 1 0, 1 1, 0 1, 0 0))'), 'polygon', 'text polygon');";

            return Mono.from(connection.createStatement(insertPointQuery).execute());
        }).block();

    }

}

