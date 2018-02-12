package io.sapl.prp.inmemory.indexed;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableMap;

import io.sapl.grammar.sapl.SAPL;

public class FastIndexCreationStrategy implements IndexCreationStrategy {

	@Override
	public IndexContainer construct(final Map<String, SAPL> documents, final Map<String, DisjunctiveFormula> targets) {
		Map<String, SAPL> idToDocument = ImmutableMap.copyOf(documents);

		Map<DisjunctiveFormula, Set<SAPL>> formulaToDocuments = mapFormulaToDocuments(targets, idToDocument);

		Map<ConjunctiveClause, Set<DisjunctiveFormula>> clauseToFormulas = mapClauseToFormulas(
				formulaToDocuments.keySet());

		Collection<VariableInfo> data = collectData(formulaToDocuments.keySet());
		List<Variable> variableOrder = createVariableOrder(data);
		BiMap<ConjunctiveClause, Integer> clauseToIndexBiMap = createCandidateOrder(data);

		Map<Integer, Set<DisjunctiveFormula>> indexToTargets = mapIndexToTargets(clauseToIndexBiMap, clauseToFormulas);

		Map<DisjunctiveFormula, Bitmask> formulaToClauses = mapFormulaToClauses(formulaToDocuments.keySet(),
				clauseToIndexBiMap);

		int[] indexToLengthTemplate = mapIndexToLength(clauseToIndexBiMap.inverse());
		int[] indexToOccurencesTemplate = mapIndexToOccurences(clauseToIndexBiMap.inverse(), clauseToFormulas);
		int[][] template = { indexToLengthTemplate, indexToOccurencesTemplate };
		AuxiliaryMatrix matrix = new AuxiliaryMatrix(template);

		return new FastIndexContainer(true, variableOrder, formulaToClauses, flattenIndexMap(indexToTargets),
				formulaToDocuments, matrix);
	}

	private static Collection<VariableInfo> collectData(final Collection<DisjunctiveFormula> formulas) {
		Map<Bool, VariableInfo> boolToVariableInfo = new HashMap<>();
		Set<Bool> negativesGroupedByFormula = new HashSet<>();
		Set<Bool> positivesGroupedByFormula = new HashSet<>();
		for (DisjunctiveFormula formula : formulas) {
			negativesGroupedByFormula.clear();
			positivesGroupedByFormula.clear();
			for (ConjunctiveClause clause : formula.getClauses()) {
				List<Literal> literals = clause.getLiterals();
				final int sizeOfClause = literals.size();
				for (Literal literal : literals) {
					collectDataImpl(literal, clause, boolToVariableInfo, negativesGroupedByFormula,
							positivesGroupedByFormula, sizeOfClause);
				}
			}
		}
		for (VariableInfo variableInfo : boolToVariableInfo.values()) {
			double sum = variableInfo.getClauseRelevancesList().stream().mapToDouble(x -> x).sum();
			sum /= (variableInfo.getNumberOfPositives() + variableInfo.getNumberOfNegatives());
			variableInfo.setRelevance(sum);
		}
		return boolToVariableInfo.values();
	}

	private static void collectDataImpl(final Literal literal, final ConjunctiveClause clause,
			final Map<Bool, VariableInfo> boolToVariableInfo, final Set<Bool> negativesGroupedByFormula,
			final Set<Bool> positivesGroupedByFormula, final int sizeOfClause) {
		Bool bool = literal.getBool();
		VariableInfo variableInfo = boolToVariableInfo.get(bool);
		if (variableInfo == null) {
			Variable variable = new Variable(bool);
			variableInfo = new VariableInfo(variable);
			boolToVariableInfo.put(bool, variableInfo);
		}
		variableInfo.addToClauseRelevanceList(1.0 / sizeOfClause);
		if (literal.isNegated()) {
			variableInfo.addToSetOfUnsatisfiableClausesIfTrue(clause);
			variableInfo.incNumberOfNegatives();
			if (!negativesGroupedByFormula.contains(bool)) {
				negativesGroupedByFormula.add(bool);
				variableInfo.incGroupedNumberOfNegatives();
			}
		} else {
			variableInfo.addToSetOfUnsatisfiableClausesIfFalse(clause);
			variableInfo.incNumberOfPositives();
			if (!positivesGroupedByFormula.contains(bool)) {
				positivesGroupedByFormula.add(bool);
				variableInfo.incGroupedNumberOfPositives();
			}
		}
	}

	private static BiMap<ConjunctiveClause, Integer> createCandidateOrder(final Collection<VariableInfo> data) {
		BiMap<ConjunctiveClause, Integer> result = HashBiMap.create();
		int i = 0;
		for (VariableInfo variableInfo : data) {
			Variable variable = variableInfo.getVariable();
			for (ConjunctiveClause clause : variableInfo.getSetOfUnsatisfiableClausesIfTrue()) {
				Integer index = result.get(clause);
				if (index == null) {
					index = i;
					result.put(clause, index);
					i += 1;
				}
				variable.getUnsatisfiedCandidatesWhenTrue().set(index);
				variable.getCandidates().set(index);
			}
			for (ConjunctiveClause clause : variableInfo.getSetOfUnsatisfiableClausesIfFalse()) {
				Integer index = result.get(clause);
				if (index == null || !result.containsKey(clause)) {
					index = i;
					result.put(clause, index);
					i += 1;
				}
				variable.getUnsatisfiedCandidatesWhenFalse().set(index);
				variable.getCandidates().set(index);
			}
		}
		return result;
	}

	private static double createScore(final VariableInfo variableInfo) {
		final double square = 2.0;
		final double groupedPositives = variableInfo.getGroupedNumberOfPositives();
		final double groupedNegatives = variableInfo.getGroupedNumberOfNegatives();
		final double relevance = variableInfo.getRelevance();
		final double costs = 1.0;

		return ((Math.pow(relevance, square - relevance) * (groupedPositives + groupedNegatives)) / costs) * (square
				- Math.pow((groupedPositives - groupedNegatives) / (groupedPositives + groupedNegatives), square));
	}

	private static List<Variable> createVariableOrder(final Collection<VariableInfo> data) {
		List<VariableInfo> infos = new ArrayList<>(data);
		for (VariableInfo variableInfo : data) {
			variableInfo.setEnergyScore(createScore(variableInfo));
		}
		Collections.sort(infos, Collections.reverseOrder());
		List<Variable> result = new ArrayList<>();
		for (VariableInfo variableInfo : infos) {
			result.add(variableInfo.getVariable());
		}
		return result;
	}

	private static <T> List<T> flattenIndexMap(final Map<Integer, T> data) {
		final List<T> result = new ArrayList<>(Collections.nCopies(data.size(), null));
		data.forEach(result::set);
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

	private static Map<DisjunctiveFormula, Bitmask> mapFormulaToClauses(final Collection<DisjunctiveFormula> formulas,
			final Map<ConjunctiveClause, Integer> clauseToIndex) {
		final Map<DisjunctiveFormula, Bitmask> result = new HashMap<>();
		for (DisjunctiveFormula formula : formulas) {
			for (ConjunctiveClause clause : formula.getClauses()) {
				Bitmask associatedIndexes = result.get(formula);
				if (associatedIndexes == null) {
					associatedIndexes = new Bitmask();
					result.put(formula, associatedIndexes);
				}
				associatedIndexes.set(clauseToIndex.get(clause));
			}
		}
		return result;
	}

	private static Map<DisjunctiveFormula, Set<SAPL>> mapFormulaToDocuments(
			final Map<String, DisjunctiveFormula> targets, final Map<String, SAPL> documents) {
		Map<DisjunctiveFormula, Set<SAPL>> result = new HashMap<>();
		for (Map.Entry<String, DisjunctiveFormula> entry : targets.entrySet()) {
			DisjunctiveFormula formula = entry.getValue();
			Set<SAPL> set = result.get(formula);
			if (set == null) {
				set = new HashSet<>();
				result.put(formula, set);
			}
			set.add(documents.get(entry.getKey()));
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

	private static Map<Integer, Set<DisjunctiveFormula>> mapIndexToTargets(
			final Map<ConjunctiveClause, Integer> clauseToIndex,
			final Map<ConjunctiveClause, Set<DisjunctiveFormula>> clauseToFormulas) {
		Map<Integer, Set<DisjunctiveFormula>> result = new HashMap<>();
		for (Map.Entry<ConjunctiveClause, Set<DisjunctiveFormula>> entry : clauseToFormulas.entrySet()) {
			result.put(clauseToIndex.get(entry.getKey()), entry.getValue());
		}
		return result;
	}
}
