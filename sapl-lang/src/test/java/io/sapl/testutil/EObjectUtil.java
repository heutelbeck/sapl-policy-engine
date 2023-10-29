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
package io.sapl.testutil;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;

import lombok.extern.slf4j.Slf4j;

/**
 * Utiltiy to log eObjects to the console.
 */
@Slf4j
public class EObjectUtil {

    /**
     * Log the structure of an eObject for debugging of tests.
     * 
     * @param eObject an eObject
     */
    public static void dump(EObject eObject) {
        dumpEObjectTree(eObject, 0);
    }

    @SuppressWarnings("unchecked")
    private static void dumpEObjectTree(EObject eObject, int indent) {
        log(indent, "Type: {}", eObject == null ? null : eObject.eClass().getName());
        if (eObject == null) {
            return;
        }
        EList<EStructuralFeature> features = eObject.eClass().getEAllStructuralFeatures();
        for (EStructuralFeature feature : features) {
            var featureInstance = eObject.eGet(feature);
            if (featureInstance != null) {
                log(indent + 1, "Feature:{} {}", feature.getName(), feature.getEType().getName());
                if (featureInstance instanceof EObject) {
                    dumpEObjectTree((EObject) featureInstance, indent + 2);
                } else if (featureInstance instanceof EList) {
                    for (var feat : (EList<Object>) featureInstance) {
                        if (feat instanceof EObject)
                            dumpEObjectTree((EObject) feat, indent + 2);
                        else
                            log(indent + 2, feat.toString());
                    }
                } else {
                    log(indent + 2, String.valueOf(featureInstance));
                }
            }
        }
    }

    private static void log(int indent, String message, Object... arguments) {
        log.trace("|  ".repeat(Math.max(0, indent - 1)) + "|- ".repeat(Math.min(1, indent)) + message, arguments);
    }

}
