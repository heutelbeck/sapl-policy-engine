package io.sapl.reimpl.prp.index.canonical;

import io.sapl.grammar.sapl.SAPL;
import io.sapl.prp.inmemory.indexed.Bitmask;
import io.sapl.prp.inmemory.indexed.DisjunctiveFormula;
import io.sapl.prp.inmemory.indexed.improved.CTuple;
import io.sapl.prp.inmemory.indexed.improved.Predicate;
import lombok.Value;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Value
public class CanonicalIndexDataContainer {

    private final Map<DisjunctiveFormula, Set<SAPL>> formulaToDocuments;

    private final List<Predicate> predicateOrder;

    private final List<Set<DisjunctiveFormula>> relatedFormulas;

    private final Map<DisjunctiveFormula, Bitmask> relatedCandidates;

    private final Map<Integer, Set<CTuple>> conjunctionsInFormulasReferencingConjunction;

    private final int[] numberOfLiteralsInConjunction;

    private final int[] numberOfFormulasWithConjunction;

}
