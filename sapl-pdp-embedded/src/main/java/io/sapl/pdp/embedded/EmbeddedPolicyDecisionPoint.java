package io.sapl.pdp.embedded;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.functions.FunctionException;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.pdp.Disposable;
import io.sapl.api.pdp.PDPConfigurationException;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.api.pdp.Request;
import io.sapl.api.pdp.Response;
import io.sapl.api.pdp.multirequest.IdentifiableRequest;
import io.sapl.api.pdp.multirequest.IdentifiableResponse;
import io.sapl.api.pdp.multirequest.MultiRequest;
import io.sapl.api.pdp.multirequest.MultiResponse;
import io.sapl.api.pip.AttributeException;
import io.sapl.api.prp.ParsedDocumentIndex;
import io.sapl.api.prp.PolicyRetrievalPoint;
import io.sapl.functions.FilterFunctionLibrary;
import io.sapl.functions.SelectionFunctionLibrary;
import io.sapl.functions.StandardFunctionLibrary;
import io.sapl.functions.TemporalFunctionLibrary;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.combinators.DocumentsCombinator;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.pdp.embedded.config.PDPConfigurationProvider;
import io.sapl.pdp.embedded.config.filesystem.FilesystemPDPConfigurationProvider;
import io.sapl.pdp.embedded.config.resources.ResourcesPDPConfigurationProvider;
import io.sapl.pip.ClockPolicyInformationPoint;
import io.sapl.prp.filesystem.FilesystemPolicyRetrievalPoint;
import io.sapl.prp.inmemory.indexed.FastParsedDocumentIndex;
import io.sapl.prp.inmemory.simple.SimpleParsedDocumentIndex;
import io.sapl.prp.resources.ResourcesPolicyRetrievalPoint;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@Slf4j
public class EmbeddedPolicyDecisionPoint implements PolicyDecisionPoint, Disposable {

	private final FunctionContext functionCtx = new AnnotationFunctionContext();

	private final AttributeContext attributeCtx = new AnnotationAttributeContext();

	private PDPConfigurationProvider configurationProvider;

	private PolicyRetrievalPoint prp;

	private EmbeddedPolicyDecisionPoint() {
		// use Builder to create new instances
	}

	@Override
	public Flux<Response> decide(Request request) {
		LOGGER.trace("|---------------------------");
		LOGGER.trace("|-- PDP Request: {}", request);

		final Flux<DocumentsCombinator> combinatorFlux = configurationProvider
				.getDocumentsCombinator();
		final Flux<Map<String, JsonNode>> variablesFlux = configurationProvider
				.getVariables();

		return Flux.<DocumentsCombinator, Map<String, JsonNode>, Flux<Response>>combineLatest(combinatorFlux, variablesFlux,
				(combinator, variables) -> prp
						.retrievePolicies(request, functionCtx, variables)
						.switchMap(result -> {
							final Collection<SAPL> matchingDocuments = result
									.getMatchingDocuments();
							final boolean errorsInTarget = result.isErrorsInTarget();
							LOGGER.trace("|-- Combine documents of request: {}", request);
							return combinator.combineMatchingDocuments(matchingDocuments,
									errorsInTarget, request, attributeCtx, functionCtx,
									variables);
						}))
				.flatMap(responseFlux -> responseFlux).distinctUntilChanged();
	}

	@Override
	public Flux<IdentifiableResponse> decide(MultiRequest multiRequest) {
		if (multiRequest.hasRequests()) {
			final List<Flux<IdentifiableResponse>> requestIdResponsePairFluxes = new ArrayList<>();
			for (IdentifiableRequest identifiableRequest : multiRequest) {
				final Request request = identifiableRequest.getRequest();
				final Flux<Response> responseFlux = decide(request);
				final Flux<IdentifiableResponse> requestResponsePairFlux = responseFlux
						.map(response -> new IdentifiableResponse(
								identifiableRequest.getRequestId(), response))
						.subscribeOn(Schedulers.newElastic("pdp"));
				requestIdResponsePairFluxes.add(requestResponsePairFlux);
			}
			return Flux.merge(requestIdResponsePairFluxes);
		}
		return Flux.just(IdentifiableResponse.indeterminate());
	}

	@Override
	public Flux<MultiResponse> decideAll(MultiRequest multiRequest) {
		if (multiRequest.hasRequests()) {
			final List<Flux<IdentifiableResponse>> identifiableResponseFluxes = new ArrayList<>();
			for (IdentifiableRequest identifiableRequest : multiRequest) {
				final String requestId = identifiableRequest.getRequestId();
				final Request request = identifiableRequest.getRequest();
				final Flux<Response> responseFlux = decide(request);
				final Flux<IdentifiableResponse> identifiableResponseFlux = responseFlux
						.map(response -> new IdentifiableResponse(requestId, response));
				identifiableResponseFluxes.add(identifiableResponseFlux);
			}
			return Flux.combineLatest(identifiableResponseFluxes, this::collectResponses);
		}
		return Flux.just(MultiResponse.indeterminate());
	}

	private MultiResponse collectResponses(Object[] values) {
		final MultiResponse multiResponse = new MultiResponse();
		for (Object value : values) {
			IdentifiableResponse ir = (IdentifiableResponse) value;
			multiResponse.setResponseForRequestWithId(ir.getRequestId(),
					ir.getResponse());
		}
		return multiResponse;
	}

	@Override
	public void dispose() {
		if (prp instanceof Disposable) {
			((Disposable) prp).dispose();
		}
	}

	public static Builder builder() throws FunctionException, AttributeException {
		return new Builder();
	}

	public static class Builder {

		public enum IndexType {

			SIMPLE, FAST

		}

		private EmbeddedPolicyDecisionPoint pdp = new EmbeddedPolicyDecisionPoint();

		private Builder() throws FunctionException, AttributeException {
			pdp.functionCtx.loadLibrary(new FilterFunctionLibrary());
			pdp.functionCtx.loadLibrary(new SelectionFunctionLibrary());
			pdp.functionCtx.loadLibrary(new StandardFunctionLibrary());
			pdp.functionCtx.loadLibrary(new TemporalFunctionLibrary());

			pdp.attributeCtx
					.loadPolicyInformationPoint(new ClockPolicyInformationPoint());
		}

		public Builder withResourcePDPConfigurationProvider()
				throws PDPConfigurationException, IOException, URISyntaxException {
			pdp.configurationProvider = new ResourcesPDPConfigurationProvider();
			return this;
		}

		public Builder withResourcePDPConfigurationProvider(String resourcePath)
				throws PDPConfigurationException, IOException, URISyntaxException {
			return withResourcePDPConfigurationProvider(
					ResourcesPDPConfigurationProvider.class, resourcePath);
		}

		public Builder withResourcePDPConfigurationProvider(Class<?> clazz,
				String resourcePath)
				throws PDPConfigurationException, IOException, URISyntaxException {
			pdp.configurationProvider = new ResourcesPDPConfigurationProvider(clazz,
					resourcePath);
			return this;
		}

		public Builder withFilesystemPDPConfigurationProvider(String configFolder) {
			pdp.configurationProvider = new FilesystemPDPConfigurationProvider(
					configFolder);
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

		public Builder withResourcePolicyRetrievalPoint(String resourcePath,
				IndexType indexType)
				throws IOException, URISyntaxException, PolicyEvaluationException {
			return withResourcePolicyRetrievalPoint(ResourcesPolicyRetrievalPoint.class,
					resourcePath, indexType);
		}

		public Builder withResourcePolicyRetrievalPoint(Class<?> clazz,
				String resourcePath, IndexType indexType)
				throws IOException, URISyntaxException, PolicyEvaluationException {
			final ParsedDocumentIndex index = getDocumentIndex(indexType);
			pdp.prp = new ResourcesPolicyRetrievalPoint(clazz, resourcePath, index);
			return this;
		}

		public Builder withFilesystemPolicyRetrievalPoint(String policiesFolder,
				IndexType indexType) {
			final ParsedDocumentIndex index = getDocumentIndex(indexType);
			pdp.prp = new FilesystemPolicyRetrievalPoint(policiesFolder, index);
			return this;
		}

		private ParsedDocumentIndex getDocumentIndex(IndexType indexType) {
			switch (indexType) {
			case SIMPLE:
				return new SimpleParsedDocumentIndex();
			case FAST:
				return new FastParsedDocumentIndex(pdp.functionCtx);
			}
			return new SimpleParsedDocumentIndex();
		}

		public EmbeddedPolicyDecisionPoint build()
				throws IOException, URISyntaxException, PolicyEvaluationException {
			if (pdp.prp == null) {
				withResourcePolicyRetrievalPoint();
			}
			return pdp;
		}

	}

}
