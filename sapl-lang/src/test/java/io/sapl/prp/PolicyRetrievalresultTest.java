package io.sapl.prp;

import io.sapl.grammar.sapl.SAPL;
import lombok.val;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PolicyRetrievalresultTest {

    static PolicyRetrievalResult empty;
    static PolicyRetrievalResult error;
    static PolicyRetrievalResult invalid;
    static PolicyRetrievalResult match;
    static PolicyRetrievalResult otherMatch;
    static PolicyRetrievalResult nullResult;

    @BeforeAll
    static void beforeClass() throws Exception {
        val saplMock = mock(SAPL.class);
        when(saplMock.toString()).thenReturn("SAPL");

        empty = new PolicyRetrievalResult();
        error = new PolicyRetrievalResult().withError();
        invalid = new PolicyRetrievalResult().withInvalidState();
        match = new PolicyRetrievalResult().withMatch(saplMock);
        otherMatch = new PolicyRetrievalResult().withMatch(mock(SAPL.class));
        nullResult = new PolicyRetrievalResult(null, false, false);
    }

    @Test
    void equalsTest() {
        assertThat(empty.equals(empty), is(true));
        assertThat(empty.equals(invalid), is(true));
        assertThat(empty.equals(error), is(false));
        assertThat(empty.equals(match), is(false));

        assertThat(empty.equals("match"), is(false));

        assertThat(nullResult.equals(invalid), is(false));
        assertThat(error.equals(nullResult), is(false));

        assertThat(match.equals(nullResult), is(false));


        try (MockedStatic<EcoreUtil> mockedEcore = Mockito.mockStatic(EcoreUtil.class)) {
            mockedEcore.when(() -> EcoreUtil.equals(anyList(), anyList())).thenReturn(false);

            assertThat(match.equals(otherMatch), is(false));
        }
    }


    @Test
    void toStringTest() {
        assertThat(empty.toString(), is("PolicyRetrievalResult(matchingDocuments=[], errorsInTarget=false)"));
        assertThat(error.toString(), is("PolicyRetrievalResult(matchingDocuments=[], errorsInTarget=true)"));
        assertThat(invalid.toString(), is("PolicyRetrievalResult(matchingDocuments=[], errorsInTarget=false)"));
        assertThat(match.toString(), is("PolicyRetrievalResult(matchingDocuments=[SAPL], errorsInTarget=false)"));
    }

    @Test
    void hashCodeTest() {
        assertThat(empty.hashCode(), is(3637));
        assertThat(error.hashCode(), is(3619));
        assertThat(invalid.hashCode(), is(3637));
    }
}
