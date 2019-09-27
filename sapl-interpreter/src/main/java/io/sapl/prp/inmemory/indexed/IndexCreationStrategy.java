package io.sapl.prp.inmemory.indexed;

import java.util.Map;

import io.sapl.grammar.sapl.SAPL;

public interface IndexCreationStrategy {

	IndexContainer construct(final Map<String, SAPL> documents, final Map<String, DisjunctiveFormula> targets);

}
