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

import common.DatabaseTestBase;
import io.asyncer.r2dbc.mysql.MySqlConnectionConfiguration;
import io.asyncer.r2dbc.mysql.MySqlConnectionFactory;
import io.sapl.api.interpreter.Val;

import reactor.test.StepVerifier;

@TestInstance(Lifecycle.PER_CLASS)
@Testcontainers
public class MySqlConnectionTests extends DatabaseTestBase {

	private String tmpAll;
	private String tmpPoint;	
	private String template;
	
	@Container
	private static final MySQLContainer<?> mySqlContainer = new MySQLContainer<>(
	   DockerImageName.parse("mysql:8.2.0")) //8.3.0 is buggy
	   .withUsername("test").withPassword("test").withDatabaseName("test");
	
	@BeforeAll
	public void setUp() throws Exception {
	
		template = String.format(template1, mySqlContainer.getUsername(), mySqlContainer.getPassword(),
				mySqlContainer.getHost(), mySqlContainer.getMappedPort(3306), mySqlContainer.getDatabaseName());
		
		tmpAll = template.concat(tmpAll1);
			
		tmpPoint = template.concat(tmpPoint1);	
		
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


        var str = String.format(tmpAll, "geometries", "geom");

        var exp = Val.ofJson(expAll);
        var mysql = MySqlConnection.connect(Val.ofJson(str).get(), new ObjectMapper());
        StepVerifier.create(mysql).expectNext(exp).expectNext(exp).verifyComplete();
    }

    @Test
    public void Test02MySqlConnectionSingleResult() throws JsonProcessingException {

        
        var str = String.format(tmpPoint, "geometries", "geom");

        var exp = Val.ofJson(expPt);

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


}

