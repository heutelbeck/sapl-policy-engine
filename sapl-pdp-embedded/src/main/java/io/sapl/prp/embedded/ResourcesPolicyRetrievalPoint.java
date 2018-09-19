package io.sapl.prp.embedded;

import java.io.IOException;
import java.util.Map;

import io.sapl.api.prp.PolicyRetrievalPoint;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.SAPLInterpreter;
import io.sapl.api.pdp.Request;
import io.sapl.api.prp.ParsedDocumentIndex;
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

	ParsedDocumentIndex parsedDocIdx;
	SAPLInterpreter interpreter = new DefaultSAPLInterpreter();

	protected ResourcesPolicyRetrievalPoint(String policyPath, ParsedDocumentIndex parsedDocIdx)
			throws IOException, PolicyEvaluationException {
		String path = (policyPath == null) ? DEFAULT_PATH : policyPath;
		this.parsedDocIdx = parsedDocIdx;
		PathMatchingResourcePatternResolver pm = new PathMatchingResourcePatternResolver();
		Resource[] policyFiles = pm.getResources(path + SAPL_SUFFIX);
		for (Resource policyFile : policyFiles) {
			SAPL saplDocument = interpreter.parse(policyFile.getInputStream());
			this.parsedDocIdx.put(policyFile.getFilename(), saplDocument);
		}
		this.parsedDocIdx.setLiveMode();
	}

	@Override
	public PolicyRetrievalResult retrievePolicies(Request request, FunctionContext functionCtx,
			Map<String, JsonNode> variables) {
		return parsedDocIdx.retrievePolicies(request, functionCtx, variables);
	}

	public static PolicyRetrievalPoint of(String policyPath, EmbeddedPolicyDecisionPointConfiguration config,
			FunctionContext functionCtx) throws IOException, PolicyEvaluationException {
		if (PrpImplementation.INDEXED == config.getPrpImplementation()) {
			return new ResourcesPolicyRetrievalPoint(policyPath, new FastParsedDocumentIndex(functionCtx));
		} else {
			return new ResourcesPolicyRetrievalPoint(policyPath, new SimpleParsedDocumentIndex());
		}
	}

	public static PolicyRetrievalPoint of(EmbeddedPolicyDecisionPointConfiguration config, FunctionContext functionCtx)
			throws IOException, PolicyEvaluationException {
		return of(null, config, functionCtx);
	}

}
