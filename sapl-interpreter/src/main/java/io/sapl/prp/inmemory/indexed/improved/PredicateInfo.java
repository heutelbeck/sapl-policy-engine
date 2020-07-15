package io.sapl.prp.inmemory.indexed.improved;

import com.google.common.base.Preconditions;
import io.sapl.prp.inmemory.indexed.ConjunctiveClause;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class PredicateInfo {

    private final Predicate predicate;

    private final Set<ConjunctiveClause> unsatisfiableConjunctionsIfFalse = new HashSet<>();

    private final Set<ConjunctiveClause> unsatisfiableConjunctionsIfTrue = new HashSet<>();


    public PredicateInfo(final Predicate predicate) {
        this.predicate = Preconditions.checkNotNull(predicate);
    }

    public Set<ConjunctiveClause> getUnsatisfiableConjunctionsIfFalse() {
        return Collections.unmodifiableSet(unsatisfiableConjunctionsIfFalse);
    }

    public Set<ConjunctiveClause> getUnsatisfiableConjunctionsIfTrue() {
        return Collections.unmodifiableSet(unsatisfiableConjunctionsIfTrue);
    }

    public Predicate getPredicate() {
        return predicate;
    }


    public void addUnsatisfiableConjunctionIfFalse(ConjunctiveClause clause){
        unsatisfiableConjunctionsIfFalse.add(clause);
    }

    public void addUnsatisfiableConjunctionIfTrue(ConjunctiveClause clause){
        unsatisfiableConjunctionsIfTrue.add(clause);
    }
}
