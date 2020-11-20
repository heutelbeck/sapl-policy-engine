package io.sapl.interpreter.combinators;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.prp.PolicyRetrievalResult;
import io.sapl.grammar.sapl.AuthorizationDecisionEvaluable;
import reactor.core.publisher.Flux;

public class CombinatorMockUtil {
	public static PolicyRetrievalResult mockPolicyRetrievalResult(boolean errorsInTarget,
			AuthorizationDecision... authorizationDecisions) {
		var documents = new ArrayList<AuthorizationDecisionEvaluable>(authorizationDecisions.length);
		for (var decision : authorizationDecisions) {
			documents.add(mockDocumentEvaluatingTo(decision));
		}
		return new PolicyRetrievalResult(documents, errorsInTarget);
	}

	public static AuthorizationDecisionEvaluable mockDocumentEvaluatingTo(AuthorizationDecision authzDecison) {
		var document = mock(AuthorizationDecisionEvaluable.class);
		when(document.evaluate(any())).thenReturn(Flux.just(authzDecison));
		return document;
	}
}
