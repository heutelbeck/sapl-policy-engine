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
package io.sapl.test.coverage.api;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CoverageAPIFactoryTest {

	@AfterEach
	void cleanup() {
		TestFileHelper.deleteDirectory(Paths.get("target/tmp").toFile());
	}

	@Test
	void test() throws IOException {

		Path hitDir = Paths.get("target/tmp/hits");

		Assertions.assertThat(countFilesInDir(hitDir)).isEqualTo(0);

		CoverageAPIFactory.constructCoverageHitRecorder(Paths.get("target/tmp"));

		Assertions.assertThat(countFilesInDir(hitDir)).isEqualTo(3);
	}

	private int countFilesInDir(Path path) throws IOException {
		int count = 0;
		if (!path.toFile().exists()) {
			return count;
		}
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(path, "*.txt")) {
			Iterator<Path> it = stream.iterator();
			while (it.hasNext()) {
				it.next();
				count++;
			}
		}
		return count;
	}

	@Test
	void test_reader() {
		var object = CoverageAPIFactory.constructCoverageHitReader(Paths.get(""));
		Assertions.assertThat(object).isNotNull();
	}

}
