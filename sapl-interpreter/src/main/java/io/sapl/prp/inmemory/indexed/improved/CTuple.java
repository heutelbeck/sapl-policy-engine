package io.sapl.prp.inmemory.indexed.improved;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@Deprecated
// tuple of a conjunction index number and the number of formulas in F(ci) containing ci .
public class CTuple {
    // conjunction index number
    private final int cI;
    // number of formulas containing conjunction
    private final long n;




}
