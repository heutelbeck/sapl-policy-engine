/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import io.sapl.test.coverage.api.model.PolicyConditionHit;
import io.sapl.test.coverage.api.model.PolicyHit;
import io.sapl.test.coverage.api.model.PolicySetHit;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class CoverageHitAPIFile implements CoverageHitRecorder, CoverageHitReader {

    private final Path filePathPolicySetHits;
    private final Path filePathPolicyHits;
    private final Path filePathPolicyConditionHits;

    CoverageHitAPIFile(Path basedir) {
        filePathPolicySetHits       = basedir.resolve("hits").resolve("_policySetHits.txt");
        filePathPolicyHits          = basedir.resolve("hits").resolve("_policyHits.txt");
        filePathPolicyConditionHits = basedir.resolve("hits").resolve("_policyConditionHits.txt");
    }

    @Override
    public void recordPolicySetHit(PolicySetHit hit) {
        addPossibleHit(filePathPolicySetHits, hit.toString());
    }

    @Override
    public void recordPolicyHit(PolicyHit hit) {
        addPossibleHit(filePathPolicyHits, hit.toString());
    }

    @Override
    public void recordPolicyConditionHit(PolicyConditionHit hit) {
        addPossibleHit(filePathPolicyConditionHits, hit.toString());

    }

    private void addPossibleHit(@NonNull Path filePath, String lineToAdd) {
        if (!Files.exists(filePath)) {
            log.warn("Expected File {} not found. Did something deleted this file during test runtime?", filePath);
            createCoverageHitFile(filePath);
        }

        try {
            if (!doesLineExistsInFile(filePath, lineToAdd))
                appendLineToFile(filePath, lineToAdd);
        } catch (IOException e) {
            log.error("Error writing File " + filePath, e);
        }
    }

    private boolean doesLineExistsInFile(Path filePathPolicySetHits, String lineToAdd) throws IOException {
        try (Stream<String> stream = Files.lines(filePathPolicySetHits)) {
            Optional<String> lineHavingTarget = stream.filter(l -> l.contains(lineToAdd)).findFirst();
            return lineHavingTarget.isPresent();
        }
    }

    private void appendLineToFile(Path filePathPolicySetHits, String lineToAdd) throws IOException {
        Files.writeString(filePathPolicySetHits, lineToAdd + System.lineSeparator(), StandardOpenOption.APPEND);
    }

    @Override
    public List<PolicySetHit> readPolicySetHits() {
        return readFileLines(filePathPolicySetHits).stream().map(PolicySetHit::fromString).toList();
    }

    @Override
    public List<PolicyHit> readPolicyHits() {
        return readFileLines(filePathPolicyHits).stream().map(PolicyHit::fromString).toList();
    }

    @Override
    public List<PolicyConditionHit> readPolicyConditionHits() {
        return readFileLines(filePathPolicyConditionHits).stream().map(PolicyConditionHit::fromString).toList();
    }

    private List<String> readFileLines(Path filePathPolicySetHits) {
        try {
            return Files.readAllLines(filePathPolicySetHits);
        } catch (IOException e) {
            log.error(String.format("Error reading File %s. Is the policy coverage recording disabled?",
                    filePathPolicySetHits.toAbsolutePath()), e);
        }
        return new LinkedList<>();
    }

    @Override
    public void cleanCoverageHitFiles() {
        cleanCoverageHitFile(filePathPolicySetHits);
        cleanCoverageHitFile(filePathPolicyHits);
        cleanCoverageHitFile(filePathPolicyConditionHits);
    }

    private void cleanCoverageHitFile(Path filePath) {
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.error("Error deleting File " + filePath, e);
        }
    }

    @Override
    public void createCoverageHitFiles() {
        createCoverageHitFile(filePathPolicySetHits);
        createCoverageHitFile(filePathPolicyHits);
        createCoverageHitFile(filePathPolicyConditionHits);
    }

    private void createCoverageHitFile(Path filePath) {
        try {
            // ignore when file in previous test got created
            if (!Files.exists(filePath)) {
                var parent = filePath.getParent();
                if (parent != null)
                    Files.createDirectories(parent);
                Files.createFile(filePath);
            }
        } catch (IOException e) {
            log.error("Error creating File " + filePath, e);
        }
    }

}
