/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.prp.index.canonical;

import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;

import com.google.common.base.Objects;

import lombok.NonNull;
import lombok.experimental.UtilityClass;

/**
 * For indexing, expressions must be compared based on equivalence.
 * <p>
 * a) Parent nodes in the ASTs (eContainer) are irrelevant for this comparison
 * <p>
 * b) Import statements in policies may lead to locally equal expression objects
 * to be semantically different, or locally different objects to we semantically
 * equivalent. This the case for functions and attribute finders. These have to
 * be explicitly handled and imports must be resolved before comparing or
 * hashing.
 */
@UtilityClass
public class EquivalenceAndHashUtil {

    private static final int HASH_SEED_PRIME = 7;
    private static final int PRIME           = 31;

    public static int semanticHash(@NonNull EObject thiz, @NonNull Map<String, String> imports) {
        var hash = HASH_SEED_PRIME;
        hash = PRIME * hash + thiz.eClass().hashCode(); // else literals all return
                                                        // HASH_SEED_PRIME and collide
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

    private static int hash(Object featureInstance, @NonNull Map<String, String> imports) {
        if (featureInstance == null) {
            return 0;
        }
        int hash = HASH_SEED_PRIME;
        if (featureInstance instanceof EList<?> eListFeatureInscance) {
            var thizList = eListFeatureInscance;
            for (Object element : thizList) {
                hash = PRIME * hash + hash(element, imports);
            }
        } else if (featureInstance instanceof EObject eObjectFeatureInscance) {
            hash = PRIME * hash + semanticHash(eObjectFeatureInscance, imports);
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
        if (thiz.eClass() != that.eClass()) {
            return false;
        }
        EList<EStructuralFeature> features = thiz.eClass().getEAllStructuralFeatures();
        for (EStructuralFeature feature : features) {
            var thisFeatureInstance = thiz.eGet(feature);
            var thatFeatureInstance = that.eGet(feature, true);
            if ("fsteps".equals(feature.getName())) {
                return fStepsAreEquivalentWithRegardsToImports(thisFeatureInstance, thizImports, thatFeatureInstance,
                        thatImports);
            }
            if (!featuresAreEquivalent(thisFeatureInstance, thizImports, thatFeatureInstance, thatImports)) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private boolean fStepsAreEquivalentWithRegardsToImports(Object thisFeatureInstance,
            @NonNull Map<String, String> thisImports, Object thatFeatureInstance,
            @NonNull Map<String, String> thatImports) {
        var thisFull = resolveStepsToFullyQualifiedName((EList<Object>) thisFeatureInstance, thisImports);
        var thatFull = resolveStepsToFullyQualifiedName((EList<Object>) thatFeatureInstance, thatImports);
        return Objects.equal(thisFull, thatFull);
    }

    private String resolveStepsToFullyQualifiedName(EList<Object> steps, @NonNull Map<String, String> imports) {
        var baseString = steps.stream().map(String.class::cast).collect(Collectors.joining("."));
        var importBase = imports.get(baseString);
        if (importBase != null) {
            return importBase;
        }
        return baseString;
    }

    static boolean featuresAreEquivalent(Object thisFeatureInstance, Map<String, String> thisImports,
            Object thatFeatureInstance, Map<String, String> thatImports) {
        if (thisFeatureInstance == thatFeatureInstance) {
            return true;
        }
        if (thatFeatureInstance == null) {
            return false;
        }
        if (thisFeatureInstance instanceof EList<?> thizList && thatFeatureInstance instanceof EList<?> thatList) {
            if (thizList.size() != thatList.size()) {
                return false;
            }
            var thizIterator = thizList.iterator();
            var thatIterator = thatList.iterator();
            while (thizIterator.hasNext()) {
                // While this is Iterator<EObject>, it may return String
                Object thizElement = thizIterator.next();
                Object thatElement = thatIterator.next();
                if (!featuresAreEquivalent(thizElement, thisImports, thatElement, thatImports)) {
                    return false;
                }
            }
            return true;
        }
        if (thisFeatureInstance instanceof EObject thisObject && thatFeatureInstance instanceof EObject thatObject) {
            return areEquivalent(thisObject, thisImports, thatObject, thatImports);
        } else {
            return thisFeatureInstance.equals(thatFeatureInstance);
        }
    }

}
