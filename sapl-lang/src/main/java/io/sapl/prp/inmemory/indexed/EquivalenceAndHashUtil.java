/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.prp.inmemory.indexed;

import java.util.Iterator;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;

import com.google.common.base.Objects;

import lombok.NonNull;
import lombok.experimental.UtilityClass;

/**
 * For indexing, expressions must be compared with regards to equivalence.
 * 
 * a) Parent nodes in the ASTs (eContainer) are irrelevant for this comparison
 * 
 * b) Import statements in policies may lead to locally equal expression objects
 * to be semantically different, or locally different objects to we semantically
 * equivalent. This the case for functions and attribute finders. These have to
 * be explicitly handled and imports must be resolved before comparing or
 * hashing.
 * 
 */
@UtilityClass
public class EquivalenceAndHashUtil {
	private final static int HASH_SEED_PRIME = 7;
	private final static int PRIME = 31;

	public static int semanticHash(@NonNull EObject thiz, @NonNull Map<String, String> imports) {
		var hash = HASH_SEED_PRIME;
		hash = PRIME * hash + thiz.eClass().hashCode(); // else literals all return HASH_SEED_PRIME and collide
		EList<EStructuralFeature> features = thiz.eClass().getEAllStructuralFeatures();
		for (EStructuralFeature feature : features) {
			var featureInstance = thiz.eGet(feature);
			if ("fsteps".equals(feature.getName())) {
				hash = PRIME * hash + hashFStepRespectingImports(featureInstance, imports);
			} else {
				hash = PRIME * hash + hash(featureInstance, imports);
			}
		}
		return hash;
	}

	@SuppressWarnings("unchecked")
	private static int hashFStepRespectingImports(Object featureInstance, @NonNull Map<String, String> imports) {
		return resolveStepsToFullyQualifiedName((EList<Object>) featureInstance, imports).hashCode();
	}

	@SuppressWarnings("unchecked")
	private static int hash(Object featureInstance, @NonNull Map<String, String> imports) {
		if (featureInstance == null) {
			return 0;
		}
		int hash = HASH_SEED_PRIME;
		if (featureInstance instanceof EList) {
			var thizList = (EList<EObject>) featureInstance;
			for (Object element : thizList) {
				hash = PRIME * hash + hash(element, imports);
			}
		} else if (featureInstance instanceof EObject) {
			hash = PRIME * hash + semanticHash((EObject) featureInstance, imports);
		} else {
			hash = PRIME * hash + featureInstance.hashCode();
		}
		return hash;
	}

	public boolean areEquivalent(@NonNull EObject thiz, @NonNull Map<String, String> thizImports, @NonNull EObject that,
			@NonNull Map<String, String> thatImports) {
		if (thiz == that) {
			return true;
		}
		if (that == null || thiz.eClass() != that.eClass()) {
			return false;
		}
		EList<EStructuralFeature> features = thiz.eClass().getEAllStructuralFeatures();
		for (EStructuralFeature feature : features) {
			var thizFeatureInstance = thiz.eGet(feature);
			var thatFeatureInstance = that.eGet(feature, true);
			if ("fsteps".equals(feature.getName())) {
				return fStepsAreEquivalentWithRegardsToImports(thizFeatureInstance, thizImports, thatFeatureInstance,
						thatImports);
			}
			if (!featuresAreEquivalent(thizFeatureInstance, thizImports, thatFeatureInstance, thatImports)) {
				return false;
			}
		}
		return true;
	}

	@SuppressWarnings("unchecked")
	private boolean fStepsAreEquivalentWithRegardsToImports(Object thizFeatureInstance,
			@NonNull Map<String, String> thizImports, Object thatFeatureInstance,
			@NonNull Map<String, String> thatImports) {
		var thizFull = resolveStepsToFullyQualifiedName((EList<Object>) thizFeatureInstance, thizImports);
		var thatFull = resolveStepsToFullyQualifiedName((EList<Object>) thatFeatureInstance, thatImports);
		return Objects.equal(thizFull, thatFull);
	}

	private String resolveStepsToFullyQualifiedName(EList<Object> steps, @NonNull Map<String, String> imports) {
		var baseString = steps.stream().map(val -> (String) val).collect(Collectors.joining("."));
		if (imports.containsKey(baseString)) {
			return imports.get(baseString);
		}
		return baseString;
	}

	@SuppressWarnings("unchecked")
	private static boolean featuresAreEquivalent(Object thizFeatureInstance, Map<String, String> thizImports,
			Object thatFeatureInstance, Map<String, String> thatImports) {
		if (thizFeatureInstance == thatFeatureInstance) {
			return true;
		}
		if (thatFeatureInstance == null) {
			return false;
		}
		if (thizFeatureInstance instanceof EList) {
			EList<EObject> thizList = (EList<EObject>) thizFeatureInstance;
			EList<EObject> thatList = (EList<EObject>) thatFeatureInstance;
			if (thizList.size() != thatList.size()) {
				return false;
			}
			Iterator<EObject> thizIterator = thizList.iterator();
			Iterator<EObject> thatIterator = thatList.iterator();
			while (thizIterator.hasNext()) {
				// While this is Iterator<EObject>, it may return String
				Object thizElement = thizIterator.next();
				Object thatElement = thatIterator.next();
				if (!featuresAreEquivalent(thizElement, thizImports, thatElement, thatImports)) {
					return false;
				}
			}
			return true;
		}
		if (thizFeatureInstance instanceof EObject) {
			return areEquivalent((EObject) thizFeatureInstance, thizImports, (EObject) thatFeatureInstance,
					thatImports);
		} else {
			return thizFeatureInstance.equals(thatFeatureInstance);
		}
	}
}
