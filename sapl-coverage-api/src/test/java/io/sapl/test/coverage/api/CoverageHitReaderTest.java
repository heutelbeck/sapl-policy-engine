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
package io.sapl.test.coverage.api;

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

import io.sapl.test.coverage.api.model.PolicyConditionHit;
import io.sapl.test.coverage.api.model.PolicyHit;
import io.sapl.test.coverage.api.model.PolicySetHit;

class CoverageHitReaderTest {

	private Path basedir;

	private CoverageHitReader reader;

	@BeforeEach
	void setup() {
		this.basedir = Paths.get("target").resolve("sapl-coverage");
		this.reader  = new CoverageHitAPIFile(this.basedir);
		this.reader.cleanCoverageHitFiles();
	}

	@AfterEach
	void cleanup() {
		this.reader.cleanCoverageHitFiles();
	}

	@Test
	void testCoverageReading_PolicySets() throws Exception {
		// arrange
		var path = this.basedir.resolve("hits").resolve("_policySetHits.txt");
		if (!Files.exists(path)) {
			var parent = path.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			Files.createFile(path);
		}
		Files.write(path,
				(new PolicySetHit("set1").toString() + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
				StandardOpenOption.APPEND);
		Files.write(path,
				(new PolicySetHit("set2").toString() + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
				StandardOpenOption.APPEND);

		// act
		List<PolicySetHit> resultPolicySetHits = this.reader.readPolicySetHits();

		// assert
		Assertions.assertThat(resultPolicySetHits).hasSize(2);
		Assertions.assertThat(resultPolicySetHits.get(0).getPolicySetId()).isEqualTo("set1");
		Assertions.assertThat(resultPolicySetHits.get(1).getPolicySetId()).isEqualTo("set2");
	}

	@Test
	void testCoverageReading_Polices() throws Exception {
		// arrange
		var path = this.basedir.resolve("hits").resolve("_policyHits.txt");
		if (!Files.exists(path)) {
			var parent = path.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			Files.createFile(path);
		}
		Files.write(path, (new PolicyHit("set1", "policy 1").toString() + System.lineSeparator())
				.getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);
		Files.write(path, (new PolicyHit("set2", "policy 1").toString() + System.lineSeparator())
				.getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);

		// act
		List<PolicyHit> resultPolicyHits = this.reader.readPolicyHits();

		// assert
		Assertions.assertThat(resultPolicyHits).hasSize(2);
		Assertions.assertThat(resultPolicyHits.get(0).getPolicyId()).isEqualTo("policy 1");
		Assertions.assertThat(resultPolicyHits.get(0).getPolicySetId()).isEqualTo("set1");
		Assertions.assertThat(resultPolicyHits.get(1).getPolicyId()).isEqualTo("policy 1");
		Assertions.assertThat(resultPolicyHits.get(1).getPolicySetId()).isEqualTo("set2");
	}

	@Test
	void testCoverageReading_PolicyConditions() throws Exception {
		// arrange
		var hitPath = this.basedir.resolve("hits").resolve("_policyConditionHits.txt");
		if (!Files.exists(hitPath)) {
			var parent = hitPath.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			Files.createFile(hitPath);
		}
		Files.write(hitPath, (new PolicyConditionHit("set1", "policy 1", 0, true).toString() + System.lineSeparator())
				.getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);
		Files.write(hitPath, (new PolicyConditionHit("set2", "policy 1", 0, true).toString() + System.lineSeparator())
				.getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);

		// act
		List<PolicyConditionHit> resultPolicyConditionHits = this.reader.readPolicyConditionHits();

		// assert
		Assertions.assertThat(resultPolicyConditionHits).hasSize(2);
		Assertions.assertThat(resultPolicyConditionHits.get(0).getConditionStatementId()).isZero();
		Assertions.assertThat(resultPolicyConditionHits.get(0).isConditionResult()).isTrue();
		Assertions.assertThat(resultPolicyConditionHits.get(0).getPolicyId()).isEqualTo("policy 1");
		Assertions.assertThat(resultPolicyConditionHits.get(0).getPolicySetId()).isEqualTo("set1");
		;
		Assertions.assertThat(resultPolicyConditionHits.get(1).getConditionStatementId()).isZero();
		Assertions.assertThat(resultPolicyConditionHits.get(1).isConditionResult()).isTrue();
		Assertions.assertThat(resultPolicyConditionHits.get(1).getPolicyId()).isEqualTo("policy 1");
		Assertions.assertThat(resultPolicyConditionHits.get(1).getPolicySetId()).isEqualTo("set2");
	}

	@Test
	void testCoverageReading_PolicySets_FileNotExist() throws Exception {
		// arrange

		// act
		List<PolicySetHit> resultPolicySetHits = this.reader.readPolicySetHits();

		// assert
		Assertions.assertThat(resultPolicySetHits).isEmpty();
	}

}
