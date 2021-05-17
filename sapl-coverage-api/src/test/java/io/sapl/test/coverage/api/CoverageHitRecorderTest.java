package io.sapl.test.coverage.api;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.sapl.test.coverage.api.model.PolicyConditionHit;
import io.sapl.test.coverage.api.model.PolicyHit;
import io.sapl.test.coverage.api.model.PolicySetHit;

public class CoverageHitRecorderTest {

	private Path basedir;
	private CoverageHitRecorder recorder;

	@BeforeEach
	void setup() {
		this.basedir = Paths.get("target").resolve("sapl-coverage");
		this.recorder = new CoverageHitAPIImpl(this.basedir);
		this.recorder.cleanCoverageHitFiles();
		this.recorder.createCoverageHitFiles();
	}

	@AfterEach
	void cleanup() {
		this.recorder.cleanCoverageHitFiles();
	}

	@Test
	void testCoverageRecording() throws Exception {
		// arrange
		Path FILE_PATH_POLICY_SET_HITS = this.basedir.resolve("hits")
				.resolve("_policySetHits.txt");
		Path FILE_PATH_POLICY_HITS = this.basedir.resolve("hits").resolve("_policyHits.txt");
		Path FILE_PATH_POLICY_CONDITION_HITS = this.basedir.resolve("hits")
				.resolve("_policyConditionHits.txt");

		// act
		this.recorder.recordPolicySetHit(new PolicySetHit("set1"));
		this.recorder.recordPolicyHit(new PolicyHit("set1", "policy11"));
		this.recorder.recordPolicyConditionHit(new PolicyConditionHit("set1", "policy11", 7, true));
		this.recorder.recordPolicyConditionHit(new PolicyConditionHit("set1", "policy11", 8, true));
		this.recorder.recordPolicyConditionHit(new PolicyConditionHit("set1", "policy11", 9, true));
		this.recorder.recordPolicyHit(new PolicyHit("set1", "policy12"));
		this.recorder.recordPolicyConditionHit(new PolicyConditionHit("set1", "policy12", 7, true));
		this.recorder.recordPolicyConditionHit(new PolicyConditionHit("set1", "policy12", 8, true));
		this.recorder.recordPolicyConditionHit(new PolicyConditionHit("set1", "policy12", 9, true));
		this.recorder.recordPolicySetHit(new PolicySetHit("set2"));
		this.recorder.recordPolicyHit(new PolicyHit("set2", "policy21"));
		this.recorder.recordPolicyConditionHit(new PolicyConditionHit("set2", "policy21", 7, true));
		this.recorder.recordPolicyConditionHit(new PolicyConditionHit("set2", "policy21", 8, true));
		this.recorder.recordPolicyConditionHit(new PolicyConditionHit("set2", "policy21", 9, true));
		this.recorder.recordPolicyHit(new PolicyHit("set2", "policy22"));
		this.recorder.recordPolicyConditionHit(new PolicyConditionHit("set2", "policy22", 7, true));
		this.recorder.recordPolicyConditionHit(new PolicyConditionHit("set2", "policy22", 8, true));
		this.recorder.recordPolicyConditionHit(new PolicyConditionHit("set2", "policy22", 9, true));
		this.recorder.recordPolicySetHit(new PolicySetHit("set2"));
		this.recorder.recordPolicyHit(new PolicyHit("set2", "policy21"));
		this.recorder.recordPolicyConditionHit(new PolicyConditionHit("set2", "policy21", 7, true));
		this.recorder.recordPolicyConditionHit(new PolicyConditionHit("set2", "policy21", 8, true));
		this.recorder.recordPolicyConditionHit(new PolicyConditionHit("set2", "policy21", 9, true));
		this.recorder.recordPolicyHit(new PolicyHit("set2", "policy22"));
		this.recorder.recordPolicyConditionHit(new PolicyConditionHit("set2", "policy22", 7, true));
		this.recorder.recordPolicyConditionHit(new PolicyConditionHit("set2", "policy22", 8, true));
		this.recorder.recordPolicyConditionHit(new PolicyConditionHit("set2", "policy22", 9, true));

		// assert
		List<String> resultPolicySetHits = Files.readAllLines(FILE_PATH_POLICY_SET_HITS);
		Assertions.assertThat(resultPolicySetHits.size()).isEqualTo(2);
		Assertions.assertThat(resultPolicySetHits.get(0)).isEqualTo("set1");
		Assertions.assertThat(resultPolicySetHits.get(1)).isEqualTo("set2");

		List<String> resultPolicyHits = Files.readAllLines(FILE_PATH_POLICY_HITS);
		Assertions.assertThat(resultPolicyHits.size()).isEqualTo(4);
		Assertions.assertThat(resultPolicyHits.get(0)).isEqualTo("set1" + CoverageHitConfig.DELIMITER + "policy11");
		Assertions.assertThat(resultPolicyHits.get(1)).isEqualTo("set1" + CoverageHitConfig.DELIMITER + "policy12");
		Assertions.assertThat(resultPolicyHits.get(2)).isEqualTo("set2" + CoverageHitConfig.DELIMITER + "policy21");
		Assertions.assertThat(resultPolicyHits.get(3)).isEqualTo("set2" + CoverageHitConfig.DELIMITER + "policy22");

		List<String> resultPolicyConditionHits = Files.readAllLines(FILE_PATH_POLICY_CONDITION_HITS);
		Assertions.assertThat(resultPolicyConditionHits.size()).isEqualTo(12);
		Assertions.assertThat(resultPolicyConditionHits.get(0)).isEqualTo("set1" + CoverageHitConfig.DELIMITER
				+ "policy11" + CoverageHitConfig.DELIMITER + "7" + CoverageHitConfig.DELIMITER + true);
		Assertions.assertThat(resultPolicyConditionHits.get(1)).isEqualTo("set1" + CoverageHitConfig.DELIMITER
				+ "policy11" + CoverageHitConfig.DELIMITER + "8" + CoverageHitConfig.DELIMITER + true);
		Assertions.assertThat(resultPolicyConditionHits.get(2)).isEqualTo("set1" + CoverageHitConfig.DELIMITER
				+ "policy11" + CoverageHitConfig.DELIMITER + "9" + CoverageHitConfig.DELIMITER + true);
		Assertions.assertThat(resultPolicyConditionHits.get(3)).isEqualTo("set1" + CoverageHitConfig.DELIMITER
				+ "policy12" + CoverageHitConfig.DELIMITER + "7" + CoverageHitConfig.DELIMITER + true);
		Assertions.assertThat(resultPolicyConditionHits.get(4)).isEqualTo("set1" + CoverageHitConfig.DELIMITER
				+ "policy12" + CoverageHitConfig.DELIMITER + "8" + CoverageHitConfig.DELIMITER + true);
		Assertions.assertThat(resultPolicyConditionHits.get(5)).isEqualTo("set1" + CoverageHitConfig.DELIMITER
				+ "policy12" + CoverageHitConfig.DELIMITER + "9" + CoverageHitConfig.DELIMITER + true);
		Assertions.assertThat(resultPolicyConditionHits.get(6)).isEqualTo("set2" + CoverageHitConfig.DELIMITER
				+ "policy21" + CoverageHitConfig.DELIMITER + "7" + CoverageHitConfig.DELIMITER + true);
		Assertions.assertThat(resultPolicyConditionHits.get(7)).isEqualTo("set2" + CoverageHitConfig.DELIMITER
				+ "policy21" + CoverageHitConfig.DELIMITER + "8" + CoverageHitConfig.DELIMITER + true);
		Assertions.assertThat(resultPolicyConditionHits.get(8)).isEqualTo("set2" + CoverageHitConfig.DELIMITER
				+ "policy21" + CoverageHitConfig.DELIMITER + "9" + CoverageHitConfig.DELIMITER + true);
		Assertions.assertThat(resultPolicyConditionHits.get(9)).isEqualTo("set2" + CoverageHitConfig.DELIMITER
				+ "policy22" + CoverageHitConfig.DELIMITER + "7" + CoverageHitConfig.DELIMITER + true);
		Assertions.assertThat(resultPolicyConditionHits.get(10)).isEqualTo("set2" + CoverageHitConfig.DELIMITER
				+ "policy22" + CoverageHitConfig.DELIMITER + "8" + CoverageHitConfig.DELIMITER + true);
		Assertions.assertThat(resultPolicyConditionHits.get(11)).isEqualTo("set2" + CoverageHitConfig.DELIMITER
				+ "policy22" + CoverageHitConfig.DELIMITER + "9" + CoverageHitConfig.DELIMITER + true);
	}

	@Test
	void testCoverageWriting_FileNotExist() throws Exception {
		// arrange
		// simulate something deletes expected files during runtime
		this.recorder.cleanCoverageHitFiles();
		// act
		this.recorder.recordPolicySetHit(new PolicySetHit("set"));
		// assert
		Assertions.assertThatNoException();
	}
}
