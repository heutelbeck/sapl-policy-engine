package io.sapl.grammar.sapl.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.emf.common.util.EList;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.grammar.sapl.Step;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.FluxProvider;
import io.sapl.interpreter.DependentStreamsUtil;
import io.sapl.interpreter.selection.JsonNodeWithoutParent;
import io.sapl.interpreter.selection.ResultNode;
import lombok.experimental.UtilityClass;
import reactor.core.publisher.Flux;

@UtilityClass
public class StepResolver {

	/**
	 * Method for application of a number of selection steps to a JsonNode. The method
	 * returns a Flux of result trees, i.e., either an annotated JsonNode or an array of
	 * annotated JsonNodes. The annotation contains the parent node of the JsonNode in the
	 * JSON tree of which the root is the input JsonNode. This allows for modifying or
	 * deleting the selected JsonNodes.
	 * @param rootNode the input JsonNode
	 * @param steps the selection steps
	 * @param ctx the evaluation context
	 * @param isBody true if the expression occurs within the policy body (attribute
	 * finder steps are only allowed if set to true)
	 * @param relativeNode the node a relative expression would point to
	 * @return a flux of result tree root nodes (either an annotated JsonNode or an array)
	 */
	public static Flux<ResultNode> resolveSteps(Optional<JsonNode> rootNode, EList<Step> steps, EvaluationContext ctx,
			boolean isBody, Optional<JsonNode> relativeNode) {
		// this implementation must be able to handle expressions like
		// "input".<first.attr>.<second.attr>.<third.attr>... correctly
		final ResultNode result = new JsonNodeWithoutParent(rootNode);
		if (steps != null && !steps.isEmpty()) {
			final List<FluxProvider<ResultNode>> fluxProviders = new ArrayList<>(steps.size());
			for (Step step : steps) {
				fluxProviders.add(resultNode -> resultNode.applyStep(step, ctx, isBody, relativeNode));
			}
			return DependentStreamsUtil.nestedSwitchMap(result, fluxProviders);
		}
		else {
			return Flux.just(result);
		}
	}

}
