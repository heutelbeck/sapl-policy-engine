filepath = 'C:/devkit/git/sapl-policy-engine/sapl-test/src/main/java/io/sapl/test/plain/ScenarioInterpreter.java'

with open(filepath, 'r') as f:
    content = f.read()

# Replace the executeExpectation method to combine matchers
old_execute_expectation = '''    private void executeExpectation(SaplTestFixture.DecisionResult decisionResult, ExpectationContext expectation) {
        if (expectation instanceof SingleExpectationContext single) {
            // expect permit|deny|... [with obligations ...] [with resource ...] [with
            // advice ...]
            var matcher = buildDecisionMatcher(single.authorizationDecision());
            decisionResult.expectDecisionMatches(matcher);

        } else if (expectation instanceof MatcherExpectationContext matcherExp) {
            // expect decision any, is permit, with obligation ..., etc.
            for (var matcherCtx : matcherExp.matchers) {
                applyDecisionMatcherExpectation(decisionResult, matcherCtx);
            }

        } else if (expectation instanceof StreamExpectationContext streamExp) {
            // expect - permit once - deny 2 times - ...
            for (var expectStep : streamExp.expectStep()) {
                executeExpectStep(decisionResult, expectStep);
            }
        }
    }'''

new_execute_expectation = '''    private void executeExpectation(SaplTestFixture.DecisionResult decisionResult, ExpectationContext expectation) {
        if (expectation instanceof SingleExpectationContext single) {
            // expect permit|deny|... [with obligations ...] [with resource ...] [with
            // advice ...]
            var matcher = buildDecisionMatcher(single.authorizationDecision());
            decisionResult.expectDecisionMatches(matcher);

        } else if (expectation instanceof MatcherExpectationContext matcherExp) {
            // expect decision any, is permit, with obligation ..., etc.
            // Build a combined predicate that checks ALL matchers on a SINGLE decision
            var combinedPredicate = buildCombinedMatcherPredicate(matcherExp.matchers);
            decisionResult.expectDecisionMatches(combinedPredicate);

        } else if (expectation instanceof StreamExpectationContext streamExp) {
            // expect - permit once - deny 2 times - ...
            for (var expectStep : streamExp.expectStep()) {
                executeExpectStep(decisionResult, expectStep);
            }
        }
    }'''

content = content.replace(old_execute_expectation, new_execute_expectation)

# Also fix the executeExpectStep method for NextWithMatcherStepContext
old_execute_step = '''        } else if (step instanceof NextWithMatcherStepContext nextMatcherStep) {
            // decision any, is permit, with obligation ...
            for (var matcherCtx : nextMatcherStep.matchers) {
                applyDecisionMatcherExpectation(decisionResult, matcherCtx);
            }
        }'''

new_execute_step = '''        } else if (step instanceof NextWithMatcherStepContext nextMatcherStep) {
            // decision any, is permit, with obligation ...
            var combinedPredicate = buildCombinedMatcherPredicate(nextMatcherStep.matchers);
            decisionResult.expectDecisionMatches(combinedPredicate);
        }'''

content = content.replace(old_execute_step, new_execute_step)

# Add the buildCombinedMatcherPredicate method after applyDecisionMatcherExpectation
old_apply_method_end = '''        default                                         ->
            throw new IllegalArgumentException("Unknown decision matcher type: " + ctx.getClass().getSimpleName());
        }
    }

    /**
     * Matches an extended constraint'''

new_apply_method_end = '''        default                                         ->
            throw new IllegalArgumentException("Unknown decision matcher type: " + ctx.getClass().getSimpleName());
        }
    }

    /**
     * Builds a combined predicate from multiple authorization decision matchers.
     * All matchers must match for the combined predicate to return true.
     */
    private java.util.function.Predicate<io.sapl.api.pdp.AuthorizationDecision> buildCombinedMatcherPredicate(
            java.util.List<AuthorizationDecisionMatcherContext> matchers) {
        java.util.function.Predicate<io.sapl.api.pdp.AuthorizationDecision> combined = Objects::nonNull;
        for (var matcher : matchers) {
            combined = combined.and(buildSingleMatcherPredicate(matcher));
        }
        return combined;
    }

    /**
     * Builds a predicate from a single authorization decision matcher.
     */
    private java.util.function.Predicate<io.sapl.api.pdp.AuthorizationDecision> buildSingleMatcherPredicate(
            AuthorizationDecisionMatcherContext ctx) {
        return switch (ctx) {
        case AnyDecisionMatcherContext anyCtx -> Objects::nonNull;
        case IsDecisionMatcherContext isCtx -> {
            var expectedDecision = parseDecisionType(isCtx.decision);
            yield decision -> decision != null && decision.decision() == expectedDecision;
        }
        case HasObligationOrAdviceMatcherContext hasCtx -> {
            var isObligation = "obligation".equalsIgnoreCase(hasCtx.matcherType.getText());
            var extendedMatch = hasCtx.extendedMatcher;
            yield decision -> {
                if (decision == null) {
                    return false;
                }
                var constraints = isObligation ? decision.obligations() : decision.advice();
                if (constraints == null || constraints.isEmpty()) {
                    return false;
                }
                if (extendedMatch == null) {
                    return true;
                }
                return matchExtendedConstraint(constraints, extendedMatch);
            };
        }
        case HasResourceMatcherContext resCtx -> {
            yield decision -> {
                if (decision == null) {
                    return false;
                }
                var resource = decision.resource();
                if (resCtx.defaultMatcher == null) {
                    return resource != null && !(resource instanceof UndefinedValue);
                }
                if (resource == null || resource instanceof UndefinedValue) {
                    return false;
                }
                return matchDefaultObject(resource, resCtx.defaultMatcher);
            };
        }
        default -> throw new IllegalArgumentException("Unknown matcher type: " + ctx.getClass().getSimpleName());
        };
    }

    /**
     * Matches an extended constraint'''

content = content.replace(old_apply_method_end, new_apply_method_end)

with open(filepath, 'w') as f:
    f.write(content)

print("Fixed combined matcher predicates")
