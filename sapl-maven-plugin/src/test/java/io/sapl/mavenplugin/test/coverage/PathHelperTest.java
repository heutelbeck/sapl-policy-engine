/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.mavenplugin.test.coverage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;

import org.apache.maven.plugin.logging.Log;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

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
