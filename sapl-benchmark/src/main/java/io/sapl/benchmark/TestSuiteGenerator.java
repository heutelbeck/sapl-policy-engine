package io.sapl.benchmark;

import lombok.experimental.UtilityClass;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@UtilityClass
public class TestSuiteGenerator {

    TestSuite generate(String path, Random dice) {
        List<PolicyGeneratorConfiguration> configs = new LinkedList<>();
        configs.add(PolicyGeneratorConfiguration.builder()
                .name("100p, 5v, 200vp")
                .seed(29724)
                .policyCount(100)
                .logicalVariableCount(5)
                .variablePoolCount(200)
                .bracketProbability(0.2)
                .conjunctionProbability(0.9)
                .negationProbability(0.3)
                .falseProbability(0.5)
                .path(path)
                .build()
        );
        configs.add(PolicyGeneratorConfiguration.builder()
                .name("100p, 10v, 200vp")
                .seed(81893)
                .policyCount(100)
                .logicalVariableCount(10)
                .variablePoolCount(200)
                .bracketProbability(0.2)
                .conjunctionProbability(0.9)
                .negationProbability(0.3)
                .falseProbability(0.5)
                .path(path)
                .build()
        );
        configs.add(PolicyGeneratorConfiguration.builder()
                .name("200p, 5v, 400vp")
                .seed(1271)
                .policyCount(200)
                .logicalVariableCount(5)
                .variablePoolCount(400)
                .bracketProbability(0.2)
                .conjunctionProbability(0.9)
                .negationProbability(0.3)
                .falseProbability(0.5)
                .path(path)
                .build()
        );
        configs.add(PolicyGeneratorConfiguration.builder()
                .name("200p, 10v, 400vp")
                .seed(60565)
                .policyCount(200)
                .logicalVariableCount(10)
                .variablePoolCount(400)
                .bracketProbability(0.2)
                .conjunctionProbability(0.9)
                .negationProbability(0.3)
                .falseProbability(0.5)
                .path(path)
                .build()
        );
        configs.add(PolicyGeneratorConfiguration.builder()
                .name("500p, 5v, 1000vp")
                .seed(2517)
                .policyCount(500)
                .logicalVariableCount(5)
                .variablePoolCount(1000)
                .bracketProbability(0.2)
                .conjunctionProbability(0.9)
                .negationProbability(0.3)
                .falseProbability(0.5)
                .path(path)
                .build()
        );
        configs.add(PolicyGeneratorConfiguration.builder()
                .name("500p, 10v, 1000vp")
                .seed(52359)
                .policyCount(500)
                .logicalVariableCount(10)
                .variablePoolCount(1000)
                .bracketProbability(0.2)
                .conjunctionProbability(0.9)
                .negationProbability(0.3)
                .falseProbability(0.5)
                .path(path)
                .build()
        );
        configs.add(PolicyGeneratorConfiguration.builder()
                .name("1000p, 5v, 1000vp")
                .seed(36299)
                .policyCount(1000)
                .logicalVariableCount(5)
                .variablePoolCount(2000)
                .bracketProbability(0.2)
                .conjunctionProbability(0.9)
                .negationProbability(0.3)
                .falseProbability(0.5)
                .path(path)
                .build()
        );
        configs.add(PolicyGeneratorConfiguration.builder()
                .name("1000p, 10v, 1000vp")
                .seed(85821)
                .policyCount(1000)
                .logicalVariableCount(10)
                .variablePoolCount(2000)
                .bracketProbability(0.2)
                .conjunctionProbability(0.9)
                .negationProbability(0.3)
                .falseProbability(0.5)
                .path(path)
                .build()
        );
        configs.add(PolicyGeneratorConfiguration.builder()
                .name("MH Conj. Max TC")
                .seed(63317)
                .policyCount(1000)
                .logicalVariableCount(10)
                .variablePoolCount(2000)
                .bracketProbability(0.2)
                .conjunctionProbability(0.7)
                .negationProbability(0.3)
                .falseProbability(0.5)
                .path(path)
                .build()
        );
        configs.add(PolicyGeneratorConfiguration.builder()
                .name("ML Conj. Max TC")
                .seed(11142)
                .policyCount(1000)
                .logicalVariableCount(10)
                .variablePoolCount(2000)
                .bracketProbability(0.2)
                .conjunctionProbability(0.3)
                .negationProbability(0.3)
                .falseProbability(0.5)
                .path(path)
                .build()
        );

        addSeedToTestName(configs);

        return TestSuite.builder()
                .cases(configs)
                .build();
    }

    TestSuite generateN(String path, int numberOfTests, Random dice) {
        List<PolicyGeneratorConfiguration> limitedConfigs = generate(path, dice).getCases().stream()
                .limit(numberOfTests)
                .collect(Collectors.toList());

        if (limitedConfigs.size() < numberOfTests) {
            int missingTestCount = numberOfTests - limitedConfigs.size();
            limitedConfigs.addAll(generateN(path, missingTestCount, dice).getCases());
        }

        return TestSuite.builder()
                .cases(limitedConfigs)
                .build();
    }


    private void addSeedToTestName(List<PolicyGeneratorConfiguration> configs) {
        configs.forEach(config -> config.setName(String.format("%s (%d)", config.getName(), config.getSeed())));
    }
}
