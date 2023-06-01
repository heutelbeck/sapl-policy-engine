/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.spring.method.metadata;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import org.springframework.core.annotation.AnnotationConfigurationException;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.RepeatableContainers;

import lombok.experimental.UtilityClass;

/**
 * A wrapper around {@link AnnotationUtils} that checks for, and errors on,
 * conflicting annotations. This is specifically important for Spring Security
 * annotations which are not designed to be repeatable.
 * <p>
 * There are numerous ways that two annotations of the same type may be attached
 * to the same method. For example, a class may implement a method defined in
 * two separate interfaces. If both of those interfaces have a `@PreAuthorize`
 * annotation, then it's unclear which `@PreAuthorize` expression Spring
 * Security should use.
 * <p>
 * Another way is when one of Spring Security's annotations is used as a
 * meta-annotation. In that case, two custom annotations can be declared, each
 * with their own `@PreAuthorize` declaration. If both custom annotations are
 * used on the same method, then it's unclear which `@PreAuthorize` expression
 * Spring Security should use.
 *
 * @author Josh Cummings, Dominic Heutelbeck
 */
@UtilityClass
final class AuthorizationAnnotationUtils {

	/**
	 * First lookup the annotation on the method, then on the class.
	 * 
	 * @param <A>            The annotation type.
	 * @param method		the method to examine
	 * @param annotationType The annotation type to lookup, e.g., @PreEnforce
	 * @return the annotation if found or, {@code null} otherwise
	 */
	public static <A extends Annotation> A findAuthorizeAnnotationOnMethodOrDeclaringClass(Method method,
			Class<A> annotationType) {
		var preAuthorize = findUniqueAnnotation(method, annotationType);
		return (preAuthorize != null) ? preAuthorize
				: findUniqueAnnotation(method.getDeclaringClass(), annotationType);
	}

	/**
	 * Perform an exhaustive search on the type hierarchy of the given
	 * {@link Method} for the annotation of type {@code annotationType}, including
	 * any annotations using {@code annotationType} as a meta-annotation.
	 * <p>
	 * If more than one is found, then throw an error.
	 * 
	 * @param method         the method declaration to search from
	 * @param annotationType the annotation type to search for
	 * @return the unique instance of the annotation attributed to the method,
	 *         {@code null} otherwise
	 * @throws AnnotationConfigurationException if more than one instance of the
	 *                                          annotation is found
	 */
	static <A extends Annotation> A findUniqueAnnotation(Method method, Class<A> annotationType) {
		MergedAnnotations mergedAnnotations = MergedAnnotations.from(method,
				MergedAnnotations.SearchStrategy.TYPE_HIERARCHY, RepeatableContainers.none());
		if (hasDuplicate(mergedAnnotations, annotationType)) {
			throw new AnnotationConfigurationException("Found more than one annotation of type " + annotationType
					+ " attributed to " + method
					+ " Please remove the duplicate annotations and publish a bean to handle your authorization logic.");
		}
		return AnnotationUtils.findAnnotation(method, annotationType);
	}

	/**
	 * Perform an exhaustive search on the type hierarchy of the given {@link Class}
	 * for the annotation of type {@code annotationType}, including any annotations
	 * using {@code annotationType} as a meta-annotation.
	 * <p>
	 * If more than one is found, then throw an error.
	 * 
	 * @param type           the type to search from
	 * @param annotationType the annotation type to search for
	 * @return the unique instance of the annotation attributed to the method,
	 *         {@code null} otherwise
	 * @throws AnnotationConfigurationException if more than one instance of the
	 *                                          annotation is found
	 */
	static <A extends Annotation> A findUniqueAnnotation(Class<?> type, Class<A> annotationType) {
		MergedAnnotations mergedAnnotations = MergedAnnotations.from(type,
				MergedAnnotations.SearchStrategy.TYPE_HIERARCHY, RepeatableContainers.none());
		if (hasDuplicate(mergedAnnotations, annotationType)) {
			throw new AnnotationConfigurationException("Found more than one annotation of type " + annotationType
					+ " attributed to " + type
					+ " Please remove the duplicate annotations and publish a bean to handle your authorization logic.");
		}
		return AnnotationUtils.findAnnotation(type, annotationType);
	}

	private static <A extends Annotation> boolean hasDuplicate(MergedAnnotations mergedAnnotations,
			Class<A> annotationType) {
		boolean alreadyFound = false;
		for (MergedAnnotation<Annotation> mergedAnnotation : mergedAnnotations) {
			if (mergedAnnotation.getType() == annotationType) {
				if (alreadyFound) {
					return true;
				}
				alreadyFound = true;
			}
		}
		return false;
	}

}
