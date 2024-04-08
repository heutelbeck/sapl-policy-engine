package io.sapl.geo.connection.owntracks;

import static org.junit.Assert.assertEquals;

import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.ObjectMapper;

import common.SourceProvider;
import io.sapl.api.interpreter.Val;

@Testcontainers
@TestInstance(Lifecycle.PER_CLASS)
public class OwnTracksConnectionTests {
	String              address;
    Integer             port;
    SourceProvider      source            = SourceProvider.getInstance();
    final static String resourceDirectory = Paths.get("src", "test", "resources").toFile().getAbsolutePath();

    @Container

    public static GenericContainer<?> owntracksRecorder = new GenericContainer<>(
            DockerImageName.parse("owntracks/recorder:latest")).withExposedPorts(8083)
            .withFileSystemBind(resourceDirectory + "/owntracks/store", "/store", BindMode.READ_WRITE)
            .withEnv("OTR_PORT", "0")
//            .withFileSystemBind(resourceDirectory + "/opt/traccar/data", "/opt/traccar/data", BindMode.READ_WRITE)
            .withReuse(false);

    @BeforeAll
    void setup() {

    	
    	var a = owntracksRecorder.getHost();
    	var b = owntracksRecorder.getMappedPort(8083);
        address = owntracksRecorder.getHost() + ":" + owntracksRecorder.getMappedPort(8083);
    }
    
    @Test
    void test() throws Exception {
        var exp = "{\"deviceId\":1,\"position\":{\"type\":\"Point\",\"coordinates\":[40,10],\"crs\":{\"type\":\"name\",\"properties\":{\"name\":\"EPSG:4326\"}}},\"altitude\":409.0,\"lastUpdate\":\"1712477273\",\"accuracy\":20.0,\"geoFences\":[{\"name\":\"home\"},{\"name\":\"home2\"}]}";

        var st = """
                {
                "user":"user",
            	"server":"%s",
            	"protocol":"http",
            	"responseFormat":"GEOJSON",
            	"deviceId":1
            }
            """;
        
        var val = Val.ofJson(String.format(st, address));
        var res = OwnTracksConnection.connect(val.get(), new ObjectMapper()).blockFirst().get().toString();

        assertEquals(exp, res);

    }
    
}
