package io.sapl.api.prp;

import java.util.Collection;

import io.sapl.grammar.sapl.SAPL;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class PolicyRetrievalResult {
	Collection<SAPL> matchingDocuments;
	boolean errorsInTarget;
}
