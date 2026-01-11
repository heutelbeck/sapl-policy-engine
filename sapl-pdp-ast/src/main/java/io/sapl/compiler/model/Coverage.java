package io.sapl.compiler.model;

import io.sapl.api.model.SourceLocation;
import io.sapl.api.model.Value;

import java.util.List;

public record Coverage() {
    public record BodyCoverage(List<ConditionHit> hits, long numberOfConditions) {}
    public record ConditionHit(long statementId, Value result, SourceLocation location) {}
}
