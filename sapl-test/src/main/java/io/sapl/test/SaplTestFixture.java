package io.sapl.test;

import java.nio.file.Path;
import java.nio.file.Paths;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.interpreter.InitializationException;
import io.sapl.test.steps.GivenStep;
import io.sapl.test.steps.WhenStep;

public interface SaplTestFixture {
	GivenStep constructTestCaseWithMocks();
	WhenStep constructTestCase();
	

	SaplTestFixture registerPIP(Object pip) throws InitializationException;
	SaplTestFixture registerFunctionLibrary(Object function) throws InitializationException;
	SaplTestFixture registerVariable(String key, JsonNode value);
	
	default Path resolveCoverageBaseDir() {
		// if configured via system property because of custom path or custom maven
		// build dir
		String saplSpecificOutputDir = System.getProperty("io.sapl.test.outputDir");
		if (saplSpecificOutputDir != null) {
			return Paths.get(saplSpecificOutputDir).resolve("sapl-coverage");
		}

		// else use standard maven build dir
		return Paths.get("target").resolve("sapl-coverage");
	}
}
