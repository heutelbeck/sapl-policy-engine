package io.sapl.mavenplugin.test.coverage;

import org.apache.maven.plugin.logging.Log;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PathHelperTest {
    private Log log;
    
    @BeforeEach
    void setup() {
        log = Mockito.mock(Log.class);
    }
    
    @Test
    public void test_customConfigBaseDir() {
        
        String configBaseDir = "test";
        String projectBuildDir = "target";

        Path expectedPath = Path.of("test", "sapl-coverage");
        
        Path result = PathHelper.resolveBaseDir(configBaseDir, projectBuildDir, this.log);
        
        assertEquals(expectedPath, result);
    }

    @Test
    public void test_customConfigBaseDir_Empty() {

        String configBaseDir = "";
        String projectBuildDir = "target";

        Path expectedPath = Path.of("target", "sapl-coverage");

        Path result = PathHelper.resolveBaseDir(configBaseDir, projectBuildDir, this.log);

        assertEquals(expectedPath, result);
    }

    @Test
    public void test_customConfigBaseDir_Null() {

        String projectBuildDir = "target";

        Path expectedPath = Path.of("target", "sapl-coverage");

        Path result = PathHelper.resolveBaseDir(null, projectBuildDir, this.log);

        assertEquals(expectedPath, result);
    }

}
