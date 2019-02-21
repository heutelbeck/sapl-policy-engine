package io.sapl.pdp.embedded;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.functions.FunctionException;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.SAPLInterpreter;
import io.sapl.api.pdp.PolicyCombiningAlgorithm;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.api.pdp.Request;
import io.sapl.api.pdp.Response;
import io.sapl.api.pdp.multirequest.IdentifiableRequest;
import io.sapl.api.pdp.multirequest.IdentifiableResponse;
import io.sapl.api.pdp.multirequest.MultiRequest;
import io.sapl.api.pip.AttributeException;
import io.sapl.api.prp.PolicyRetrievalPoint;
import io.sapl.api.prp.PolicyRetrievalResult;
import io.sapl.functions.FilterFunctionLibrary;
import io.sapl.functions.SelectionFunctionLibrary;
import io.sapl.functions.StandardFunctionLibrary;
import io.sapl.functions.TemporalFunctionLibrary;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.combinators.DenyOverridesCombinator;
import io.sapl.interpreter.combinators.DenyUnlessPermitCombinator;
import io.sapl.interpreter.combinators.DocumentsCombinator;
import io.sapl.interpreter.combinators.OnlyOneApplicableCombinator;
import io.sapl.interpreter.combinators.PermitOverridesCombinator;
import io.sapl.interpreter.combinators.PermitUnlessDenyCombinator;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.pip.ClockPolicyInformationPoint;
import io.sapl.prp.filesystem.FilesystemPolicyRetrievalPoint;
import io.sapl.prp.resources.ResourcesPolicyRetrievalPoint;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
public class EmbeddedPolicyDecisionPoint implements PolicyDecisionPoint {

	private static final SAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();
	private static final String ALGORITHM_NOT_ALLOWED_FOR_PDP_LEVEL_COMBINATION = "algorithm not allowed for PDP level combination.";
	public static final String DEFAULT_PATH = "~" + File.separator + "policies";

	private PolicyRetrievalPoint prp;
	private DocumentsCombinator combinator;
	private Map<String, JsonNode> variables = new HashMap<>();
	private AttributeContext attributeCtx;
	private FunctionContext functionCtx;

	public static class Builder {
		private EmbeddedPolicyDecisionPoint pdp = new EmbeddedPolicyDecisionPoint();
		private PolicyCombiningAlgorithm algorithm;

		private Builder() throws FunctionException, AttributeException {
			pdp.functionCtx.loadLibrary(new FilterFunctionLibrary());
			pdp.functionCtx.loadLibrary(new SelectionFunctionLibrary());
			pdp.functionCtx.loadLibrary(new StandardFunctionLibrary());
			pdp.functionCtx.loadLibrary(new TemporalFunctionLibrary());
			pdp.attributeCtx.loadPolicyInformationPoint(new ClockPolicyInformationPoint());
		}

		public Builder withVariable(String name, JsonNode value) {
			pdp.variables.put(name, value);
			return this;
		}

		public Builder withFunctionLibrary(Object lib) throws FunctionException {
			pdp.functionCtx.loadLibrary(lib);
			return this;
		}

		public Builder withPolicyInformationPoint(Object pip) throws AttributeException {
			pdp.attributeCtx.loadPolicyInformationPoint(pip);
			return this;
		}

		public Builder withResourcePolicyRetrievalPoint()
				throws IOException, URISyntaxException, PolicyEvaluationException {
			pdp.prp = new ResourcesPolicyRetrievalPoint();
			return this;
		}

		public Builder withResourcePolicyRetrievalPoint(String resourcePath)
				throws IOException, URISyntaxException, PolicyEvaluationException {
			pdp.prp = new ResourcesPolicyRetrievalPoint(resourcePath);
			return this;
		}

		public Builder withResourcePolicyRetrievalPoint(Class<?> clazz, String resourcePath)
				throws IOException, URISyntaxException, PolicyEvaluationException {
			pdp.prp = new ResourcesPolicyRetrievalPoint(clazz, resourcePath);
			return this;
		}

		public Builder withFilesystemPolicyRetrievalPoint(String policiesFolder) {
			pdp.prp = new FilesystemPolicyRetrievalPoint(policiesFolder);
			return this;
		}

		public Builder withIndexedFilesystemPolicyRetrievalPoint(String policiesFolder) {
			pdp.prp = new FilesystemPolicyRetrievalPoint(policiesFolder, pdp.functionCtx);
			return this;
		}

		public Builder withCombiningAlgorithm(PolicyCombiningAlgorithm algorithm) {
			this.algorithm = algorithm;
			return this;
		}

		public EmbeddedPolicyDecisionPoint build() throws IOException, URISyntaxException, PolicyEvaluationException {
			if (pdp.prp == null) {
				withResourcePolicyRetrievalPoint();
			}
			if (algorithm == null) {
				withCombiningAlgorithm(PolicyCombiningAlgorithm.DENY_UNLESS_PERMIT);
			}
			setCombinatorAlgorithm(algorithm);
			return pdp;
		}

		private void setCombinatorAlgorithm(PolicyCombiningAlgorithm algorithm) {
			switch (algorithm) {
			case PERMIT_UNLESS_DENY:
				pdp.combinator = new PermitUnlessDenyCombinator(INTERPRETER);
				break;
			case DENY_UNLESS_PERMIT:
				pdp.combinator = new DenyUnlessPermitCombinator(INTERPRETER);
				break;
			case PERMIT_OVERRIDES:
				pdp.combinator = new PermitOverridesCombinator(INTERPRETER);
				break;
			case DENY_OVERRIDES:
				pdp.combinator = new DenyOverridesCombinator(INTERPRETER);
				break;
			case ONLY_ONE_APPLICABLE:
				pdp.combinator = new OnlyOneApplicableCombinator(INTERPRETER);
				break;
			default:
				throw new IllegalArgumentException(ALGORITHM_NOT_ALLOWED_FOR_PDP_LEVEL_COMBINATION);
			}
		}

	}

	private EmbeddedPolicyDecisionPoint() {
		functionCtx = new AnnotationFunctionContext();
		attributeCtx = new AnnotationAttributeContext();
	}

	@Override
	public Flux<Response> subscribe(Request request) {
		final Flux<PolicyRetrievalResult> retrievalResult = prp.retrievePolicies(request, functionCtx, variables);
		return retrievalResult.switchMap(result -> {
			final Collection<SAPL> matchingDocuments = result.getMatchingDocuments();
			final boolean errorsInTarget = result.isErrorsInTarget();
			return combinator.combineMatchingDocuments(matchingDocuments, errorsInTarget, request, attributeCtx,
					functionCtx, variables);
		}).distinctUntilChanged();
	}

	@Override
	public Flux<IdentifiableResponse> subscribe(MultiRequest multiRequest) {
		if (multiRequest.hasRequests()) {
			final List<Flux<IdentifiableResponse>> requestIdResponsePairFluxes = new ArrayList<>();
			for (IdentifiableRequest identifiableRequest : multiRequest) {
				final Request request = identifiableRequest.getRequest();
				final Flux<Response> responseFlux = subscribe(request);
				final Flux<IdentifiableResponse> requestResponsePairFlux = responseFlux
						.map(response -> new IdentifiableResponse(identifiableRequest.getId(), response))
						.subscribeOn(Schedulers.newElastic("pdp"));
				requestIdResponsePairFluxes.add(requestResponsePairFlux);
			}
			return Flux.merge(requestIdResponsePairFluxes);
		}
		return Flux.just(IdentifiableResponse.indeterminate());
	}

	public void dispose() {
		prp.dispose();
	}

	public static Builder builder() throws FunctionException, AttributeException {
		return new Builder();
	}

	@Override
	public Mono<Response> decide(Request request) {
		LOGGER.trace("PDP Request: {}",request);
		return subscribe(request).log().next();
	}

	@Override
	public Mono<IdentifiableResponse> decide(MultiRequest multiRequest) {
		return subscribe(multiRequest).next();
	}

}
