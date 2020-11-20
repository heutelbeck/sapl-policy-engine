package io.sapl.integrationtest;

import java.util.HashMap;

import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import io.sapl.api.interpreter.SAPLInterpreter;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.prp.PolicyRetrievalResult;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.reimpl.prp.GenericInMemoryIndexedPolicyRetrievalPoint;
import io.sapl.reimpl.prp.ImmutableParsedDocumentIndex;
import io.sapl.reimpl.prp.filesystem.FileSystemPrpUpdateEventSource;
import io.sapl.reimpl.prp.index.canonical.CanonicalImmutableParsedDocumentIndex;

@Ignore
public class IntegrationTest {

	@Rule
	public Timeout globalTimeout = Timeout.seconds(10);

	private SAPLInterpreter interpreter;
	private ImmutableParsedDocumentIndex seedIndex;
	private static final EvaluationContext PDP_SCOPED_EVALUATION_CONTEXT = new EvaluationContext(
			new AnnotationAttributeContext(), new AnnotationFunctionContext(), new HashMap<>());
	private static final AuthorizationSubscription EMPTY_SUBSCRIPTION = AuthorizationSubscription.of(null, null, null);

	@Before
	public void setUp() {
		interpreter = new DefaultSAPLInterpreter();
		seedIndex = new CanonicalImmutableParsedDocumentIndex(PDP_SCOPED_EVALUATION_CONTEXT);
	}

	@Test
	public void return_empty_result_when_no_documents_are_published() {
		var source = new FileSystemPrpUpdateEventSource("src/test/resources/it/empty", interpreter);
		source.getUpdates().log().blockFirst();
		var prp = new GenericInMemoryIndexedPolicyRetrievalPoint(seedIndex, source);
		var evaluationCtx = new EvaluationContext(new AnnotationAttributeContext(), new AnnotationFunctionContext(),
				new HashMap<>());
		evaluationCtx = evaluationCtx.forAuthorizationSubscription(EMPTY_SUBSCRIPTION);

		PolicyRetrievalResult result = prp.retrievePolicies(evaluationCtx).blockFirst();

		Assertions.assertThat(result).isNotNull();
		Assertions.assertThat(result.getMatchingDocuments()).isEmpty();
		Assertions.assertThat(result.isErrorsInTarget()).isFalse();
	}

	@Test
	public void throw_exception_for_invalid_document() {
		var source = new FileSystemPrpUpdateEventSource("src/test/resources/it/invalid", interpreter);

		Assertions.assertThatExceptionOfType(RuntimeException.class)
				.isThrownBy(() -> new GenericInMemoryIndexedPolicyRetrievalPoint(seedIndex, source));

	}

	@Test
	public void return_error_flag_when_evaluation_throws_exception() {
		var source = new FileSystemPrpUpdateEventSource("src/test/resources/it/error", interpreter);
		var prp = new GenericInMemoryIndexedPolicyRetrievalPoint(seedIndex, source);
		var evaluationCtx = new EvaluationContext(new AnnotationAttributeContext(), new AnnotationFunctionContext(),
				new HashMap<>());
		evaluationCtx = evaluationCtx.forAuthorizationSubscription(EMPTY_SUBSCRIPTION);
		PolicyRetrievalResult result = prp.retrievePolicies(evaluationCtx).blockFirst();

		Assertions.assertThat(result).isNotNull();
		Assertions.assertThat(result.getMatchingDocuments()).isEmpty();
		Assertions.assertThat(result.isErrorsInTarget()).isTrue();
	}

	@Test
	public void return_matching_document_for_valid_subscription() {
		var source = new FileSystemPrpUpdateEventSource("src/test/resources/it/policies", interpreter);
		var prp = new GenericInMemoryIndexedPolicyRetrievalPoint(seedIndex, source);
		var authzSubscription = AuthorizationSubscription.of(null, "read", null);

		var evaluationCtx1 = new EvaluationContext(new AnnotationAttributeContext(), new AnnotationFunctionContext(),
				new HashMap<>());
		evaluationCtx1 = evaluationCtx1.forAuthorizationSubscription(authzSubscription);
		PolicyRetrievalResult result = prp.retrievePolicies(evaluationCtx1).blockFirst();

		Assertions.assertThat(result).isNotNull();
		Assertions.assertThat(result.getMatchingDocuments()).hasSize(1);
		Assertions.assertThat(result.isErrorsInTarget()).isFalse();

		Assertions.assertThat(result.getMatchingDocuments().stream().map(doc -> (SAPL) doc).findFirst().get()
				.getPolicyElement().getSaplName()).isEqualTo("policy read");

		authzSubscription = AuthorizationSubscription.of("Willi", "eat", "icecream");

		var evaluationCtx2 = new EvaluationContext(new AnnotationAttributeContext(), new AnnotationFunctionContext(),
				new HashMap<>());
		evaluationCtx2 = evaluationCtx2.forAuthorizationSubscription(authzSubscription);

		result = prp.retrievePolicies(evaluationCtx2).blockFirst();

		Assertions.assertThat(result).isNotNull();
		Assertions.assertThat(result.getMatchingDocuments()).hasSize(1);
		Assertions.assertThat(result.isErrorsInTarget()).isFalse();

		Assertions.assertThat(result.getMatchingDocuments().stream().map(doc -> (SAPL) doc).findFirst().get()
				.getPolicyElement().getSaplName()).isEqualTo("policy eat icecream");
	}

	@Test
	public void return_empty_result_for_non_matching_subscription() {
		var source = new FileSystemPrpUpdateEventSource("src/test/resources/it/policies", interpreter);
		var prp = new GenericInMemoryIndexedPolicyRetrievalPoint(seedIndex, source);
		var evaluationCtx = new EvaluationContext(new AnnotationAttributeContext(), new AnnotationFunctionContext(),
				new HashMap<>());
		evaluationCtx = evaluationCtx.forAuthorizationSubscription(EMPTY_SUBSCRIPTION);

		PolicyRetrievalResult result = prp.retrievePolicies(evaluationCtx).blockFirst();

		Assertions.assertThat(result).isNotNull();
		Assertions.assertThat(result.getMatchingDocuments()).isEmpty();
		Assertions.assertThat(result.isErrorsInTarget()).isFalse();
	}

}
