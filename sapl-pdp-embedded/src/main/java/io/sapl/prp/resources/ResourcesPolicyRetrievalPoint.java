package io.sapl.prp.resources;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.SAPLInterpreter;
import io.sapl.api.pdp.Request;
import io.sapl.api.prp.ParsedDocumentIndex;
import io.sapl.api.prp.PolicyRetrievalPoint;
import io.sapl.api.prp.PolicyRetrievalResult;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.prp.inmemory.simple.SimpleParsedDocumentIndex;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
public class ResourcesPolicyRetrievalPoint implements PolicyRetrievalPoint {

	private static final String DEFAULT_POLICIES_PATH = "/policies";

	private static final String POLICY_FILE_GLOB_PATTERN = "*.sapl";

	private ParsedDocumentIndex parsedDocIdx;

	public ResourcesPolicyRetrievalPoint()
			throws IOException, URISyntaxException, PolicyEvaluationException {
		this(DEFAULT_POLICIES_PATH, new SimpleParsedDocumentIndex());
	}

	public ResourcesPolicyRetrievalPoint(@NonNull String policyPath,
			@NonNull ParsedDocumentIndex parsedDocumentIndex)
			throws IOException, URISyntaxException, PolicyEvaluationException {
		this(ResourcesPolicyRetrievalPoint.class, policyPath, parsedDocumentIndex);
	}

	public ResourcesPolicyRetrievalPoint(@NonNull Class<?> clazz,
			@NonNull String policyPath, @NonNull ParsedDocumentIndex parsedDocumentIndex)
			throws IOException, URISyntaxException, PolicyEvaluationException {

		URL policyFolderUrl = clazz.getResource(policyPath);

		if (policyFolderUrl == null) {
			throw new PolicyEvaluationException("Policy folder not found. Path:"
					+ policyPath);
		}

		this.parsedDocIdx = parsedDocumentIndex;

		Path path;
		FileSystem fs = null;
		try {
			if ("jar".equals(policyFolderUrl.getProtocol())) {
				final Map<String, String> env = new HashMap<>();
				final String[] array = policyFolderUrl.toString().split("!");
				fs = FileSystems.newFileSystem(URI.create(array[0]), env);
				path = fs.getPath(array[1]);
			}
			else {
				path = Paths.get(policyFolderUrl.toURI());
			}
			LOGGER.info("current path: {}", path);
			final SAPLInterpreter interpreter = new DefaultSAPLInterpreter(); 
			try (DirectoryStream<Path> stream = Files.newDirectoryStream(path,
					POLICY_FILE_GLOB_PATTERN)) {
				for (Path filePath : stream) {
					LOGGER.info("load: {}", filePath);
					final SAPL saplDocument = interpreter
							.parse(Files.newInputStream(filePath));
					this.parsedDocIdx.put(filePath.toString(), saplDocument);
				}
			}
		}
		finally {
			if (fs != null) {
				fs.close();
			}
		}
		this.parsedDocIdx.setLiveMode();
	}

	@Override
	public Flux<PolicyRetrievalResult> retrievePolicies(Request request,
			FunctionContext functionCtx, Map<String, JsonNode> variables) {
		return Flux.just(parsedDocIdx.retrievePolicies(request, functionCtx, variables))
				.doOnNext(this::logMatching);
	}

	private void logMatching(PolicyRetrievalResult result) {
		if (result.getMatchingDocuments().isEmpty()) {
			LOGGER.trace("|-- Matching documents: NONE");
		}
		else {
			LOGGER.trace("|-- Matching documents:");
			for (SAPL doc : result.getMatchingDocuments()) {
				LOGGER.trace("| |-- * {} ({})", doc.getPolicyElement().getSaplName(),
						doc.getPolicyElement().getClass().getName());
			}
		}
		LOGGER.trace("|");
	}

}
