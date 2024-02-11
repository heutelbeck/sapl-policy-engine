/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
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
import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.sapl.test.coverage.api.model.PolicyConditionHit;
import io.sapl.test.coverage.api.model.PolicyHit;
import io.sapl.test.coverage.api.model.PolicySetHit;

class CoverageHitRecorderTests {

    @Test
    void testCoverageRecording(@TempDir Path tempDir) throws Exception {
        var recorder = new CoverageHitAPIFile(tempDir);
        // arrange
        Path FILE_PATH_POLICY_SET_HITS       = tempDir.resolve("hits").resolve("_policySetHits.txt");
        Path FILE_PATH_POLICY_HITS           = tempDir.resolve("hits").resolve("_policyHits.txt");
        Path FILE_PATH_POLICY_CONDITION_HITS = tempDir.resolve("hits").resolve("_policyConditionHits.txt");

        // act
        recorder.recordPolicySetHit(new PolicySetHit("set1"));
        recorder.recordPolicyHit(new PolicyHit("set1", "policy11"));
        recorder.recordPolicyConditionHit(new PolicyConditionHit("set1", "policy11", 7, true));
        recorder.recordPolicyConditionHit(new PolicyConditionHit("set1", "policy11", 8, true));
        recorder.recordPolicyConditionHit(new PolicyConditionHit("set1", "policy11", 9, true));
        recorder.recordPolicyHit(new PolicyHit("set1", "policy12"));
        recorder.recordPolicyConditionHit(new PolicyConditionHit("set1", "policy12", 7, true));
        recorder.recordPolicyConditionHit(new PolicyConditionHit("set1", "policy12", 8, true));
        recorder.recordPolicyConditionHit(new PolicyConditionHit("set1", "policy12", 9, true));
        recorder.recordPolicySetHit(new PolicySetHit("set2"));
        recorder.recordPolicyHit(new PolicyHit("set2", "policy21"));
        recorder.recordPolicyConditionHit(new PolicyConditionHit("set2", "policy21", 7, true));
        recorder.recordPolicyConditionHit(new PolicyConditionHit("set2", "policy21", 8, true));
        recorder.recordPolicyConditionHit(new PolicyConditionHit("set2", "policy21", 9, true));
        recorder.recordPolicyHit(new PolicyHit("set2", "policy22"));
        recorder.recordPolicyConditionHit(new PolicyConditionHit("set2", "policy22", 7, true));
        recorder.recordPolicyConditionHit(new PolicyConditionHit("set2", "policy22", 8, true));
        recorder.recordPolicyConditionHit(new PolicyConditionHit("set2", "policy22", 9, true));
        recorder.recordPolicySetHit(new PolicySetHit("set2"));
        recorder.recordPolicyHit(new PolicyHit("set2", "policy21"));
        recorder.recordPolicyConditionHit(new PolicyConditionHit("set2", "policy21", 7, true));
        recorder.recordPolicyConditionHit(new PolicyConditionHit("set2", "policy21", 8, true));
        recorder.recordPolicyConditionHit(new PolicyConditionHit("set2", "policy21", 9, true));
        recorder.recordPolicyHit(new PolicyHit("set2", "policy22"));
        recorder.recordPolicyConditionHit(new PolicyConditionHit("set2", "policy22", 7, true));
        recorder.recordPolicyConditionHit(new PolicyConditionHit("set2", "policy22", 8, true));
        recorder.recordPolicyConditionHit(new PolicyConditionHit("set2", "policy22", 9, true));

        // assert
        List<String> resultPolicySetHits = Files.readAllLines(FILE_PATH_POLICY_SET_HITS);
        Assertions.assertThat(resultPolicySetHits).hasSize(2);
        Assertions.assertThat(resultPolicySetHits.get(0)).isEqualTo("set1");
        Assertions.assertThat(resultPolicySetHits.get(1)).isEqualTo("set2");

        List<String> resultPolicyHits = Files.readAllLines(FILE_PATH_POLICY_HITS);
        Assertions.assertThat(resultPolicyHits).hasSize(4);
        Assertions.assertThat(resultPolicyHits.get(0)).isEqualTo("set1" + CoverageHitConstants.DELIMITER + "policy11");
        Assertions.assertThat(resultPolicyHits.get(1)).isEqualTo("set1" + CoverageHitConstants.DELIMITER + "policy12");
        Assertions.assertThat(resultPolicyHits.get(2)).isEqualTo("set2" + CoverageHitConstants.DELIMITER + "policy21");
        Assertions.assertThat(resultPolicyHits.get(3)).isEqualTo("set2" + CoverageHitConstants.DELIMITER + "policy22");

        List<String> resultPolicyConditionHits = Files.readAllLines(FILE_PATH_POLICY_CONDITION_HITS);
        Assertions.assertThat(resultPolicyConditionHits).hasSize(12);
        Assertions.assertThat(resultPolicyConditionHits.get(0)).isEqualTo("set1" + CoverageHitConstants.DELIMITER
                + "policy11" + CoverageHitConstants.DELIMITER + "7" + CoverageHitConstants.DELIMITER + true);
        Assertions.assertThat(resultPolicyConditionHits.get(1)).isEqualTo("set1" + CoverageHitConstants.DELIMITER
                + "policy11" + CoverageHitConstants.DELIMITER + "8" + CoverageHitConstants.DELIMITER + true);
        Assertions.assertThat(resultPolicyConditionHits.get(2)).isEqualTo("set1" + CoverageHitConstants.DELIMITER
                + "policy11" + CoverageHitConstants.DELIMITER + "9" + CoverageHitConstants.DELIMITER + true);
        Assertions.assertThat(resultPolicyConditionHits.get(3)).isEqualTo("set1" + CoverageHitConstants.DELIMITER
                + "policy12" + CoverageHitConstants.DELIMITER + "7" + CoverageHitConstants.DELIMITER + true);
        Assertions.assertThat(resultPolicyConditionHits.get(4)).isEqualTo("set1" + CoverageHitConstants.DELIMITER
                + "policy12" + CoverageHitConstants.DELIMITER + "8" + CoverageHitConstants.DELIMITER + true);
        Assertions.assertThat(resultPolicyConditionHits.get(5)).isEqualTo("set1" + CoverageHitConstants.DELIMITER
                + "policy12" + CoverageHitConstants.DELIMITER + "9" + CoverageHitConstants.DELIMITER + true);
        Assertions.assertThat(resultPolicyConditionHits.get(6)).isEqualTo("set2" + CoverageHitConstants.DELIMITER
                + "policy21" + CoverageHitConstants.DELIMITER + "7" + CoverageHitConstants.DELIMITER + true);
        Assertions.assertThat(resultPolicyConditionHits.get(7)).isEqualTo("set2" + CoverageHitConstants.DELIMITER
                + "policy21" + CoverageHitConstants.DELIMITER + "8" + CoverageHitConstants.DELIMITER + true);
        Assertions.assertThat(resultPolicyConditionHits.get(8)).isEqualTo("set2" + CoverageHitConstants.DELIMITER
                + "policy21" + CoverageHitConstants.DELIMITER + "9" + CoverageHitConstants.DELIMITER + true);
        Assertions.assertThat(resultPolicyConditionHits.get(9)).isEqualTo("set2" + CoverageHitConstants.DELIMITER
                + "policy22" + CoverageHitConstants.DELIMITER + "7" + CoverageHitConstants.DELIMITER + true);
        Assertions.assertThat(resultPolicyConditionHits.get(10)).isEqualTo("set2" + CoverageHitConstants.DELIMITER
                + "policy22" + CoverageHitConstants.DELIMITER + "8" + CoverageHitConstants.DELIMITER + true);
        Assertions.assertThat(resultPolicyConditionHits.get(11)).isEqualTo("set2" + CoverageHitConstants.DELIMITER
                + "policy22" + CoverageHitConstants.DELIMITER + "9" + CoverageHitConstants.DELIMITER + true);
    }

    @Test
    void testCoverageWriting_FileNotExist(@TempDir Path tempDir) {
        var recorder = new CoverageHitAPIFile(tempDir);
        // arrange
        // simulate something deletes expected files during runtime
        recorder.cleanCoverageHitFiles();
        // act
        recorder.recordPolicySetHit(new PolicySetHit("set"));
        // assert
        Assertions.assertThatNoException();
    }

}
