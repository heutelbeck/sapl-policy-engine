package io.sapl.mavenplugin.test.coverage;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class EnableCoverageCollectionMojoTest extends AbstractMojoTestCase {

	private Log log;
	
	@BeforeEach
	void setup() throws Exception {
		super.setUp();
		
		log = Mockito.mock(Log.class);
	}
	
	@Test
	@Disabled
	void test_disableCoverage() throws Exception {

		Path pom = Paths.get("src", "test", "resources", "pom", "pom_withoutProject_coverageDisabled.xml");
		var mojo = (EnableCoverageCollectionMojo) lookupMojo("enable-coverage-collection", pom.toFile());
		mojo.setLog(this.log);
		
		assertDoesNotThrow(() -> mojo.execute());
	}
	
	@Test
	@Disabled
	void test() throws Exception {
		Path pom = Paths.get("src", "test", "resources", "pom", "pom_withoutProject.xml");
		var mojo = (EnableCoverageCollectionMojo) lookupMojo("enable-coverage-collection", pom.toFile());
		mojo.setLog(this.log);
		
	    try (MockedStatic<PathHelper> pathHelper = Mockito.mockStatic(PathHelper.class)) {
	    	pathHelper.when(() -> PathHelper.resolveBaseDir(any(), any(), log)).thenReturn(Paths.get("tmp"));
			assertDoesNotThrow(() -> mojo.execute());
	    }
	}

}
