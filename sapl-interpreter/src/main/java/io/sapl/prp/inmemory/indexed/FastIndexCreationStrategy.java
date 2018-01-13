package io.sapl.prp.inmemory.indexed;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import io.sapl.grammar.sapl.SAPL;

public class FastIndexCreationStrategy implements IndexCreationStrategy {

	@Override
	public IndexContainer construct(final Map<String, SAPL> documents, final Map<String, DisjunctiveFormula> targets) {
		Map<String, SAPL> idToDocument = new HashMap<>(documents);

		Map<DisjunctiveFormula, Set<String>> volatileFormulaToIds = mapFormulaToDocuments(targets);
		Map<DisjunctiveFormula, Set<String>> tautologicalFormulaToId = extractTautologies(volatileFormulaToIds);
		// Map<DisjunctiveFormula, String> contradictoryFormulaToId =
		extractContradictions(volatileFormulaToIds);

		Set<SAPL> tautologicalDocuments = linkFormulaToDocuments(tautologicalFormulaToId, idToDocument);

		Map<ConjunctiveClause, Set<DisjunctiveFormula>> clauseToFormulas = mapClauseToFormulas(
				volatileFormulaToIds.keySet());

		Collection<Variable> data = collectData(volatileFormulaToIds.keySet());
		List<Variable> variableOrder = createVariableOrder(data);
		BiMap<ConjunctiveClause, Integer> clauseToIndexBiMap = addBitSetInformation(variableOrder);

		Map<Integer, Set<SAPL>> indexToDocuments = mapIndexToDocuments(clauseToIndexBiMap, clauseToFormulas,
				volatileFormulaToIds, idToDocument);

		int[] indexToLengthTemplate = mapIndexToLength(clauseToIndexBiMap.inverse());
		int[] indexToOccurencesTemplate = mapIndexToOccurences(clauseToIndexBiMap.inverse(), clauseToFormulas);

		List<Set<Integer>> indexToAssociatedIndexes = mapIndexToAllAssociatedIndexes(clauseToIndexBiMap,
				clauseToFormulas);

		return new FastIndexContainer(flattenIndexMap(clauseToIndexBiMap.inverse()), indexToLengthTemplate,
				variableOrder, flattenIndexMap(indexToDocuments), indexToOccurencesTemplate, indexToAssociatedIndexes,
				tautologicalDocuments);
	}

	private static List<Set<Integer>> mapIndexToAllAssociatedIndexes(
			BiMap<ConjunctiveClause, Integer> clauseToIndexBiMap,
			final Map<ConjunctiveClause, Set<DisjunctiveFormula>> clauseToFormulas) {
		final Map<Integer, ConjunctiveClause> indexToClause = clauseToIndexBiMap.inverse();
		final List<Set<Integer>> result = new ArrayList<>(Collections.nCopies(indexToClause.size(), null));
		indexToClause.forEach((key, value) -> {
			Set<Integer> indexes = new HashSet<>();
			for (DisjunctiveFormula formula : clauseToFormulas.get(value)) {
				for (ConjunctiveClause clause : formula.getClauses()) {
					indexes.add(clauseToIndexBiMap.get(clause));
				}
			}
			result.set(key, indexes);
		});
		return result;
	}

	private static BiMap<ConjunctiveClause, Integer> addBitSetInformation(final List<Variable> variableOrder) {
		BiMap<ConjunctiveClause, Integer> result = HashBiMap.create();
		int i = 0;
		for (Variable variable : variableOrder) {
			for (ConjunctiveClause clause : variable.getSetOfUnsatisfiableClausesIfTrue()) {
				Integer index = result.get(clause);
				if (index == null || !result.containsKey(clause)) {
					index = i;
					result.put(clause, index);
					i += 1;
				}
				variable.getUnsatisfiedCandidatesWhenTrue().set(index);
				variable.addToSetOfSatisfiableCandidatesWhenFalse(index);
			}
			for (ConjunctiveClause clause : variable.getSetOfUnsatisfiableClausesIfFalse()) {
				Integer index = result.get(clause);
				if (index == null || !result.containsKey(clause)) {
					index = i;
					result.put(clause, index);
					i += 1;
				}
				variable.getUnsatisfiedCandidatesWhenFalse().set(index);
				variable.addToSetOfSatisfiableCandidatesWhenTrue(index);
			}
			variable.getOccurences().or(variable.getUnsatisfiedCandidatesWhenTrue());
			variable.getOccurences().or(variable.getUnsatisfiedCandidatesWhenFalse());
		}
		return result;
	}

	private static Collection<Variable> collectData(final Collection<DisjunctiveFormula> formulas) {
		Map<Bool, Variable> boolToVariable = new HashMap<>();
		Set<Bool> negativesGroupedByFormula = new HashSet<>();
		Set<Bool> positivesGroupedByFormula = new HashSet<>();
		for (DisjunctiveFormula formula : formulas) {
			negativesGroupedByFormula.clear();
			positivesGroupedByFormula.clear();
			for (ConjunctiveClause clause : formula.getClauses()) {
				List<Literal> literals = clause.getLiterals();
				final int sizeOfClause = literals.size();
				for (Literal literal : literals) {
					collectDataImpl(literal, clause, boolToVariable, negativesGroupedByFormula,
							positivesGroupedByFormula, sizeOfClause);
				}
			}
		}
		for (Variable variable : boolToVariable.values()) {
			double sum = variable.getClauseRelevancesList().stream().mapToDouble(x -> x).sum();
			sum /= (variable.getNumberOfPositives() + variable.getNumberOfNegatives());
			variable.setRelevance(sum);
		}
		return boolToVariable.values();
	}

	private static void collectDataImpl(final Literal literal, final ConjunctiveClause clause,
			final Map<Bool, Variable> boolToVariable, final Set<Bool> negativesGroupedByFormula,
			final Set<Bool> positivesGroupedByFormula, final int sizeOfClause) {
		Bool bool = literal.getBool();
		Variable variable = boolToVariable.get(bool);
		if (variable == null || !boolToVariable.containsKey(bool)) {
			variable = new Variable(bool);
			boolToVariable.put(bool, variable);
		}
		variable.addToClauseRelevanceList(1.0 / sizeOfClause);
		if (literal.isNegated()) {
			variable.addToSetOfUnsatisfiableClausesIfTrue(clause);
			variable.incNumberOfNegatives();
			if (!negativesGroupedByFormula.contains(bool)) {
				negativesGroupedByFormula.add(bool);
				variable.incGroupedNumberOfNegatives();
			}
		} else {
			variable.addToSetOfUnsatisfiableClausesIfFalse(clause);
			variable.incNumberOfPositives();
			if (!positivesGroupedByFormula.contains(bool)) {
				positivesGroupedByFormula.add(bool);
				variable.incGroupedNumberOfPositives();
			}
		}
	}

	private static double createScore(final Variable variable) {
		final double square = 2.0;
		final double groupedPositives = variable.getGroupedNumberOfPositives();
		final double groupedNegatives = variable.getGroupedNumberOfNegatives();
		final double relevance = variable.getRelevance();
		final double costs = 1.0;

		return ((Math.pow(relevance, square - relevance) * (groupedPositives + groupedNegatives)) / costs) * (square
				- Math.pow((groupedPositives - groupedNegatives) / (groupedPositives + groupedNegatives), square));
	}

	private static List<Variable> createVariableOrder(final Collection<Variable> data) {
		List<Variable> result = new ArrayList<>(data);
		for (Variable variable : result) {
			variable.setEnergyScore(createScore(variable));
		}
		Collections.sort(result, Collections.reverseOrder());
		return result;
	}

	private static Map<DisjunctiveFormula, Set<String>> extractContradictions(
			final Map<DisjunctiveFormula, Set<String>> targetFormulas) {
		Map<DisjunctiveFormula, Set<String>> result = new HashMap<>();
		Iterator<Map.Entry<DisjunctiveFormula, Set<String>>> iter = targetFormulas.entrySet().iterator();
		while (iter.hasNext()) {
			Map.Entry<DisjunctiveFormula, Set<String>> entry = iter.next();
			DisjunctiveFormula formula = entry.getKey();
			if (formula.isImmutable() && !formula.evaluate()) {
				result.put(formula, entry.getValue());
				iter.remove();
			}
		}
		return result;
	}

	private static Map<DisjunctiveFormula, Set<String>> extractTautologies(
			final Map<DisjunctiveFormula, Set<String>> targetFormulas) {
		Map<DisjunctiveFormula, Set<String>> result = new HashMap<>();
		Iterator<Map.Entry<DisjunctiveFormula, Set<String>>> iter = targetFormulas.entrySet().iterator();
		while (iter.hasNext()) {
			Map.Entry<DisjunctiveFormula, Set<String>> entry = iter.next();
			DisjunctiveFormula formula = entry.getKey();
			if (formula.isImmutable() && formula.evaluate()) {
				result.put(formula, entry.getValue());
				iter.remove();
			}
		}
		return result;
	}

	private static <T> List<T> flattenIndexMap(final Map<Integer, T> data) {
		final List<T> result = new ArrayList<>(Collections.nCopies(data.size(), null));
		data.forEach(result::set);
		return result;
	}

	private static Set<SAPL> linkFormulaToDocuments(final Map<DisjunctiveFormula, Set<String>> formulaToId,
			final Map<String, SAPL> idToDocument) {
		Set<SAPL> result = new HashSet<>();
		for (Set<String> ids : formulaToId.values()) {
			for (String id : ids) {
				result.add(idToDocument.get(id));
			}
		}
		return result;
	}

	private static Map<ConjunctiveClause, Set<DisjunctiveFormula>> mapClauseToFormulas(
			final Collection<DisjunctiveFormula> formulas) {
		Map<ConjunctiveClause, Set<DisjunctiveFormula>> result = new HashMap<>();
		for (DisjunctiveFormula formula : formulas) {
			for (ConjunctiveClause clause : formula.getClauses()) {
				Set<DisjunctiveFormula> set = result.get(clause);
				if (set == null || !result.containsKey(clause)) {
					set = new HashSet<>();
					result.put(clause, set);
				}
				set.add(formula);
			}
		}
		return result;
	}

	private static Map<DisjunctiveFormula, Set<String>> mapFormulaToDocuments(
			final Map<String, DisjunctiveFormula> documents) {
		Map<DisjunctiveFormula, Set<String>> result = new HashMap<>();
		for (Map.Entry<String, DisjunctiveFormula> entry : documents.entrySet()) {
			DisjunctiveFormula formula = entry.getValue();
			Set<String> set = result.get(formula);
			if (set == null || !result.containsKey(formula)) {
				set = new HashSet<>();
				result.put(formula, set);
			}
			set.add(entry.getKey());
		}
		return result;
	}

	private static Map<Integer, Set<SAPL>> mapIndexToDocuments(final Map<ConjunctiveClause, Integer> clauseToIndex,
			final Map<ConjunctiveClause, Set<DisjunctiveFormula>> clauseToFormulas,
			final Map<DisjunctiveFormula, Set<String>> formulaToIds, final Map<String, SAPL> idToDocument) {
		Map<Integer, Set<SAPL>> result = new HashMap<>();
		for (Map.Entry<ConjunctiveClause, Set<DisjunctiveFormula>> entry : clauseToFormulas.entrySet()) {
			final Set<SAPL> documents = new HashSet<>();
			for (DisjunctiveFormula formula : entry.getValue()) {
				formulaToIds.get(formula).forEach(id -> documents.add(idToDocument.get(id)));
			}
			result.put(clauseToIndex.get(entry.getKey()), documents);
		}
		return result;
	}

	private static int[] mapIndexToLength(final Map<Integer, ConjunctiveClause> indexToClause) {
		final int[] result = new int[indexToClause.size()];
		indexToClause.forEach((key, value) -> result[key] = value.size());
		return result;
	}

	private static int[] mapIndexToOccurences(final Map<Integer, ConjunctiveClause> indexToClause,
			final Map<ConjunctiveClause, Set<DisjunctiveFormula>> clauseToFormulas) {
		final int[] result = new int[indexToClause.size()];
		indexToClause.forEach((key, value) -> result[key] = clauseToFormulas.get(value).size());
		return result;
	}
}
