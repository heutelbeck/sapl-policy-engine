/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

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
		return Paths.get(Objects.requireNonNullElse(saplSpecificOutputDir, "target")).resolve("sapl-coverage");

		// else use standard maven build dir
	}

}
