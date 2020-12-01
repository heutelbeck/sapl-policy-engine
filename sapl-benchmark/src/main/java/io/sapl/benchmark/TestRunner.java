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
package io.sapl.benchmark;


import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.analyzer.PolicyAnalyzer;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.SAPLInterpreter;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.prp.PolicyRetrievalResult;
import io.sapl.generator.DomainGenerator;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.prp.PrpUpdateEvent;
import io.sapl.prp.PrpUpdateEvent.Type;
import io.sapl.prp.PrpUpdateEvent.Update;
import io.sapl.prp.index.ImmutableParsedDocumentIndex;
import io.sapl.prp.index.canonical.CanonicalImmutableParsedDocumentIndex;
import io.sapl.prp.index.naive.NaiveImmutableParsedDocumentIndex;
import io.sapl.spring.pdp.embedded.EmbeddedPDPProperties.IndexType;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
public class TestRunner {

    private static final double MILLION = 1000000.0D;
    private static final double REMOVE_EDGE_DATA_BY_PERCENTAGE = 0.005D;
    private static final Map<String, JsonNode> VARIABLES = Collections.emptyMap();
    private static final AttributeContext ATTRIBUTE_CONTEXT = new AnnotationAttributeContext();
    private static final FunctionContext FUNCTION_CONTEXT = new AnnotationFunctionContext();
    private static final SAPLInterpreter SAPL_INTERPRETER = new DefaultSAPLInterpreter();
    private static final EvaluationContext PDP_SCOPED_EVALUATION_CONTEXT = new EvaluationContext(
            new AnnotationAttributeContext(), new AnnotationFunctionContext(), new HashMap<>());

    private ImmutableParsedDocumentIndex getSeedIndex(IndexType indexType) {
        switch (indexType) {
            case CANONICAL:
                return new CanonicalImmutableParsedDocumentIndex(PDP_SCOPED_EVALUATION_CONTEXT);
            case NAIVE:
                // fall through
            default:
                return new NaiveImmutableParsedDocumentIndex();
        }
    }

    private void validateIndexImplementation(IndexType indexType, ImmutableParsedDocumentIndex documentIndex) {
        switch (indexType) {
            case CANONICAL:
                if (!(documentIndex instanceof CanonicalImmutableParsedDocumentIndex))
                    throw new RuntimeException(
                            String.format("wrong index impl. expected %s but got %s", indexType, documentIndex
                                    .getClass()));
                break;
            case NAIVE:
                if (!(documentIndex instanceof NaiveImmutableParsedDocumentIndex))
                    throw new RuntimeException(
                            String.format("wrong index impl. expected %s but got %s", indexType, documentIndex
                                    .getClass()));
                break;
        }
    }

    private void sanitizeResults(List<XlsRecord> results) {
        int numberOfDataToRemove = (int) (results.size() * REMOVE_EDGE_DATA_BY_PERCENTAGE);

        for (int i = 0; i < numberOfDataToRemove; i++) {
            results.stream().min(Comparator.comparingDouble(XlsRecord::getTimeDuration)).ifPresent(results::remove);

            results.stream().max(Comparator.comparingDouble(XlsRecord::getTimeDuration)).ifPresent(results::remove);
        }
    }

    private void addResultsForConfigToContainer(BenchmarkDataContainer benchmarkDataContainer,
                                                PolicyGeneratorConfiguration config, List<XlsRecord> results) {
        benchmarkDataContainer.getIdentifier().add(config.getName());
        benchmarkDataContainer.getMinValues().add(extractMin(results));
        benchmarkDataContainer.getMaxValues().add(extractMax(results));
        benchmarkDataContainer.getAvgValues().add(extractAvg(results));
        benchmarkDataContainer.getMdnValues().add(extractMdn(results));
        benchmarkDataContainer.getData().addAll(results);

        benchmarkDataContainer.getConfigs().add(config);
    }

    private double extractMin(Iterable<XlsRecord> data) {
        double min = Double.MAX_VALUE;
        for (XlsRecord item : data) {
            if (item.getTimeDuration() < min) {
                min = item.getTimeDuration();
            }
        }
        return min;
    }

    private double extractMax(Iterable<XlsRecord> data) {
        double max = Double.MIN_VALUE;
        for (XlsRecord item : data) {
            if (item.getTimeDuration() > max) {
                max = item.getTimeDuration();
            }
        }
        return max;
    }

    private double extractAvg(Collection<XlsRecord> data) {
        double sum = 0;
        for (XlsRecord item : data) {
            sum += item.getTimeDuration();
        }
        return sum / data.size();
    }

    private double extractMdn(Collection<XlsRecord> data) {
        List<Double> list = data.stream().map(XlsRecord::getTimeDuration).sorted().collect(Collectors.toList());
        int index = list.size() / 2;
        if (list.size() % 2 == 0) {
            return (list.get(index) + list.get(index - 1)) / 2;
        } else {
            return list.get(index);
        }
    }

    public List<XlsRecord> runTest(PolicyGeneratorConfiguration config, String path,
                                   BenchmarkDataContainer benchmarkDataContainer, DomainGenerator domainGenerator) throws Exception {

        config.setPath(path);
        // config.updateName();
        PolicyGenerator generator = new PolicyGenerator(config, domainGenerator.getDomainData());
        String subFolder = generateRandomPolicies(generator, path);

        return run(config, path + "/" + subFolder, benchmarkDataContainer, generator);
    }

    public List<XlsRecord> runTestNew(PolicyGeneratorConfiguration config, String policyFolder,
                                      BenchmarkDataContainer benchmarkDataContainer, DomainGenerator domainGenerator) {

        log.info("generating domain policies with seed {}", config.getSeed());
        domainGenerator.generateDomainPoliciesWithSeed(config.getSeed(),
                domainGenerator.getDomainData().getPolicyDirectoryPath());

        // update config by analyzing the generated policies
        PolicyGeneratorConfiguration updatedConfig = new PolicyAnalyzer(domainGenerator.getDomainData())
                .analyzeSaplDocuments(benchmarkDataContainer.getIndexType());

        log.info("{}", updatedConfig);
        return run(updatedConfig, policyFolder, benchmarkDataContainer,
                new PolicyGenerator(config, domainGenerator.getDomainData()));
    }

    private ImmutableParsedDocumentIndex initializeIndex(IndexType indexType, String policyFolder) {

        ImmutableParsedDocumentIndex seedIndex = getSeedIndex(indexType);
        validateIndexImplementation(indexType, seedIndex);

        List<Update> updates = new ArrayList<>();

        try {
            DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(policyFolder), "*.sapl");
            try {
                for (Path filePath : stream) {

                    SAPL saplDocument = SAPL_INTERPRETER.parse(Files.newInputStream(filePath));
                    updates.add(new Update(Type.PUBLISH, saplDocument, saplDocument.toString()));
                }
            } catch (Exception var11) {
                try {
                    stream.close();
                } catch (Exception var10) {
                    var11.addSuppressed(var10);
                }
                throw var11;
            }

            stream.close();

        } catch (PolicyEvaluationException | IOException var12) {
            log.error("Error while initializing the document index.", var12);
        }

        PrpUpdateEvent prpUpdateEvent = new PrpUpdateEvent(updates);
        ImmutableParsedDocumentIndex updatedIndex = seedIndex.apply(prpUpdateEvent);


        return updatedIndex;
    }

    private List<XlsRecord> run(PolicyGeneratorConfiguration config, String policyFolder,
                                BenchmarkDataContainer benchmarkDataContainer, PolicyGenerator generator) {

        List<XlsRecord> results = new LinkedList<>();
        // PolicyGenerator generator = new PolicyGenerator(config, domainData);

        log.info("running benchmark with config={}, runs={}", config.getName(), benchmarkDataContainer.getRuns());

        try {
            log.debug("init index");
            // create PRP
            long begin = System.nanoTime();
            ImmutableParsedDocumentIndex initializedIndex =
                    initializeIndex(benchmarkDataContainer.getIndexType(), policyFolder);
            double timePreparation = nanoToMs(System.nanoTime() - begin);

            // warm up
            warmUp(generator, initializedIndex);

            // generate AuthorizationSubscription
            List<AuthorizationSubscription> subscriptions = generateSubscriptions(benchmarkDataContainer, generator);

            for (int j = 0; j < benchmarkDataContainer.getRuns(); j++) {

                AuthorizationSubscription request = generator.getRandomElement(subscriptions);

                long start = System.nanoTime();
                EvaluationContext subscriptionScopedEvaluationCtx =
                        new EvaluationContext(ATTRIBUTE_CONTEXT, FUNCTION_CONTEXT, VARIABLES)
                                .forAuthorizationSubscription(request);
                PolicyRetrievalResult result = initializedIndex.retrievePolicies(subscriptionScopedEvaluationCtx)
                        .block();
                long end = System.nanoTime();

                double timeRetrieve = nanoToMs(end - start);

                // Objects.requireNonNull(result);
                AuthorizationDecision decision = AuthorizationDecision.INDETERMINATE;
                // AuthorizationDecision decision = DOCUMENTS_COMBINATOR
                // .combineMatchingDocuments(result.getMatchingDocuments(), false,
                // request, ATTRIBUTE_CONTEXT, FUNCTION_CONTEXT, VARIABLES).blockFirst();
                // Objects.requireNonNull(decision);

                results.add(new XlsRecord(j, config.getName(), timePreparation, timeRetrieve,
                        "AuthorizationSubscription", buildResponseStringForResult(Objects
                        .requireNonNull(result), decision)));

                log.debug("Total : {}ms", timeRetrieve);
            }

            // log.debug("destroy index");
            // documentIndex.destroyIndex();

        } catch (Exception e) {
            log.error("Error running test", e);
        }

        sanitizeResults(results);
        addResultsForConfigToContainer(benchmarkDataContainer, config, results);

        return results;
    }

    private void warmUp(PolicyGenerator generator, ImmutableParsedDocumentIndex documentIndex) {
        EvaluationContext emptySubscriptionScopedEvaluationCtx =
                new EvaluationContext(ATTRIBUTE_CONTEXT, FUNCTION_CONTEXT, VARIABLES)
                        .forAuthorizationSubscription(generator.createEmptySubscription());
        try {
            for (int i = 0; i < 10; i++) {
                documentIndex.retrievePolicies(emptySubscriptionScopedEvaluationCtx);
            }
        } catch (Exception ignored) {
            log.error("error during warm-up", ignored);
        }
    }

    private List<AuthorizationSubscription> generateSubscriptions(BenchmarkDataContainer benchmarkDataContainer,
                                                                  PolicyGenerator generator) {
        List<AuthorizationSubscription> subscriptions = new LinkedList<>();
        for (int i = 0; i < benchmarkDataContainer.getRuns(); i++) {
            AuthorizationSubscription sub = Benchmark.performFullyRandomBenchmark
                    ? generator.createFullyRandomSubscription()
                    : generator.createStructuredRandomSubscription();
            // AuthorizationSubscription sub = generator.createEmptySubscription();

            subscriptions.add(sub);
            log.trace("generated sub: {}", sub);
        }
        return subscriptions;
    }

    private String buildResponseStringForResult(PolicyRetrievalResult policyRetrievalResult,
                                                AuthorizationDecision decision) {
        return String.format("PolicyRetrievalResult(decision=%s, matchingDocumentsCount=%d, errorsInTarget=%b)",
                decision.getDecision(), policyRetrievalResult.getMatchingDocuments().size(),
                policyRetrievalResult.isErrorsInTarget());
    }

    @SneakyThrows
    private String generateRandomPolicies(PolicyGenerator generator, String path) throws IOException {

        String subfolder = generator.getConfig().getName().replaceAll("[^a-zA-Z0-9]", "");
        final Path dir = Paths.get(path, subfolder);
        Files.createDirectories(dir);
        generator.generatePolicies(subfolder);

        return subfolder;
    }

    private double nanoToMs(long nanoseconds) {
        return nanoseconds / MILLION;
    }

}
