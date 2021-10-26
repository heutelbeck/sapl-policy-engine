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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.plugin.logging.Log;

public class PathHelper {

	static Path resolveBaseDir(String configBaseDir, String projectBuildDir, Log log) {
		if (configBaseDir != null && !configBaseDir.isEmpty()) {
			// apply configured basedir if it is set
			log.debug(String.format("Using \"%s\" as base dir for sapl coverage", configBaseDir));
			return Paths.get(configBaseDir).resolve("sapl-coverage");
		}
		else {
			// if not use the maven project build output dir
			log.debug(String.format("Using \"%s\" as base dir for sapl coverage", projectBuildDir));
			return Paths.get(projectBuildDir).resolve("sapl-coverage");
		}
	}

	public static void createFile(Path filePath) throws IOException {
		if (!Files.exists(filePath)) {
			Path parent = filePath.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			Files.createFile(filePath);
		}

	}

	public static void createParentDirs(Path filePath) throws IOException {
		if (!Files.exists(filePath)) {
			Path parent = filePath.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
		}
	}

}
