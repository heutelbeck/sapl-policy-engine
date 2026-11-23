/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.compiler.policy;

import static io.sapl.compiler.expressions.StratifiedBooleanOperationCompiler.asBoolean;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.SourceLocation;
import io.sapl.api.model.StreamOperator;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.configuration.CombiningAlgorithm.VotingMode;
import io.sapl.ast.Policy;
import io.sapl.ast.PolicySet;
import io.sapl.ast.SchemaCondition;
import io.sapl.ast.SchemaStatement;
import io.sapl.ast.VoterMetadata;
import io.sapl.compiler.document.CompiledDocument;
import io.sapl.compiler.document.Vote;
import io.sapl.compiler.document.VoteResultWithCoverage;
import io.sapl.compiler.document.Voter;
import io.sapl.compiler.model.Coverage;
import io.sapl.compiler.policy.PolicyCompiler.IndexedCompiledCondition;

import lombok.val;

/** Observes one execution of the compiled authorization voter. */
public final class ObservedVoter implements CoverageVoter {
    private final Voter                            voter;
    private final Document                         layout;
    private final Set<CompiledExpression>          sections   = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<CompiledExpression, Integer> directUses = new IdentityHashMap<>();

    private ObservedVoter(Voter voter, Document layout) {
        this.voter  = voter;
        this.layout = layout;
        layout.register(sections, directUses);
    }

    static CoverageVoter policy(Voter voter, Policy policy, List<IndexedCompiledCondition> conditions,
            CompiledExpression pure, CompiledExpression streaming) {
        val enforced = !policy.body().statements().isEmpty()
                && policy.body().statements().getFirst() instanceof SchemaCondition block
                && block.schemas().stream().anyMatch(SchemaStatement::enforced);
        return policy(voter, policy.metadata(), policy.declarationLocation(), conditions, pure, streaming, enforced);
    }

    static CoverageVoter policy(Voter voter, VoterMetadata metadata, @Nullable SourceLocation declaration,
            List<IndexedCompiledCondition> conditions, CompiledExpression pure, CompiledExpression streaming,
            boolean enforced) {
        val singlePureCondition  = conditions.stream().map(IndexedCompiledCondition::expression)
                .filter(expression -> !Value.TRUE.equals(expression) && !(expression instanceof StreamOperator))
                .count() == 1;
        val hasDecisionCondition = enforced || conditions.stream().anyMatch(condition -> condition.statementId() >= 0);
        return new ObservedVoter(voter, new PolicyBody(metadata, declaration, List.copyOf(conditions),
                sites(conditions), pure, streaming, enforced, hasDecisionCondition, singlePureCondition));
    }

    private static Map<CompiledExpression, List<IndexedCompiledCondition>> sites(
            List<IndexedCompiledCondition> conditions) {
        val byExpression = new IdentityHashMap<CompiledExpression, List<IndexedCompiledCondition>>();
        for (val condition : conditions) {
            byExpression.computeIfAbsent(condition.expression(), ignored -> new ArrayList<>()).add(condition);
        }
        return byExpression;
    }

    /**
     * @param voter ordinary voter including the effective target
     * @param set source set with its optional schema and written target
     * @param schema compiled schema validation, or null if the target contains no schema operand
     * @param applicability compiled effective target consumed by ordinary evaluation
     * @param members compiled member policies in declaration order
     * @return coverage of the same evaluation
     */
    public static CoverageVoter policySet(Voter voter, PolicySet set, @Nullable CompiledExpression schema,
            CompiledExpression applicability, List<? extends CompiledDocument> members) {
        val match      = set.match();
        val target     = set.target();
        val schemaSite = match != null && match.schemas().stream().anyMatch(SchemaStatement::enforced)
                ? match.location()
                : null;
        val targetSite = target != null ? target.location() : schemaSite;
        return new ObservedVoter(voter, new SetBody(set.metadata(), set.declarationLocation(), targetSite, schemaSite,
                schema, applicability, set.algorithm().votingMode() != VotingMode.FIRST, layouts(members)));
    }

    /**
     * @param voter ordinary voter for this configuration
     * @param metadata PDP voter metadata
     * @param documents compiled documents
     * @return coverage of the same evaluation
     */
    public static CoverageVoter pdp(Voter voter, VoterMetadata metadata, List<? extends CompiledDocument> documents) {
        return new ObservedVoter(voter, new Pdp(metadata, layouts(documents)));
    }

    private static List<Document> layouts(List<? extends CompiledDocument> documents) {
        return documents.stream().map(CompiledDocument::coverageVoter).filter(ObservedVoter.class::isInstance)
                .map(ObservedVoter.class::cast).map(observed -> observed.layout).toList();
    }

    @Override
    public VoteResultWithCoverage evaluate(EvaluationContext ctx) {
        val observations = new Observations(sections, directUses, ctx.observer());
        val result       = voter.evaluate(ctx.withObserver(observations));
        observations.pending = result.vote() == null;
        observations.include(result.vote());
        return new VoteResultWithCoverage(result, layout.coverage(observations, true));
    }

    /**
     * Retains the already completed votes when a combiner returns a pending round.
     *
     * @param ctx evaluation context of the pending round
     * @param votes completed members of the combining scope
     */
    public static void completedBeforePending(EvaluationContext ctx, List<Vote> votes) {
        if (ctx.observer() instanceof Observations observations) {
            votes.forEach(observations::include);
        }
    }

    private interface Document {
        VoterMetadata metadata();

        void register(Set<CompiledExpression> sections, Map<CompiledExpression, Integer> direct);

        Coverage.@Nullable DocumentCoverage coverage(Observations observations, boolean reached);

        default boolean foldedFalse() {
            return false;
        }
    }

    private record Read(CompiledExpression operand, @Nullable Value value) {}

    private static final class Observations implements EvaluationContext.EvaluationObserver {
        private final Set<CompiledExpression>                        sections;
        private final Map<CompiledExpression, Integer>               directUses;
        private final EvaluationContext.@Nullable EvaluationObserver delegate;
        private final Map<CompiledExpression, List<Read>>            reads   = new IdentityHashMap<>();
        private final Map<CompiledExpression, Value>                 direct  = new IdentityHashMap<>();
        private final Map<CompiledExpression, Value>                 guards  = new IdentityHashMap<>();
        private boolean                                              pending;
        private final Set<VoterMetadata>                             reached = Collections
                .newSetFromMap(new IdentityHashMap<>());

        Observations(Set<CompiledExpression> sections,
                Map<CompiledExpression, Integer> directUses,
                EvaluationContext.@Nullable EvaluationObserver delegate) {
            this.delegate   = delegate;
            this.sections   = sections;
            this.directUses = directUses;
        }

        @Override
        public void evaluated(CompiledExpression parent, CompiledExpression operand, @Nullable Value value) {
            if (delegate != null) {
                delegate.evaluated(parent, operand, value);
            }
            if (parent == operand) {
                if (value != null && directUses.containsKey(operand)) {
                    direct.put(operand, value);
                }
            } else if (sections.contains(parent)) {
                reads.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(new Read(operand, value));
            }
        }

        @Override
        public void classified(CompiledExpression guard, Value result) {
            if (delegate != null) {
                delegate.classified(guard, result);
            }
            guards.put(guard, result);
        }

        @Nullable
        Value guard(CompiledExpression expression) {
            return guards.getOrDefault(expression, direct.get(expression));
        }

        void include(@Nullable Vote vote) {
            if (vote == null) {
                return;
            }
            val votes = new ArrayDeque<Vote>();
            votes.push(vote);
            while (!votes.isEmpty()) {
                val next = votes.pop();
                reached.add(next.voter());
                votes.addAll(next.contributingVotes());
            }
        }

        @Nullable
        Value observed(@Nullable CompiledExpression expression) {
            if (expression != null && directUses.getOrDefault(expression, 0) == 1) {
                return direct.get(expression);
            }
            return null;
        }
    }

    private record PolicyBody(
            VoterMetadata metadata,
            @Nullable SourceLocation declaration,
            List<IndexedCompiledCondition> conditions,
            Map<CompiledExpression, List<IndexedCompiledCondition>> sites,
            CompiledExpression pure,
            CompiledExpression streaming,
            boolean enforced,
            boolean hasDecisionCondition,
            boolean singlePureCondition) implements Document {
        @Override
        public void register(Set<CompiledExpression> sections, Map<CompiledExpression, Integer> direct) {
            sections.add(pure);
            sections.add(streaming);
            for (val condition : conditions) {
                if (condition.expression() instanceof PureOperator) {
                    direct.merge(condition.expression(), 1, Integer::sum);
                }
            }
            if (pure instanceof PureOperator) {
                direct.putIfAbsent(pure, 1);
            }
        }

        @Override
        public boolean foldedFalse() {
            return Value.FALSE.equals(pure);
        }

        @Override
        public Coverage.@Nullable DocumentCoverage coverage(Observations observations, boolean reached) {
            val pureReads   = observations.reads.get(pure);
            val streamReads = observations.reads.get(streaming);
            val guard       = observations.guard(pure);
            if (!reached && !observations.reached.contains(metadata) && pureReads == null && streamReads == null
                    && guard == null) {
                return null;
            }
            val hits = new ArrayList<Coverage.ConditionHit>();
            for (val condition : conditions) {
                if (condition.expression() instanceof Value value && (condition.statementId() >= 0 || enforced)
                        && (!foldedFalse() || Value.FALSE.equals(value))) {
                    hits.add(hit(condition, value));
                    if (foldedFalse()) {
                        break;
                    }
                }
            }
            if (pureReads != null) {
                addReads(hits, pureReads);
            } else if (!foldedFalse()) {
                addIndexedConditions(hits, observations, guard);
            }
            if (streamReads != null) {
                addReads(hits, streamReads);
            }
            return new Coverage.PolicyCoverage(metadata,
                    new Coverage.BodyCoverage(List.copyOf(hits), conditions.size(), hasDecisionCondition), declaration);
        }

        private void addIndexedConditions(List<Coverage.ConditionHit> hits, Observations observations,
                @Nullable Value guard) {
            // A positive index classification proves each conjunct, including repeated source sites
            // sharing the same predicate. A negative match alone does not identify a failing conjunct.
            for (val condition : conditions) {
                if (condition.expression() instanceof PureOperator) {
                    val value = Value.TRUE.equals(guard) || singlePureCondition && guard != null ? guard
                            : observations.observed(condition.expression());
                    if (value != null) {
                        hits.add(hit(condition, value));
                    }
                }
            }
        }

        private void addReads(List<Coverage.ConditionHit> hits, List<Read> reads) {
            val offsets = new IdentityHashMap<CompiledExpression, Integer>();
            for (val read : reads) {
                val occurrences = sites.get(read.operand());
                val index       = offsets.merge(read.operand(), 1, Integer::sum) - 1;
                val value       = read.value();
                if (value != null && occurrences != null && index < occurrences.size()) {
                    hits.add(hit(occurrences.get(index), value));
                }
            }
        }

        private Coverage.ConditionHit hit(IndexedCompiledCondition condition, Value value) {
            return new Coverage.ConditionHit(asBoolean(value, condition.location()), condition.location(),
                    condition.statementId(), condition.statementId() == -1 && enforced);
        }
    }

    private record SetBody(
            VoterMetadata metadata,
            SourceLocation declaration,
            @Nullable SourceLocation targetSite,
            @Nullable SourceLocation schemaSite,
            @Nullable CompiledExpression schema,
            CompiledExpression applicability,
            boolean includeFolded,
            List<Document> children) implements Document {
        @Override
        public void register(Set<CompiledExpression> sections, Map<CompiledExpression, Integer> direct) {
            if (schema instanceof PureOperator) {
                direct.merge(schema, 1, Integer::sum);
            }
            if (applicability instanceof PureOperator && applicability != schema) {
                direct.merge(applicability, 1, Integer::sum);
            }
            children.forEach(child -> child.register(sections, direct));
        }

        @Override
        public boolean foldedFalse() {
            return Value.FALSE.equals(applicability);
        }

        @Override
        public Coverage.@Nullable DocumentCoverage coverage(Observations observations, boolean reached) {
            val guard   = applicability instanceof Value value ? value : observations.guard(applicability);
            val entered = reached || observations.reached.contains(metadata)
                    || (!(applicability instanceof Value) && guard != null);
            val members = Value.TRUE.equals(guard)
                    ? observedChildren(children, observations, entered && includeFolded, metadata)
                    : List.<Coverage.DocumentCoverage>of();
            if (!entered && members.isEmpty()) {
                return null;
            }
            Coverage.TargetHit target = Coverage.BLANK_TARGET_HIT;
            if (targetSite != null) {
                target = guard == null ? Coverage.NO_TARGET_HIT : new Coverage.TargetResult(guard, targetSite);
            }
            val validation = schema == applicability ? guard : observations.observed(schema);
            val schemaHit  = schemaSite != null && validation != null
                    ? new Coverage.ConditionHit(validation, schemaSite, -1, true)
                    : null;
            return new Coverage.PolicySetCoverage(metadata, target, members, declaration, schemaHit);
        }
    }

    private record Pdp(VoterMetadata metadata, List<Document> children) implements Document {
        @Override
        public void register(Set<CompiledExpression> sections, Map<CompiledExpression, Integer> direct) {
            children.forEach(child -> child.register(sections, direct));
        }

        @Override
        public Coverage.DocumentCoverage coverage(Observations observations, boolean root) {
            return new Coverage.PdpCoverage(metadata, observedChildren(children, observations, true, metadata));
        }
    }

    private static List<Coverage.DocumentCoverage> observedChildren(List<Document> children, Observations observations,
            boolean includeFolded, VoterMetadata parent) {
        val reached    = new ArrayList<Coverage.DocumentCoverage>();
        val incomplete = observations.pending && !observations.reached.contains(parent);
        for (val child : children) {
            if (incomplete && !observations.reached.contains(child.metadata())) {
                continue;
            }
            val result = child.coverage(observations, includeFolded && child.foldedFalse());
            if (result != null) {
                reached.add(result);
            }
        }
        return List.copyOf(reached);
    }
}
