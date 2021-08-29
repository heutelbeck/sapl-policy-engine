package io.sapl.test.coverage.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.sapl.test.coverage.api.model.PolicyConditionHit;
import io.sapl.test.coverage.api.model.PolicyHit;
import io.sapl.test.coverage.api.model.PolicySetHit;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class CoverageHitAPIFile implements CoverageHitRecorder, CoverageHitReader {
	public final Path FILE_PATH_POLICY_SET_HITS;
	public final Path FILE_PATH_POLICY_HITS;
	public final Path FILE_PATH_POLICY_CONDITION_HITS;

	CoverageHitAPIFile(Path basedir) {
		FILE_PATH_POLICY_SET_HITS = basedir.resolve("hits").resolve("_policySetHits.txt");
		FILE_PATH_POLICY_HITS = basedir.resolve("hits").resolve("_policyHits.txt");
		FILE_PATH_POLICY_CONDITION_HITS = basedir.resolve("hits").resolve("_policyConditionHits.txt");
	}

	@Override
	public void recordPolicySetHit(PolicySetHit hit) {
		addPossibleHit(FILE_PATH_POLICY_SET_HITS, hit.toString());
	}

	@Override
	public void recordPolicyHit(PolicyHit hit) {
		addPossibleHit(FILE_PATH_POLICY_HITS, hit.toString());
	}

	@Override
	public void recordPolicyConditionHit(PolicyConditionHit hit) {
		addPossibleHit(FILE_PATH_POLICY_CONDITION_HITS, hit.toString());

	}

	private void addPossibleHit(@NonNull Path filePath, String lineToAdd) {
		if (!Files.exists(filePath)) {
			log.warn("Expected File {} not found. Did something deleted this file during test runtime?", filePath);
			createCoverageHitFile(filePath);
		}

		try {
			if (doesLineExistsInFile(filePath, lineToAdd)) {
				// do nothing as already hit
			} else {
				appendLineToFile(filePath, lineToAdd);
			}
		} catch (IOException e) {
			log.error("Error writing File " + filePath, e);
		}
	}

	private boolean doesLineExistsInFile(Path filePathPolicySetHits, String lineToAdd) throws IOException {
		try (Stream<String> stream = Files.lines(filePathPolicySetHits)) {
			Optional<String> lineHavingTarget = stream.filter(l -> l.contains(lineToAdd)).findFirst();
			if (lineHavingTarget.isPresent()) {
				return true;
			} else {
				return false;
			}
		}
	}

	private void appendLineToFile(Path filePathPolicySetHits, String lineToAdd) throws IOException {
		Files.write(filePathPolicySetHits, (lineToAdd + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
				StandardOpenOption.APPEND);
	}

	@Override
	public List<PolicySetHit> readPolicySetHits() {
		return readFileLines(FILE_PATH_POLICY_SET_HITS).stream().map(line -> PolicySetHit.fromString(line))
				.collect(Collectors.toList());
	}

	@Override
	public List<PolicyHit> readPolicyHits() {
		return readFileLines(FILE_PATH_POLICY_HITS).stream().map(line -> PolicyHit.fromString(line))
				.collect(Collectors.toList());
	}

	@Override
	public List<PolicyConditionHit> readPolicyConditionHits() {
		return readFileLines(FILE_PATH_POLICY_CONDITION_HITS).stream().map(line -> PolicyConditionHit.fromString(line))
				.collect(Collectors.toList());
	}

	private List<String> readFileLines(Path filePathPolicySetHits) {
		try {
			return Files.readAllLines(filePathPolicySetHits);
		} catch (IOException e) {
			log.error(String.format("Error reading File %s. Is the policy coverage recording disabled?",
					filePathPolicySetHits.toAbsolutePath().toString()), e);
		}
		return new LinkedList<>();
	}

	@Override
	public void cleanCoverageHitFiles() {
		cleanCoverageHitFile(FILE_PATH_POLICY_SET_HITS);
		cleanCoverageHitFile(FILE_PATH_POLICY_HITS);
		cleanCoverageHitFile(FILE_PATH_POLICY_CONDITION_HITS);
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
		createCoverageHitFile(FILE_PATH_POLICY_SET_HITS);
		createCoverageHitFile(FILE_PATH_POLICY_HITS);
		createCoverageHitFile(FILE_PATH_POLICY_CONDITION_HITS);
	}

	private void createCoverageHitFile(Path filePath) {
		try {
			// ignore when file in previous test got created
			if (!Files.exists(filePath)) {
				var parent = filePath.getParent();
				if(parent != null)
					Files.createDirectories(parent);
				Files.createFile(filePath);
			}
		} catch (IOException e) {
			log.error("Error creating File " + filePath, e);
		}
	}

}
