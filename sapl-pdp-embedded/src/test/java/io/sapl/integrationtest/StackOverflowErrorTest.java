package io.sapl.integrationtest;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

import java.util.HashMap;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.grammar.sapl.AuthorizationDecisionEvaluable;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.prp.PolicyRetrievalResult;
import io.sapl.prp.PrpUpdateEvent;
import io.sapl.prp.filesystem.FileSystemPrpUpdateEventSource;
import io.sapl.prp.index.ImmutableParsedDocumentIndex;
import io.sapl.prp.index.canonical.CanonicalImmutableParsedDocumentIndex;
import io.sapl.prp.index.naive.NaiveImmutableParsedDocumentIndex;
import lombok.val;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

class StackOverflowErrorTest {

    private static final JsonNode EMPTY_NODE = JsonNodeFactory.instance.objectNode();

    @Test
    @Disabled("extract archive overflow.zip before executing the test")
    void provoke_stack_overflow_error_naive() {
        var policyFolder = "src/test/resources/overflow";

        var initializedIndex = IndexFactory.indexByTypeForDocumentsIn(IndexType.NAIVE, policyFolder);

        var request = AuthorizationSubscription.of(EMPTY_NODE, EMPTY_NODE, textNode("resource.044"));

        var subscriptionScopedEvaluationCtx = new EvaluationContext(new AnnotationAttributeContext(), new AnnotationFunctionContext(),
                new HashMap<>()).forAuthorizationSubscription(request);

        var result = Assertions.assertDoesNotThrow(() ->
                initializedIndex.retrievePolicies(subscriptionScopedEvaluationCtx).block()
        );

        assertThat(result, notNullValue());
        assertThat(result.getMatchingDocuments(), not(empty()));
        assertThat(getMatchingPolicyNames(result), Matchers.containsString("unrestricted-resources"));
    }

    @Test
    @Disabled("number of policies in folder is too low (< 5500) to provoke stack overflow, regardless of implementation")
    void provoke_stack_overflow_error_canonical() {
        var policyFolder = "src/test/resources/overflow";

        var initializedIndex = IndexFactory.indexByTypeForDocumentsIn(IndexType.CANONICAL, policyFolder);

        var request = AuthorizationSubscription.of(EMPTY_NODE, EMPTY_NODE, textNode("resource.044"));

        var subscriptionScopedEvaluationCtx = new EvaluationContext(new AnnotationAttributeContext(), new AnnotationFunctionContext(),
                new HashMap<>()).forAuthorizationSubscription(request);

        var result = Assertions.assertDoesNotThrow(() ->
                initializedIndex.retrievePolicies(subscriptionScopedEvaluationCtx).block()
        );

        assertThat(result, notNullValue());
        assertThat(result.getMatchingDocuments(), not(empty()));
        assertThat(getMatchingPolicyNames(result), Matchers.containsString("unrestricted-resources"));
    }


    private String getMatchingPolicyNames(PolicyRetrievalResult result) {
        val sb = new StringBuilder();
        sb.append("[");

        for (AuthorizationDecisionEvaluable decisionEvaluable : result.getMatchingDocuments()) {
            if (decisionEvaluable instanceof SAPL) {
                sb.append(((SAPL) decisionEvaluable).getPolicyElement().getSaplName());
            } else {
                sb.append(decisionEvaluable);
            }
            sb.append(", ");
        }

        sb.append("]");

        return sb.toString().replace(", ]", "]");
    }


    JsonNode textNode(String text) {
        return JsonNodeFactory.instance.textNode(text);
    }


    public enum IndexType {
        NAIVE, CANONICAL
    }

    @Slf4j
    @UtilityClass
    public class IndexFactory {

        private static final EvaluationContext PDP_SCOPED_EVALUATION_CONTEXT =
                new EvaluationContext(new AnnotationAttributeContext(), new AnnotationFunctionContext(), new HashMap<>());


        public ImmutableParsedDocumentIndex indexByTypeForDocumentsIn(IndexType indexType, String policiesFolder) {
            switch (indexType) {
                case NAIVE:
                    return naiveIndexForDocumentsIn(policiesFolder);
                case CANONICAL:
                    //fall through
                default:
                    return canonicalIndexForDocumentsIn(policiesFolder);
            }
        }

        public ImmutableParsedDocumentIndex naiveIndexForDocumentsIn(String policiesFolder) {
            return new NaiveImmutableParsedDocumentIndex().apply(fetchInitialUpdateEvent(policiesFolder));
        }

        public ImmutableParsedDocumentIndex canonicalIndexForDocumentsIn(String policiesFolder) {
            return new CanonicalImmutableParsedDocumentIndex(PDP_SCOPED_EVALUATION_CONTEXT)
                    .apply(fetchInitialUpdateEvent(policiesFolder));
        }

        private PrpUpdateEvent fetchInitialUpdateEvent(String policiesFolder) {
            return new FileSystemPrpUpdateEventSource(policiesFolder, new DefaultSAPLInterpreter()).getUpdates()
                    .doOnNext(update -> log.debug("Initialize index with update event: {}", update)).blockFirst();
        }

    }

}
