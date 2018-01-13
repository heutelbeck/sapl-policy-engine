package io.sapl.prp.embedded;

import java.io.IOException;
import java.util.Map;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.SAPLInterpreter;
import io.sapl.api.pdp.Request;
import io.sapl.api.prp.ParsedDocumentPolicyRetrievalPoint;
import io.sapl.api.prp.PolicyRetrievalResult;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.pdp.embedded.EmbeddedPolicyDecisionPointConfiguration;
import io.sapl.pdp.embedded.PrpImplementation;
import io.sapl.prp.inmemory.indexed.FastParsedDocumentIndex;
import io.sapl.prp.inmemory.simple.SimpleParsedDocumentIndex;

public class ResourcesPolicyRetrievalPoint implements PolicyRetrievalPoint {

	public static final String DEFAULT_PATH = "classpath:policies";
	private static final String SAPL_SUFFIX = "/*.sapl";

	ParsedDocumentPolicyRetrievalPoint parsedDocPrp;
	SAPLInterpreter interpreter = new DefaultSAPLInterpreter();

	protected ResourcesPolicyRetrievalPoint(String policyPath, ParsedDocumentPolicyRetrievalPoint parsedDocPrp)
			throws IOException, PolicyEvaluationException {
		String path = (policyPath == null) ? DEFAULT_PATH : policyPath;
		this.parsedDocPrp = parsedDocPrp;
		PathMatchingResourcePatternResolver pm = new PathMatchingResourcePatternResolver();
		Resource[] policyFiles = pm.getResources(path + SAPL_SUFFIX);
		for (Resource policyFile : policyFiles) {
			SAPL saplDocument = interpreter.parse(policyFile.getInputStream());
			parsedDocPrp.put(policyFile.getFilename(), saplDocument);
		}
	}

	@Override
	public PolicyRetrievalResult retrievePolicies(Request request, FunctionContext functionCtx,
			Map<String, JsonNode> variables) {
		return parsedDocPrp.retrievePolicies(request, functionCtx, variables);
	}

	public static PolicyRetrievalPoint of(String policyPath, EmbeddedPolicyDecisionPointConfiguration config)
			throws IOException, PolicyEvaluationException {
		if (PrpImplementation.INDEXED == config.getPrpImplementation()) {
			return new ResourcesPolicyRetrievalPoint(policyPath, new FastParsedDocumentIndex());
		} else {
			return new ResourcesPolicyRetrievalPoint(policyPath, new SimpleParsedDocumentIndex());
		}
	}

	public static PolicyRetrievalPoint of(EmbeddedPolicyDecisionPointConfiguration config)
			throws IOException, PolicyEvaluationException {
		return of(null, config);
	}
}
