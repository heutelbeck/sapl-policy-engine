/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.access.method.AbstractMethodSecurityMetadataSource;
import org.springframework.util.ClassUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class SaplMethodSecurityMetadataSource extends AbstractMethodSecurityMetadataSource {

	private final SaplAttributeFactory attributeFactory;

	@Override
	public Collection<ConfigAttribute> getAllConfigAttributes() {
		return null;
	}

	@Override
	public Collection<ConfigAttribute> getAttributes(Method method, Class<?> targetClass) {
		if (method.getDeclaringClass() == Object.class)
			return Collections.emptyList();

		var attributes = new ArrayList<ConfigAttribute>(3);

		var preEnforce = findAnnotation(method, targetClass, PreEnforce.class);
		if (preEnforce != null) {
			log.trace("@PreEnforce on {}.{}: {}", targetClass.getSimpleName(), method.getName(), preEnforce);
			attributes.add(attributeFactory.attributeFrom(preEnforce));
		}

		var postEnforce = findAnnotation(method, targetClass, PostEnforce.class);
		if (postEnforce != null) {
			log.trace("@PostEnforce on {}.{}: {}", targetClass.getSimpleName(), method.getName(), postEnforce);
			attributes.add(attributeFactory.attributeFrom(postEnforce));
		}

		var enforceTillDenied = findAnnotation(method, targetClass, EnforceTillDenied.class);
		if (enforceTillDenied != null) {
			log.trace("@EnforceTillDenied on {}.{}: {}", targetClass.getSimpleName(), method.getName(),
					enforceTillDenied);
			attributes.add(attributeFactory.attributeFrom(enforceTillDenied));
		}

		var enforceDropWhileDenied = findAnnotation(method, targetClass, EnforceDropWhileDenied.class);
		if (enforceDropWhileDenied != null) {
			log.trace("@EnforceDropWhileDenied on {}.{}: {}", targetClass.getSimpleName(), method.getName(),
					enforceTillDenied);
			attributes.add(attributeFactory.attributeFrom(enforceDropWhileDenied));
		}

		var enforceRecoverableIfDenied = findAnnotation(method, targetClass, EnforceRecoverableIfDenied.class);
		if (enforceRecoverableIfDenied != null) {
			log.trace("@EnforceRecoverableIfDenied on {}.{}: {}", targetClass.getSimpleName(), method.getName(),
					enforceTillDenied);
			attributes.add(attributeFactory.attributeFrom(enforceRecoverableIfDenied));
		}

		attributes.trimToSize();

		return attributes;
	}

	/**
	 * See
	 * {@link org.springframework.security.access.prepost.PrePostAnnotationSecurityMetadataSource
	 * #findAnnotation(Method, Class)} the logic is the same as for @PreAuthorize
	 * and @PostAuthorize.
	 */
	private <A extends Annotation> A findAnnotation(Method method, Class<?> targetClass, Class<A> annotationClass) {
		// The method may be on an interface, but we need attributes from the target
		// class. If the target class is null, the method will be unchanged.
		Method specificMethod = ClassUtils.getMostSpecificMethod(method, targetClass);
		A      annotation     = AnnotationUtils.findAnnotation(specificMethod, annotationClass);
		if (annotation != null)
			return annotation;

		// Check the class-level (note declaringClass, not targetClass, which may not
		// actually implement the method)
		annotation = AnnotationUtils.findAnnotation(specificMethod.getDeclaringClass(), annotationClass);
		return annotation;
	}

}
