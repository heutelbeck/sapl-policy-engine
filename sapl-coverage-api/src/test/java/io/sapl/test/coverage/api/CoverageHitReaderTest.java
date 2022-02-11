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

public class CoverageHitReaderTest {

	private Path basedir;

	private CoverageHitReader reader;

	@BeforeEach
	void setup() {
		this.basedir = Paths.get("target").resolve("sapl-coverage");
		this.reader = new CoverageHitAPIFile(this.basedir);
		this.reader.cleanCoverageHitFiles();
	}

	@AfterEach
	void cleanup() {
		this.reader.cleanCoverageHitFiles();
	}

	@Test
	void testCoverageReading_PolicySets() throws Exception {
		// arrange
		Path FILE_PATH_POLICY_SET_HITS = this.basedir.resolve("hits").resolve("_policySetHits.txt");
		if (!Files.exists(FILE_PATH_POLICY_SET_HITS)) {
			if (FILE_PATH_POLICY_SET_HITS.getParent() != null) {
				Files.createDirectories(FILE_PATH_POLICY_SET_HITS.getParent());
			}
			Files.createFile(FILE_PATH_POLICY_SET_HITS);
		}
		Files.write(FILE_PATH_POLICY_SET_HITS,
				(new PolicySetHit("set1").toString() + System.lineSeparator()).getBytes(), StandardOpenOption.APPEND);
		Files.write(FILE_PATH_POLICY_SET_HITS,
				(new PolicySetHit("set2").toString() + System.lineSeparator()).getBytes(), StandardOpenOption.APPEND);

		// act
		List<PolicySetHit> resultPolicySetHits = this.reader.readPolicySetHits();

		// assert
		Assertions.assertThat(resultPolicySetHits.size()).isEqualTo(2);
		Assertions.assertThat(resultPolicySetHits.get(0).getPolicySetId()).isEqualTo("set1");
		Assertions.assertThat(resultPolicySetHits.get(1).getPolicySetId()).isEqualTo("set2");
	}

	@Test
	void testCoverageReading_Policys() throws Exception {
		// arrange
		Path FILE_PATH_POLICY_HITS = this.basedir.resolve("hits").resolve("_policyHits.txt");
		if (!Files.exists(FILE_PATH_POLICY_HITS)) {
			if (FILE_PATH_POLICY_HITS.getParent() != null) {
				Files.createDirectories(FILE_PATH_POLICY_HITS.getParent());
			}
			Files.createFile(FILE_PATH_POLICY_HITS);
		}
		Files.write(FILE_PATH_POLICY_HITS,
				(new PolicyHit("set1", "policy 1").toString() + System.lineSeparator()).getBytes(),
				StandardOpenOption.APPEND);
		Files.write(FILE_PATH_POLICY_HITS,
				(new PolicyHit("set2", "policy 1").toString() + System.lineSeparator()).getBytes(),
				StandardOpenOption.APPEND);

		// act
		List<PolicyHit> resultPolicyHits = this.reader.readPolicyHits();

		// assert
		Assertions.assertThat(resultPolicyHits.size()).isEqualTo(2);
		Assertions.assertThat(resultPolicyHits.get(0).getPolicyId()).isEqualTo("policy 1");
		Assertions.assertThat(resultPolicyHits.get(0).getPolicySetId()).isEqualTo("set1");
		Assertions.assertThat(resultPolicyHits.get(1).getPolicyId()).isEqualTo("policy 1");
		Assertions.assertThat(resultPolicyHits.get(1).getPolicySetId()).isEqualTo("set2");
	}

	@Test
	public void testCoverageReading_PolicyConditions() throws Exception {
		// arrange
		Path FILE_PATH_POLICY_CONDITION_HITS = this.basedir.resolve("hits").resolve("_policyConditionHits.txt");
		if (!Files.exists(FILE_PATH_POLICY_CONDITION_HITS)) {
			if (FILE_PATH_POLICY_CONDITION_HITS.getParent() != null) {
				Files.createDirectories(FILE_PATH_POLICY_CONDITION_HITS.getParent());
			}
			Files.createFile(FILE_PATH_POLICY_CONDITION_HITS);
		}
		Files.write(FILE_PATH_POLICY_CONDITION_HITS,
				(new PolicyConditionHit("set1", "policy 1", 0, true).toString() + System.lineSeparator()).getBytes(),
				StandardOpenOption.APPEND);
		Files.write(FILE_PATH_POLICY_CONDITION_HITS,
				(new PolicyConditionHit("set2", "policy 1", 0, true).toString() + System.lineSeparator()).getBytes(),
				StandardOpenOption.APPEND);

		// act
		List<PolicyConditionHit> resultPolicyConditionHits = this.reader.readPolicyConditionHits();

		// assert
		Assertions.assertThat(resultPolicyConditionHits.size()).isEqualTo(2);
		Assertions.assertThat(resultPolicyConditionHits.get(0).getConditionStatementId()).isEqualTo(0);
		Assertions.assertThat(resultPolicyConditionHits.get(0).isConditionResult()).isEqualTo(true);
		Assertions.assertThat(resultPolicyConditionHits.get(0).getPolicyId()).isEqualTo("policy 1");
		Assertions.assertThat(resultPolicyConditionHits.get(0).getPolicySetId()).isEqualTo("set1");
		;
		Assertions.assertThat(resultPolicyConditionHits.get(1).getConditionStatementId()).isEqualTo(0);
		Assertions.assertThat(resultPolicyConditionHits.get(1).isConditionResult()).isEqualTo(true);
		Assertions.assertThat(resultPolicyConditionHits.get(1).getPolicyId()).isEqualTo("policy 1");
		Assertions.assertThat(resultPolicyConditionHits.get(1).getPolicySetId()).isEqualTo("set2");
	}

	@Test
	public void testCoverageReading_PolicySets_FileNotExist() throws Exception {
		// arrange

		// act
		List<PolicySetHit> resultPolicySetHits = this.reader.readPolicySetHits();

		// assert
		Assertions.assertThat(resultPolicySetHits.size()).isEqualTo(0);
	}

}
