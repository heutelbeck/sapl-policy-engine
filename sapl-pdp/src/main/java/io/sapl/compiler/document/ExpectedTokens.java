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
package io.sapl.compiler.document;

import io.sapl.grammar.antlr.SAPLParser;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.antlr.v4.runtime.NoViableAltException;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.atn.ATNState;
import org.antlr.v4.runtime.atn.AtomTransition;
import org.antlr.v4.runtime.atn.RangeTransition;
import org.antlr.v4.runtime.atn.RuleStopState;
import org.antlr.v4.runtime.atn.RuleTransition;
import org.antlr.v4.runtime.atn.SetTransition;
import org.antlr.v4.runtime.misc.IntervalSet;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The tokens that the grammar accepts at a place, as far as the parser cannot
 * tell them itself.
 * <p>
 * Where the parser decides between alternatives by looking ahead over several
 * tokens and none fits, it names the tokens accepted where the decision
 * started, not those accepted at the token that fits no alternative. These are
 * found by following the grammar from the start of the decision over the
 * tokens before that token.
 * <p>
 * A parser of the kind that decides from the tokens read so far leaves a
 * construct before it
 * refuses a token only if the token can follow the construct somewhere in the
 * grammar. The tokens that can follow each rule of the grammar tell where it
 * refuses a token.
 */
@UtilityClass
class ExpectedTokens {

    private static final ATN           GRAMMAR        = SAPLParser._ATN;
    private static final IntervalSet[] FOLLOW_OF_RULE = followOfEachRule();

    /**
     * @param ruleIndex the index of a rule of the grammar
     * @param type the type of a token
     * @return whether the token can follow the rule somewhere in the grammar
     */
    static boolean canFollow(int ruleIndex, int type) {
        return FOLLOW_OF_RULE[ruleIndex].contains(type);
    }

    /**
     * The tokens accepted where a parser that decides from the tokens read so
     * far refuses a token. From the place where the parser first left a
     * construct for the token, such a parser leaves each construct around it
     * that can end there and that the token can follow somewhere in the grammar.
     * Where it cannot leave, it refuses the token, and the tokens accepted there
     * are those that can follow in the rules around it.
     *
     * @param stateNumber the state of the grammar where the parser first left a
     * construct for the token, or where it refused the token
     * @param context the rule the parser was in there
     * @param type the type of the refused token
     * @return the types of the accepted tokens
     */
    static IntervalSet whereRefused(int stateNumber, RuleContext context, int type) {
        var state = GRAMMAR.states.get(stateNumber);
        var rule  = context;
        while (rule.invokingState >= 0 && GRAMMAR.nextTokens(state).contains(Token.EPSILON)
                && canFollow(state.ruleIndex, type)) {
            state = ((RuleTransition) GRAMMAR.states.get(rule.invokingState).transition(0)).followState;
            rule  = rule.parent;
        }
        return GRAMMAR.getExpectedTokens(state.stateNumber, rule);
    }

    /**
     * @param parser the parser that refused the token
     * @param refusal the decision that found no alternative
     * @return the types of the tokens the grammar accepts in place of the token
     * that fits no alternative
     */
    static IntervalSet atRefusedToken(Parser parser, NoViableAltException refusal) {
        val atDeadEnd = whereDecisionEnded(refusal);
        if (atDeadEnd != null) {
            return atDeadEnd;
        }
        val stream = parser.getInputStream();
        var places = closure(
                Set.of(new Place(GRAMMAR.states.get(refusal.getOffendingState()), Frame.of(refusal.getCtx()))));
        for (var index = refusal.getStartToken().getTokenIndex(); index < refusal.getOffendingToken()
                .getTokenIndex(); index++) {
            val token = stream.get(index);
            if (token.getChannel() == Token.DEFAULT_CHANNEL) {
                places = closure(advanced(places, token.getType()));
            }
        }
        val expected = new IntervalSet();
        for (val place : places) {
            if (place.state() instanceof RuleStopState) {
                expected.add(Token.EOF);
            }
            addTokensReadFrom(place.state(), expected);
        }
        return expected;
    }

    /**
     * The places of the grammar where the decision ended tell the accepted tokens
     * directly, as long as none of them lies beyond the rule of the decision:
     * there the decision follows every use of the rule, not only the one the
     * parser is in.
     */
    private static @Nullable IntervalSet whereDecisionEnded(NoViableAltException refusal) {
        val places = refusal.getDeadEndConfigs();
        if (places == null || places.isEmpty()) {
            return null;
        }
        val expected = new IntervalSet();
        for (val place : places) {
            if (place.getOuterContextDepth() > 0 || place.state instanceof RuleStopState) {
                return null;
            }
            addTokensReadFrom(place.state, expected);
        }
        return expected;
    }

    /**
     * A rule the parser is in, by the state it goes on with when it leaves the
     * rule.
     */
    private record Frame(ATNState follow, @Nullable Frame parent) {

        static @Nullable Frame of(@Nullable RuleContext context) {
            if (context == null || context.invokingState < 0) {
                return null;
            }
            val invocation = (RuleTransition) GRAMMAR.states.get(context.invokingState).transition(0);
            return new Frame(invocation.followState, of(context.parent));
        }
    }

    /**
     * A place in the grammar: a state and the rules around it.
     */
    private record Place(ATNState state, @Nullable Frame frame) {}

    private static Set<Place> advanced(Set<Place> places, int type) {
        val next = new HashSet<Place>();
        for (val place : places) {
            for (val transition : place.state().getTransitions()) {
                if (!transition.isEpsilon()
                        && transition.matches(type, Token.MIN_USER_TOKEN_TYPE, GRAMMAR.maxTokenType)) {
                    next.add(new Place(transition.target, place.frame()));
                }
            }
        }
        return next;
    }

    /**
     * The places reachable without reading a token.
     */
    private static Set<Place> closure(Set<Place> places) {
        val reached = new HashSet<Place>();
        val pending = new ArrayDeque<>(places);
        while (!pending.isEmpty()) {
            val place = pending.pop();
            if (reached.add(place)) {
                pushPlacesWithoutReading(place, pending);
            }
        }
        return reached;
    }

    /**
     * Pushes the places that follow a place without reading a token.
     */
    private static void pushPlacesWithoutReading(Place place, Deque<Place> pending) {
        if (place.state() instanceof RuleStopState) {
            // The transitions of the end of a rule lead to every place the rule is used.
            // Only the place it was entered from is followed.
            val frame = place.frame();
            if (frame != null) {
                pending.push(new Place(frame.follow(), frame.parent()));
            }
            return;
        }
        for (val transition : place.state().getTransitions()) {
            if (transition instanceof RuleTransition rule) {
                pending.push(new Place(rule.target, new Frame(rule.followState, place.frame())));
            } else if (transition.isEpsilon()) {
                pending.push(new Place(transition.target, place.frame()));
            }
        }
    }

    /**
     * Adds the tokens that the transitions from a state of the grammar read.
     */
    private static void addTokensReadFrom(ATNState state, IntervalSet expected) {
        for (val transition : state.getTransitions()) {
            if (transition instanceof AtomTransition || transition instanceof RangeTransition
                    || transition instanceof SetTransition) {
                expected.addAll(transition.label());
            }
        }
    }

    /**
     * For each rule, the tokens that can follow it somewhere in the grammar: the
     * tokens after each use of the rule, and where a use can end the rule around
     * it, the tokens that can follow that rule.
     */
    private static IntervalSet[] followOfEachRule() {
        val rules   = GRAMMAR.ruleToStartState.length;
        val follow  = new IntervalSet[rules];
        val callers = new ArrayList<List<Integer>>(rules);
        for (var rule = 0; rule < rules; rule++) {
            follow[rule] = new IntervalSet();
            callers.add(new ArrayList<>());
        }
        for (val state : GRAMMAR.states) {
            for (val transition : state.getTransitions()) {
                if (transition instanceof RuleTransition use) {
                    noteUse(use, state.ruleIndex, follow, callers);
                }
            }
        }
        var changed = true;
        while (changed) {
            changed = false;
            for (var rule = 0; rule < rules; rule++) {
                changed |= addFollowOfCallers(follow, rule, callers.get(rule));
            }
        }
        return follow;
    }

    private static void noteUse(RuleTransition use, int caller, IntervalSet[] follow, List<List<Integer>> callers) {
        val after = GRAMMAR.nextTokens(use.followState);
        follow[use.target.ruleIndex].addAll(after);
        if (after.contains(Token.EPSILON)) {
            callers.get(use.target.ruleIndex).add(caller);
        }
    }

    /**
     * @return whether the follow of the rule grew
     */
    private static boolean addFollowOfCallers(IntervalSet[] follow, int rule, List<Integer> callers) {
        val before = follow[rule].size();
        for (val caller : callers) {
            follow[rule].addAll(follow[caller]);
        }
        return follow[rule].size() != before;
    }

}
