package io.sapl.grammar.sapl.impl;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class EObjectUtil {

	public void dump(EObject eObject) {
		dumpEObjectTree(eObject, 0);
	}

	@SuppressWarnings("unchecked")
	public void dumpEObjectTree(EObject eObject, int indent) {
		log(indent, "Type: {}", eObject == null ? null : eObject.eClass().getName());
		if (eObject == null) {
			return;
		}
		EList<EStructuralFeature> features = eObject.eClass().getEAllStructuralFeatures();
		for (EStructuralFeature feature : features) {
			var featureInstance = eObject.eGet(feature);
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

	public void log(int indent, String message, Object... arguments) {
		log.trace("|  ".repeat(Math.max(0, indent - 1)) + "|- ".repeat(Math.min(1, indent)) + message, arguments);
	}
}
