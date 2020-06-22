package io.sapl.prp.inmemory.indexed.improved;

import io.sapl.prp.inmemory.indexed.ConjunctiveClause;
import lombok.Data;

@Data
public class MTuple {

    private final ConjunctiveClause conjunctiveClause;
    private final int numbersOfFormularsWithConjunctiveClaus;

    public MTuple(ConjunctiveClause conjunctiveClause, int numbersOfFormularsWithConjunctiveClaus) {
        this.conjunctiveClause = conjunctiveClause;
        this.numbersOfFormularsWithConjunctiveClaus = numbersOfFormularsWithConjunctiveClaus;
    }
}
