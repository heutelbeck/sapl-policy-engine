/*
 * Copyright © 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.sapl.test.coverage.api.model.PolicySetHit;

class CoverageCustomConfigTest {

	private Path basedir;

	private CoverageHitReader reader;

	private CoverageHitRecorder recorder;

	@BeforeEach
	void setup() {
		basedir       = Paths.get("temp").resolve("sapl-coverage");
		this.reader   = new CoverageHitAPIFile(basedir);
		this.recorder = new CoverageHitAPIFile(basedir);
	}

	@AfterEach
	void cleanup() {
		TestFileHelper.deleteDirectory(basedir.toFile());
	}

	@Test
	void testSystemPropertyConfig_Reader() throws IOException {
		Path path = this.basedir.resolve("hits").resolve("_policySetHits.txt");
		if (!Files.exists(path)) {
			var parent = path.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			Files.createFile(path);
		}
		Files.write(path,
				(new PolicySetHit("set1") + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
				StandardOpenOption.APPEND);

		// act
		List<PolicySetHit> resultPolicySetHits = reader.readPolicySetHits();

		// assert
		Assertions.assertThat(resultPolicySetHits).hasSize(1);
		Assertions.assertThat(resultPolicySetHits.get(0).getPolicySetId()).isEqualTo("set1");
	}

	@Test
	void testSystemPropertyConfig_Recorder() {
		// act
		recorder.createCoverageHitFiles();

		// assert
		Path path = this.basedir.resolve("hits").resolve("_policySetHits.txt");
		Assertions.assertThat(Files.exists(path)).isTrue();

	}

}
