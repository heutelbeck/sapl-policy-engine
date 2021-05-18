package io.sapl.test.pdp;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Paths;
import java.util.HashMap;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.prp.PolicyRetrievalPoint;
import io.sapl.prp.PolicyRetrievalResult;
import io.sapl.test.SaplTestException;
import io.sapl.test.coverage.api.CoverageHitRecorder;
import io.sapl.test.lang.TestSaplInterpreter;

public class ClasspathPolicyRetrievalPointTest {

	private static final AuthorizationSubscription EMPTY_SUBSCRIPTION = AuthorizationSubscription.of(null, null, null);
	private CoverageHitRecorder recorder;
	private SAPLInterpreter interpreter;
	private EvaluationContext ctx;
	
	@BeforeEach
	void setup() {
		recorder = Mockito.mock(CoverageHitRecorder.class);
		interpreter = new TestSaplInterpreter(recorder);
		ctx = new EvaluationContext(new AnnotationAttributeContext(), new AnnotationFunctionContext(), new HashMap<>());
	}
	
	@Test
	void test() {
		PolicyRetrievalPoint prp = new ClasspathPolicyRetrievalPoint(Paths.get("policiesIT"), this.interpreter);
		var authzSubscription = AuthorizationSubscription.of("WILLI", "access", "foo", "");
		var prpResult = prp.retrievePolicies(ctx.forAuthorizationSubscription(authzSubscription)).blockFirst();
		Assertions.assertThat(prpResult.getMatchingDocuments().size()).isEqualTo(2);
	}
	
	
	@Test
	void test_invalidPath() {
		assertThrows(SaplTestException.class, () -> new ClasspathPolicyRetrievalPoint(Paths.get("notExisting"), this.interpreter));
	}
	
	
    @Test
    void return_empty_result_when_no_documents_are_published() {
    	PolicyRetrievalPoint prp = new ClasspathPolicyRetrievalPoint(Paths.get("it"), this.interpreter);
        var evaluationCtx = ctx.forAuthorizationSubscription(EMPTY_SUBSCRIPTION);

        PolicyRetrievalResult result = prp.retrievePolicies(evaluationCtx).blockFirst();

        assertThat(result, notNullValue());
        assertThat(result.getMatchingDocuments(), empty());
        assertThat(result.isErrorsInTarget(), is(false));
        assertThat(result.isPrpValidState(), is(true));
    }

    @Test
    void return_fail_fast_for_invalid_document() {
        assertThrows(PolicyEvaluationException.class, () ->  new ClasspathPolicyRetrievalPoint(Paths.get("it/invalid"), this.interpreter));
    }

    @Test
    void return_error_flag_when_evaluation_throws_exception() {
    	PolicyRetrievalPoint prp = new ClasspathPolicyRetrievalPoint(Paths.get("it/error"), this.interpreter);
        var evaluationCtx = ctx.forAuthorizationSubscription(EMPTY_SUBSCRIPTION);

        PolicyRetrievalResult result = prp.retrievePolicies(evaluationCtx).blockFirst();


        assertThat(result, notNullValue());
        assertThat(result.getMatchingDocuments(), empty());
        assertThat(result.isErrorsInTarget(), is(true));
        assertThat(result.isPrpValidState(), is(true));
    }

    @Test
    void return_matching_document_for_valid_subscription() {
    	PolicyRetrievalPoint prp = new ClasspathPolicyRetrievalPoint(Paths.get("it/policies"), this.interpreter);
    	
        var authzSubscription1 = AuthorizationSubscription.of(null, "read", null);
        PolicyRetrievalResult result1 = prp.retrievePolicies(ctx.forAuthorizationSubscription(authzSubscription1)).blockFirst();


        assertThat(result1, notNullValue());
        assertThat(result1.getMatchingDocuments().size(), is(1));
        assertThat(result1.isErrorsInTarget(), is(false));

        assertThat(result1.getMatchingDocuments().stream().map(doc -> (SAPL) doc).findFirst().get().getPolicyElement()
                .getSaplName(), is("policy read"));


        var authzSubscription2 = AuthorizationSubscription.of("Willi", "eat", "icecream");

        PolicyRetrievalResult result2 = prp.retrievePolicies(ctx.forAuthorizationSubscription(authzSubscription2)).blockFirst();

        assertThat(result2, notNullValue());
        assertThat(result2.getMatchingDocuments().size(), is(1));
        assertThat(result2.isErrorsInTarget(), is(false));
        assertThat(result2.isPrpValidState(), is(true));

        assertThat(result2.getMatchingDocuments().stream().map(doc -> (SAPL) doc).findFirst().get().getPolicyElement()
                .getSaplName(), is("policy eat icecream"));
    }

    @Test
    void return_empty_result_for_non_matching_subscription() {
    	PolicyRetrievalPoint prp = new ClasspathPolicyRetrievalPoint(Paths.get("it/policies"), this.interpreter);
        var evaluationCtx = ctx.forAuthorizationSubscription(EMPTY_SUBSCRIPTION);

        PolicyRetrievalResult result = prp.retrievePolicies(evaluationCtx).blockFirst();

        assertThat(result, notNullValue());
        assertThat(result.getMatchingDocuments(), empty());
        assertThat(result.isErrorsInTarget(), is(false));
        assertThat(result.isPrpValidState(), is(true));
    }

}
