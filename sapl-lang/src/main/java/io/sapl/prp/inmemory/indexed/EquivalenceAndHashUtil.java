package io.sapl.prp.inmemory.indexed;

import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;

import io.sapl.grammar.sapl.Expression;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

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
@Slf4j
@UtilityClass
public class EquivalenceAndHashUtil {
	private final static int HASH_SEED_PRIME = 7;
	private final static int PRIME = 31;

	public static int semanticHash(@NonNull EObject thiz, @NonNull Map<String, String> imports) {
		log.info("thiz : [{}] {}", thiz, thiz == null ? null : thiz.getClass().getSimpleName());
		var hash = HASH_SEED_PRIME;
		EList<EStructuralFeature> features = thiz.eClass().getEAllStructuralFeatures();
		for (EStructuralFeature feature : features) {
			log.info("inspecting feature: [{}] {}", feature.getName(), feature);
			var featureInstance = thiz.eGet(feature);
			log.info("feature instance: [{}] {}", featureInstance,
					featureInstance == null ? null : featureInstance.getClass().getSimpleName());
			hash = PRIME * hash + hash(featureInstance, imports);
		}
		log.info("hash is {} for {}", hash, thiz);
		return hash;
	}

	@SuppressWarnings("unchecked")
	private static int hash(Object featureInstance, @NonNull Map<String, String> imports) {
		if (featureInstance == null) {
			log.info("instance == null -> 0");
			return 0;
		}
		int hash = HASH_SEED_PRIME;
		if (featureInstance instanceof EList) {
			var thizList = (EList<EObject>) featureInstance;
			log.info("feature is a list[{}]. hash...", thizList.size());
			for (var element : thizList) {
				log.info("recursion for element [{}] of list...", element);
				hash = PRIME * hash + semanticHash(element, imports);
			}
		} else if (featureInstance instanceof EObject) {
			log.info("start recursion for EObject feature");
			hash = PRIME * hash + semanticHash((EObject) featureInstance, imports);
		} else {
			log.info("apply hashCode to primitives");
			hash = PRIME * hash + featureInstance.hashCode();
			log.error("hashed: {} and got: {}", featureInstance, featureInstance.hashCode());
		}
		return hash;
	}

	public boolean areEquivalent(@NonNull EObject thiz, @NonNull Map<String, String> thizImports, @NonNull EObject that,
			@NonNull Map<String, String> thatImports) {
		log.info("areEquivalent     : {} - {}", thiz, that);
		if (thiz == that) {
			return true;
		}
		if (that == null || thiz.eClass() != that.eClass()) {
			return false;
		}
		EList<EStructuralFeature> features = thiz.eClass().getEAllStructuralFeatures();
		log.info("The type {} has {} structural features", thiz.eClass().getName(), features.size());
		for (EStructuralFeature feature : features) {
			log.info("inspecting feature: [{}] {}", feature.getName(), feature);
			var thizFeatureInstance = thiz.eGet(feature);
			log.info("value for thiz    : [{}] {}",
					thizFeatureInstance != null ? thizFeatureInstance.getClass().getSimpleName() : null,
					thizFeatureInstance);
			var thatFeatureInstance = that.eGet(feature, true);
			log.info("value for that   : [{}] {}",
					thatFeatureInstance != null ? thatFeatureInstance.getClass().getSimpleName() : null,
					thatFeatureInstance);
			if (!featuresAreEquivalent(thizFeatureInstance, thizImports, thatFeatureInstance, thatImports)) {
				return false;
			}
		}
		return true;
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
			log.info("feature is a list[{}]. compare...", thizList.size());
			if (thizList.size() != thatList.size()) {
				log.info("unequal lenths of lists.");
				return false;
			}
			Iterator<EObject> thizIterator = thizList.iterator();
			Iterator<EObject> thatIterator = thatList.iterator();
			while (thizIterator.hasNext()) {
				EObject thizElement = thizIterator.next();
				EObject thatElement = thatIterator.next();
				log.info("recursion for elements [{} - {}] of list...", thizElement, thatElement);
				if (!areEquivalent(thizElement, thizImports, thatElement, thatImports)) {
					return false;
				}
			}
			return true;
		}
		if (thizFeatureInstance instanceof EObject) {
			log.info("start recursion for EObject Features");
			return areEquivalent((EObject) thizFeatureInstance, thizImports, (EObject) thatFeatureInstance,
					thatImports);
		} else {
			log.info("apply equals to primitives");
			return thizFeatureInstance.equals(thatFeatureInstance);
		}
	}
}
