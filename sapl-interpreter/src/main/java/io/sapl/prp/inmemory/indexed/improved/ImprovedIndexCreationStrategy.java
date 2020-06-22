package io.sapl.prp.inmemory.indexed.improved;

import com.google.common.collect.ImmutableMap;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.prp.inmemory.indexed.Bool;
import io.sapl.prp.inmemory.indexed.ConjunctiveClause;
import io.sapl.prp.inmemory.indexed.DisjunctiveFormula;
import io.sapl.prp.inmemory.indexed.IndexContainer;
import io.sapl.prp.inmemory.indexed.IndexCreationStrategy;
import io.sapl.prp.inmemory.indexed.Literal;
import io.sapl.prp.inmemory.indexed.Variable;
import io.sapl.prp.inmemory.indexed.VariableInfo;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class ImprovedIndexCreationStrategy implements IndexCreationStrategy {

    @Override
    public IndexContainer construct(Map<String, SAPL> documents, Map<String, DisjunctiveFormula> targets) {
        Map<String, SAPL> idToDocument = ImmutableMap.copyOf(documents);
        Map<DisjunctiveFormula, Set<SAPL>> formulaToDocuments = mapFormulaToDocuments(targets, idToDocument);

        Map<ConjunctiveClause, Set<DisjunctiveFormula>> clauseToFormulas = mapClauseToFormulas(
                formulaToDocuments.keySet());

//        Set<ConjunctiveClause> conjunctiveClauseSet = clauseToFormulas.keySet();
        Map<ConjunctiveClause, Integer> numberOfFormulasWithConjunction = clauseToFormulas.entrySet().stream()
                .collect(Collectors.toMap(Entry::getKey,
                        conjunctiveClauseSetEntry -> conjunctiveClauseSetEntry.getValue().size()));

        Map<ConjunctiveClause, Set<MTuple>> conjunctionsInFormulasReferencingConjunction =
                createConjunctionTupleMap(clauseToFormulas);

        Map<Bool, VariableInfo> variableInfo = getVariableInfo(formulaToDocuments.keySet());
        Set<Bool> boolSet = variableInfo.keySet();

        //TODO think of making all these maps immutable
        return new ImprovedIndexContainer(boolSet,
                numberOfFormulasWithConjunction, conjunctionsInFormulasReferencingConjunction,
                clauseToFormulas, formulaToDocuments, variableInfo);
    }

    private Map<ConjunctiveClause, Set<MTuple>> createConjunctionTupleMap(Map<ConjunctiveClause,
            Set<DisjunctiveFormula>> clauseToFormulas) {

        Map<ConjunctiveClause, Set<MTuple>> result = new HashMap<>();

        for (Entry<ConjunctiveClause, Set<DisjunctiveFormula>> entry : clauseToFormulas.entrySet()) {
            Set<MTuple> tuplesFromFormulas = new HashSet<>();

            //formulas containing outerClause
            for (DisjunctiveFormula formula : entry.getValue()) {
                //iterate over all inner clauses of the formula
                tuplesFromFormulas.addAll(formula.getClauses().stream()
                        //count how many formulas contain the clauseOfFormular
                        .map(clauseOfFormular -> new MTuple(clauseOfFormular, clauseToFormulas.get(clauseOfFormular)
                                .size()))
                        .collect(Collectors.toSet()));

            }

            Set<MTuple> mTuplesOfClause = result.computeIfAbsent(entry.getKey(), k -> new HashSet<>());
            mTuplesOfClause.addAll(tuplesFromFormulas);

        }

        return result;
    }

    private static Map<DisjunctiveFormula, Set<SAPL>> mapFormulaToDocuments(
            final Map<String, DisjunctiveFormula> targets, final Map<String, SAPL> documents) {
        Map<DisjunctiveFormula, Set<SAPL>> result = new HashMap<>();

        for (Map.Entry<String, DisjunctiveFormula> entry : targets.entrySet()) {
            DisjunctiveFormula formula = entry.getValue();

            Set<SAPL> set = result.computeIfAbsent(formula, k -> new HashSet<>());
            set.add(documents.get(entry.getKey()));
        }
        return result;
    }

    private static Map<ConjunctiveClause, Set<DisjunctiveFormula>> mapClauseToFormulas(
            final Collection<DisjunctiveFormula> formulas) {
        Map<ConjunctiveClause, Set<DisjunctiveFormula>> result = new HashMap<>();

        for (DisjunctiveFormula formula : formulas) {
            for (ConjunctiveClause clause : formula.getClauses()) {
                Set<DisjunctiveFormula> set = result.computeIfAbsent(clause, k -> new HashSet<>());
                set.add(formula);
            }
        }
        return result;
    }

    // copied & modified from FastIndexCreationStrategy
    private static Map<Bool, VariableInfo> getVariableInfo(final Collection<DisjunctiveFormula> formulas) {
        Map<Bool, VariableInfo> boolToVariableInfo = new HashMap<>();

        for (DisjunctiveFormula formula : formulas) {
            for (ConjunctiveClause clause : formula.getClauses()) {
                for (Literal literal : clause.getLiterals()) {
                    collectDataImpl(literal, clause, boolToVariableInfo);
                }
            }
        }

        return boolToVariableInfo;
    }

    private static void collectDataImpl(final Literal literal, final ConjunctiveClause clause,
                                        final Map<Bool, VariableInfo> boolToVariableInfo) {
        Bool bool = literal.getBool();
        VariableInfo variableInfo = boolToVariableInfo
                .computeIfAbsent(bool, k -> new VariableInfo(new Variable(bool)));

        if (literal.isNegated()) {
            variableInfo.addToSetOfUnsatisfiableClausesIfTrue(clause);
            variableInfo.incNumberOfNegatives();
        } else {
            variableInfo.addToSetOfUnsatisfiableClausesIfFalse(clause);
            variableInfo.incNumberOfPositives();
        }
    }
}
