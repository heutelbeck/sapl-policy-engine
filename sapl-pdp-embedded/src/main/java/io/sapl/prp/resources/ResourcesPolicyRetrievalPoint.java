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
import io.sapl.prp.inmemory.indexed.FastParsedDocumentIndex;
import io.sapl.prp.inmemory.simple.SimpleParsedDocumentIndex;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
public class ResourcesPolicyRetrievalPoint implements PolicyRetrievalPoint {

	private static final String DEFAULT_PATH = "/policies";

	public static final String POLICY_FILE_PATTERN = "*.sapl";

	private ParsedDocumentIndex parsedDocIdx;

	private SAPLInterpreter interpreter = new DefaultSAPLInterpreter();

	public ResourcesPolicyRetrievalPoint() throws IOException, URISyntaxException, PolicyEvaluationException {
		this(null);
	}

	public ResourcesPolicyRetrievalPoint(String policyPath)
			throws IOException, URISyntaxException, PolicyEvaluationException {
		this(ResourcesPolicyRetrievalPoint.class, policyPath, null);
	}

	public ResourcesPolicyRetrievalPoint(Class<?> clazz, String policyPath)
			throws IOException, URISyntaxException, PolicyEvaluationException {
		this(clazz, policyPath, null);
	}

	public ResourcesPolicyRetrievalPoint(Class<?> clazz, String policyPath, FunctionContext functionCtx)
			throws IOException, URISyntaxException, PolicyEvaluationException {
		if (policyPath == null) {
			policyPath = DEFAULT_PATH;
		}

		URL policyFolderUrl = clazz.getResource(policyPath);

		if (policyFolderUrl == null) {
			throw new PolicyEvaluationException(
					"Policy folder not found. Path:" + policyPath + " - URL: " + policyFolderUrl);
		}

		parsedDocIdx = functionCtx != null ? new FastParsedDocumentIndex(functionCtx) : new SimpleParsedDocumentIndex();

		Path path = null;
		FileSystem fs = null;
		if (policyFolderUrl.getProtocol().equals("jar")) {
			final Map<String, String> env = new HashMap<>();
			final String[] array = policyFolderUrl.toString().split("!");
			fs = FileSystems.newFileSystem(URI.create(array[0]), env);
			path = fs.getPath(array[1]);
		} else {
			path = Paths.get(policyFolderUrl.toURI());
		}
		try {
			LOGGER.info("current path: {}", path);
			DirectoryStream<Path> stream = Files.newDirectoryStream(path, POLICY_FILE_PATTERN);
			try {
				for (Path filePath : stream) {
					LOGGER.info("load: {}", filePath);
					final SAPL saplDocument = interpreter.parse(Files.newInputStream(filePath));
					this.parsedDocIdx.put(filePath.toString(), saplDocument);
				}
			} finally {
				stream.close();
			}
		} finally {
			if (fs != null) {
				fs.close();
			}
		}
		this.parsedDocIdx.setLiveMode();
	}

	@Override
	public Flux<PolicyRetrievalResult> retrievePolicies(Request request, FunctionContext functionCtx,
			Map<String, JsonNode> variables) {
		return Flux.just(parsedDocIdx.retrievePolicies(request, functionCtx, variables)).map(this::logMatching);
	}

	private PolicyRetrievalResult logMatching(PolicyRetrievalResult result) {
		if (result.getMatchingDocuments().isEmpty()) {
			LOGGER.trace("|-- Matching documents: NONE");
		} else {
			LOGGER.trace("|-- Matching documents:");
			for (SAPL doc : result.getMatchingDocuments()) {
				LOGGER.trace("| |-- * {} ({})", doc.getPolicyElement().getSaplName(), doc.getPolicyElement().getClass().getName());
			}
		}
		LOGGER.trace("|");

		return result;
	}

	@Override
	public void dispose() {
		// NOP
	}
}
