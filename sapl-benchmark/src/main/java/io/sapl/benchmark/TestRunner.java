package io.sapl.benchmark;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.analyzer.PolicyAnalyzer;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.SAPLInterpreter;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.prp.ParsedDocumentIndex;
import io.sapl.api.prp.PolicyRetrievalResult;
import io.sapl.generator.DomainData;
import io.sapl.generator.DomainGenerator;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TestRunner {

	private static final double MILLION = 1000000.0D;

	private static final double REMOVE_EDGE_DATA_BY_PERCENTAGE = 0.005D;

	private static final Map<String, JsonNode> VARIABLES = Collections.emptyMap();

	private static final AnnotationFunctionContext FUNCTION_CONTEXT = new AnnotationFunctionContext();

	private static final SAPLInterpreter SAPL_INTERPRETER = new DefaultSAPLInterpreter();

	private ParsedDocumentIndex getDocumentIndex(IndexType indexType) {
		switch (indexType) {
		case IMPROVED:
			return new ImprovedDocumentIndex(new AnnotationFunctionContext());
		case SIMPLE:
			// fall through
		default:
			return new SimpleParsedDocumentIndex();
		}
	}

	private void validateIndexImplementation(IndexType indexType, ParsedDocumentIndex documentIndex) {
		switch (indexType) {
		case IMPROVED:
			if (!(documentIndex instanceof ImprovedDocumentIndex))
				throw new RuntimeException(
						String.format("wrong index impl. expected %s but got %s", indexType, documentIndex.getClass()));
			break;
		case SIMPLE:
			if (!(documentIndex instanceof SimpleParsedDocumentIndex))
				throw new RuntimeException(
						String.format("wrong index impl. expected %s but got %s", indexType, documentIndex.getClass()));
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

	private double extractMin(List<XlsRecord> data) {
		double min = Double.MAX_VALUE;
		for (XlsRecord item : data) {
			if (item.getTimeDuration() < min) {
				min = item.getTimeDuration();
			}
		}
		return min;
	}

	private double extractMax(List<XlsRecord> data) {
		double max = Double.MIN_VALUE;
		for (XlsRecord item : data) {
			if (item.getTimeDuration() > max) {
				max = item.getTimeDuration();
			}
		}
		return max;
	}

	private double extractAvg(List<XlsRecord> data) {
		double sum = 0;
		for (XlsRecord item : data) {
			sum += item.getTimeDuration();
		}
		return sum / data.size();
	}

	private double extractMdn(List<XlsRecord> data) {
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

		return run(config, path + "/" + subFolder, benchmarkDataContainer, domainGenerator.getDomainData(), generator);
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
		return run(updatedConfig, policyFolder, benchmarkDataContainer, domainGenerator.getDomainData(),
				new PolicyGenerator(config, domainGenerator.getDomainData()));
	}

	private ParsedDocumentIndex initializeIndex(IndexType indexType, String policyFolder) {

		ParsedDocumentIndex documentIndex = getDocumentIndex(indexType);
		validateIndexImplementation(indexType, documentIndex);

		try {
			DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(policyFolder), "*.sapl");
			try {
				for (Path filePath : stream) {
					SAPL saplDocument = SAPL_INTERPRETER.parse(Files.newInputStream(filePath));
					documentIndex.put(filePath.toString(), saplDocument);
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

		documentIndex.setLiveMode();

		return documentIndex;
	}

	private List<XlsRecord> run(PolicyGeneratorConfiguration config, String policyFolder,
			BenchmarkDataContainer benchmarkDataContainer, DomainData domainData, PolicyGenerator generator) {

		List<XlsRecord> results = new LinkedList<>();
		// PolicyGenerator generator = new PolicyGenerator(config, domainData);

		log.info("running benchmark with config={}, runs={}", config.getName(), benchmarkDataContainer.getRuns());

		try {
			log.debug("init index");
			// create PRP
			long begin = System.nanoTime();
			ParsedDocumentIndex documentIndex = initializeIndex(benchmarkDataContainer.getIndexType(), policyFolder);
			double timePreparation = nanoToMs(System.nanoTime() - begin);

			// warm up
			warmUp(generator, documentIndex);

			// generate AuthorizationSubscription
			List<AuthorizationSubscription> subscriptions = generateSubscriptions(benchmarkDataContainer, generator);

			for (int j = 0; j < benchmarkDataContainer.getRuns(); j++) {

				AuthorizationSubscription request = generator.getRandomElement(subscriptions);

				long start = System.nanoTime();
				PolicyRetrievalResult result = documentIndex.retrievePolicies(request, FUNCTION_CONTEXT, VARIABLES)
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
						"AuthorizationSubscription", buildResponseStringForResult(result, decision)));

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

	private void warmUp(PolicyGenerator generator, ParsedDocumentIndex documentIndex) {
		try {
			for (int i = 0; i < 10; i++) {
				documentIndex.retrievePolicies(generator.createEmptySubscription(), FUNCTION_CONTEXT, VARIABLES);
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
