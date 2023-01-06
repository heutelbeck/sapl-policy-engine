package io.sapl.grammar.sapl.impl.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.context.AuthorizationContext;
import lombok.experimental.UtilityClass;
import reactor.core.publisher.Flux;

@UtilityClass
public class StepAlgorithmUtil {
	private static final String ARRAY_ACCESS_TYPE_MISMATCH  = "Type mismatch. Expected an Array, but got: '%s'.";
	private static final String OBJECT_ACCESS_TYPE_MISMATCH = "Type mismatch. Expected an Object, but got: '%s'.";
	private static final String STEP_ACCESS_TYPE_MISMATCH   = "Type mismatch. Expected an Object or Array, but got: '%s'.";

	public Flux<Val> apply(Val parentValue, Supplier<Flux<Val>> selector, String stepParameters,
			Class<?> operationType) {
		if (parentValue.isError()) {
			return Flux.just(parentValue.withParentTrace(operationType, parentValue));
		}
		if (parentValue.isArray()) {
			return applyOnArray(parentValue, selector, stepParameters, operationType);
		}
		if (parentValue.isObject()) {
			return applyOnObject(parentValue, selector, stepParameters, operationType);
		}
		return Flux.just(Val.error(STEP_ACCESS_TYPE_MISMATCH, parentValue).withTrace(operationType, parentValue));
	}

	public static Flux<Val> applyOnArray(Val parentValue, Supplier<Flux<Val>> selector, String stepParameters,
			Class<?> operationType) {
		if (parentValue.isError()) {
			return Flux.just(parentValue.withParentTrace(operationType, parentValue));
		}

		if (!parentValue.isArray()) {
			return Flux.just(
					Val.error(ARRAY_ACCESS_TYPE_MISMATCH, parentValue).withParentTrace(operationType, parentValue));
		}

		if (parentValue.isEmpty()) {
			return Flux.just(Val.ofEmptyArray().withParentTrace(operationType, parentValue));
		}
		var array   = parentValue.getArrayNode();
		var results = new ArrayList<Flux<Val>>(array.size());
		for (int i = 0; i < array.size(); i++) {
			var element         = array.get(i);
			var elementValue    = Val.of(element);
			var index           = i;
			var condition       = selector.get().contextWrite(ctx -> AuthorizationContext.setRelativeNodeWithIndex(ctx,
					elementValue.withTrace(operationType, Map.of("from", parentValue)), index));
			var selectedElement = condition.map(applySelectionToElement(elementValue, stepParameters, operationType,
					parentValue, "array[" + index + "]"));
			results.add(selectedElement);
		}
		return Flux.combineLatest(results, RepackageUtil::recombineArray);
	}

	public static Flux<Val> applyOnObject(Val parentValue, Supplier<Flux<Val>> selector, String stepParameters,
			Class<?> operationType) {
		if (parentValue.isError()) {
			return Flux.just(parentValue.withParentTrace(operationType, parentValue));
		}

		if (!parentValue.isObject()) {
			return Flux.just(
					Val.error(OBJECT_ACCESS_TYPE_MISMATCH, parentValue).withParentTrace(operationType, parentValue));
		}

		if (parentValue.isEmpty()) {
			return Flux.just(Val.ofEmptyArray().withParentTrace(operationType, parentValue));
		}

		var object  = parentValue.getObjectNode();
		var results = new ArrayList<Flux<Val>>(object.size());
		var fields  = object.fields();
		while (fields.hasNext()) {
			var field     = fields.next();
			var key       = field.getKey();
			var value     = Val.of(field.getValue());
			var condition = selector.get().contextWrite(ctx -> AuthorizationContext.setRelativeNodeWithKey(ctx,
					value.withTrace(operationType, Map.of("from", parentValue)), key));
			var selected  = condition
					.map(applySelectionToElement(value, stepParameters, operationType, parentValue, key));
			results.add(selected);
		}
		return Flux.combineLatest(results, RepackageUtil::recombineArray);
	}

	private static Function<Val, Val> applySelectionToElement(Val elementValue, String stepParameters,
			Class<?> operationType, Val parentValue, String elementIdentidier) {
		return conditionResult -> {
			var trace = new HashMap<String, Val>();
			trace.put("parentValue", parentValue);
			trace.put("stepParameters", Val.of(stepParameters));
			trace.put(elementIdentidier, elementValue.withTrace(operationType, Map.of("from", parentValue)));
			trace.put("conditionResult", conditionResult);
			if (conditionResult.isError()) {
				return conditionResult.withTrace(operationType, trace);
			}
			if (conditionResult.isBoolean() && conditionResult.getBoolean()) {
				return elementValue.withTrace(operationType, trace);
			}
			// Treat non-boolean as FALSE
			return Val.UNDEFINED.withTrace(operationType, trace);
		};
	}
//
//	public static Flux<Val> applyFilterOnArray(Val unfilteredValue, int stepId,
//			BiFunction<Integer, Val, Boolean> selector, FilterStatement statement, String stepParameters,
//			Class<?> operationType) {
//		if (unfilteredValue.isError() | !unfilteredValue.isArray()) {
//			return Flux.just(unfilteredValue.withTrace(operationType,
//					Map.of("unfilteredValue", unfilteredValue, "stepParameters", Val.of(stepParameters))));
//		}
//
//		var array = unfilteredValue.getArrayNode();
//		if (array.isEmpty()) {
//			return Flux.just(unfilteredValue.withTrace(operationType,
//					Map.of("unfilteredValue", unfilteredValue, "stepParameters", Val.of(stepParameters))));
//		}
//
//		var elementFluxes = new ArrayList<Flux<Val>>(array.size());
//		for (int i = 0; i < array.size(); i++) {
//			var element = array.get(i);
//			var trace   = new HashMap<String, Val>();
//			trace.put("unfilteredValue", unfilteredValue);
//			trace.put("stepParameters", Val.of(stepParameters));
//			trace.put("array[" + i + "]", Val.of(element).withTrace(operationType, Map.of("from", unfilteredValue)));
//
//			var elementValue = Val.of(element);
//			try {
//				var isSelected = selector.apply(i, elementValue);
//				trace.put("isSelected", Val.of(isSelected));
//				elementValue = elementValue.withTrace(operationType, trace);
//				if (isSelected) {
//					if (stepId == statement.getTarget().getSteps().size() - 1) {
//						// this was the final step. apply filter
//						elementFluxes.add(FilterComponentImplCustom.applyFilterFunction(elementValue,
//								statement.getArguments(), statement.getFsteps(), statement.isEach())
//								.contextWrite(ctx -> AuthorizationContext.setRelativeNode(ctx, unfilteredValue)));
//					} else {
//						// there are more steps. descent with them
//						elementFluxes.add(statement.getTarget().getSteps().get(stepId + 1)
//								.applyFilterStatement(elementValue, stepId + 1, statement));
//					}
//				} else {
//					elementFluxes.add(Flux.just(elementValue));
//				}
//			} catch (PolicyEvaluationException e) {
//				elementFluxes.add(Flux.just(Val.error(e.getMessage()).withTrace(operationType, trace)));
//			}
//		}
//		return Flux.combineLatest(elementFluxes, RepackageUtil::recombineArray);
//	}
//
//	public static Flux<Val> applyFilterOnObject(Val unfilteredValue, int stepId,
//			BiFunction<String, JsonNode, Boolean> selector, FilterStatement statement, String stepParameters,
//			Class<?> operationType) {
//		if (unfilteredValue.isError() || !unfilteredValue.isObject()) {
//			return Flux.just(unfilteredValue.withTrace(operationType,
//					Map.of("unfilteredValue", unfilteredValue, "stepParameters", Val.of(stepParameters))));
//		}
//
//		var object = unfilteredValue.getObjectNode();
//		if (object.isEmpty()) {
//			return Flux.just(unfilteredValue.withTrace(operationType,
//					Map.of("unfilteredValue", unfilteredValue, "stepParameters", Val.of(stepParameters))));
//		}
//
//		var fieldFluxes = new ArrayList<Flux<Tuple2<String, Val>>>(object.size());
//		var fields      = object.fields();
//		while (fields.hasNext()) {
//			var field = fields.next();
//			var key   = field.getKey();
//			var value = field.getValue();
//			var trace = new HashMap<String, Val>();
//			trace.put("unfilteredValue", unfilteredValue);
//			trace.put("stepParameters", Val.of(stepParameters));
//			trace.put(key, Val.of(value).withTrace(operationType, Map.of("from", unfilteredValue)));
//			try {
//				var isSelected = selector.apply(key, value);
//				trace.put("isSelected", Val.of(isSelected));
//				var tracedValue = Val.of(field.getValue()).withTrace(operationType, trace);
//				if (isSelected) {
//					if (stepId == statement.getTarget().getSteps().size() - 1) {
//						// this was the final step. apply filter
//						fieldFluxes.add(FilterComponentImplCustom
//								.applyFilterFunction(tracedValue, statement.getArguments(), statement.getFsteps(),
//										statement.isEach())
//								.contextWrite(ctx -> AuthorizationContext.setRelativeNode(ctx, unfilteredValue))
//								.map(val -> Tuples.of(field.getKey(), val)));
//					} else {
//						// there are more steps. descent with them
//						fieldFluxes.add(statement.getTarget().getSteps().get(stepId + 1)
//								.applyFilterStatement(tracedValue, stepId + 1, statement)
//								.map(val -> Tuples.of(field.getKey(), val)));
//					}
//				} else {
//					fieldFluxes.add(Flux.just(Tuples.of(key, tracedValue)));
//				}
//			} catch (PolicyEvaluationException e) {
//				fieldFluxes.add(Flux.just(Tuples.of(key, Val.error(e.getMessage()).withTrace(operationType, trace))));
//			}
//		}
//
//		return Flux.combineLatest(fieldFluxes, RepackageUtil::recombineObject);
//	}

}