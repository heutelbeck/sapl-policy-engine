package io.sapl.integrationtest;

import io.sapl.api.interpreter.SAPLInterpreter;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.prp.PolicyRetrievalResult;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.reimpl.prp.GenericInMemoryIndexedPolicyRetrievalPoint;
import io.sapl.reimpl.prp.ImmutableParsedDocumentIndex;
import io.sapl.reimpl.prp.filesystem.FileSystemPrpUpdateEventSource;
import io.sapl.reimpl.prp.index.canonical.CanonicalImmutableParsedDocumentIndex;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.HashMap;

public class IntegrationTest {

    @Rule
    public Timeout globalTimeout = Timeout.seconds(60);

    private SAPLInterpreter interpreter;

    private ImmutableParsedDocumentIndex seedIndex;

    private static final AuthorizationSubscription EMPTY_SUBSCRIPTION = AuthorizationSubscription.of(null, null, null);


    @Before
    public void setUp() {
        interpreter = new DefaultSAPLInterpreter();
        seedIndex = new CanonicalImmutableParsedDocumentIndex();
    }


    @Test
    public void return_empty_result_when_no_documents_are_published() {
        var source = new FileSystemPrpUpdateEventSource("src/test/resources/it/empty", interpreter);
        var prp = new GenericInMemoryIndexedPolicyRetrievalPoint(seedIndex, source);

        PolicyRetrievalResult result =
                prp.retrievePolicies(EMPTY_SUBSCRIPTION, new AnnotationFunctionContext(), new HashMap<>()).blockFirst();

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
    public void return_error_flag_when_evaluation_throws_exception(){
        var source = new FileSystemPrpUpdateEventSource("src/test/resources/it/error", interpreter);
        var prp = new GenericInMemoryIndexedPolicyRetrievalPoint(seedIndex, source);

        PolicyRetrievalResult result =
                prp.retrievePolicies(EMPTY_SUBSCRIPTION, new AnnotationFunctionContext(), new HashMap<>()).blockFirst();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result.getMatchingDocuments()).isEmpty();
        Assertions.assertThat(result.isErrorsInTarget()).isTrue();
    }

    @Test
    public void return_matching_document_for_valid_subscription() {
        var source = new FileSystemPrpUpdateEventSource("src/test/resources/it/policies", interpreter);
        var prp = new GenericInMemoryIndexedPolicyRetrievalPoint(seedIndex, source);
        var authzSubscription = AuthorizationSubscription.of(null, "read", null);


        PolicyRetrievalResult result =
                prp.retrievePolicies(authzSubscription, new AnnotationFunctionContext(), new HashMap<>()).blockFirst();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result.getMatchingDocuments()).hasSize(1);
        Assertions.assertThat(result.isErrorsInTarget()).isFalse();

        Assertions.assertThat(result.getMatchingDocuments().stream().findFirst().get().getPolicyElement().getSaplName())
                .isEqualTo("policy read");

        authzSubscription = AuthorizationSubscription.of("Willi", "eat", "icecream");

        result =
                prp.retrievePolicies(authzSubscription, new AnnotationFunctionContext(), new HashMap<>()).blockFirst();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result.getMatchingDocuments()).hasSize(1);
        Assertions.assertThat(result.isErrorsInTarget()).isFalse();

        Assertions.assertThat(result.getMatchingDocuments().stream().findFirst().get().getPolicyElement().getSaplName())
                .isEqualTo("policy eat icecream");
    }

    @Test
    public void return_empty_result_for_non_matching_subscription() {
        var source = new FileSystemPrpUpdateEventSource("src/test/resources/it/policies", interpreter);
        var prp = new GenericInMemoryIndexedPolicyRetrievalPoint(seedIndex, source);

        PolicyRetrievalResult result =
                prp.retrievePolicies(EMPTY_SUBSCRIPTION, new AnnotationFunctionContext(), new HashMap<>()).blockFirst();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result.getMatchingDocuments()).isEmpty();
        Assertions.assertThat(result.isErrorsInTarget()).isFalse();
    }

}
