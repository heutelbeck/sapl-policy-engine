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
package io.sapl.spring.method;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.access.method.AbstractMethodSecurityMetadataSource;
import org.springframework.util.ClassUtils;

import io.sapl.spring.method.post.PostEnforce;
import io.sapl.spring.method.post.PostInvocationEnforcementAttribute;
import io.sapl.spring.method.pre.PreEnforce;
import io.sapl.spring.method.pre.PreInvocationEnforcementAttribute;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class PolicyEnforcementMethodSecurityMetadataSource extends AbstractMethodSecurityMetadataSource {

	private final PolicyEnforcementAttributeFactory attributeFactory;

	@Override
	public Collection<ConfigAttribute> getAllConfigAttributes() {
		return null;
	}

	@Override
	public Collection<ConfigAttribute> getAttributes(Method method, Class<?> targetClass) {
		if (method.getDeclaringClass() == Object.class) {
			return Collections.emptyList();
		}

		ArrayList<ConfigAttribute> attrs = new ArrayList<>(2);

		PreEnforce preEnforce = findAnnotation(method, targetClass, PreEnforce.class);
		if (preEnforce != null) {
			log.trace("@PreEnforce on {}.{}: {}", targetClass.getSimpleName(), method.getName(), preEnforce);
			String preEnforceSubject = preEnforce.subject();
			String preEnforceAction = preEnforce.action();
			String preEnforceResource = preEnforce.resource();
			String preEnforceEnvironment = preEnforce.environment();
			PreInvocationEnforcementAttribute pre = attributeFactory.createPreInvocationAttribute(preEnforceSubject,
					preEnforceAction, preEnforceResource, preEnforceEnvironment);
			attrs.add(pre);
		}

		PostEnforce postEnforce = findAnnotation(method, targetClass, PostEnforce.class);
		if (postEnforce != null) {
			log.trace("@PostEnforce on {}.{}: {}", targetClass.getSimpleName(), method.getName(), postEnforce);
			String postEnforceSubject = postEnforce.subject();
			String postEnforceAction = postEnforce.action();
			String postEnforceResource = postEnforce.resource();
			String postEnforceEnvironment = postEnforce.environment();
			PostInvocationEnforcementAttribute post = attributeFactory.createPostInvocationAttribute(postEnforceSubject,
					postEnforceAction, postEnforceResource, postEnforceEnvironment);
			attrs.add(post);
		}

		attrs.trimToSize();

		return attrs;
	}

	/**
	 * See
	 * {@link org.springframework.security.access.prepost.PrePostAnnotationSecurityMetadataSource #findAnnotation(Method, Class)}
	 * the logic is the same as for @PreAuthorize and @PostAuthorize.
	 */
	private <A extends Annotation> A findAnnotation(Method method, Class<?> targetClass, Class<A> annotationClass) {
		log.trace("LOOKING FOR... {} on {} in {}", annotationClass.getSimpleName(), method.getName(),
				targetClass.getSimpleName());
		// The method may be on an interface, but we need attributes from the target
		// class.
		// If the target class is null, the method will be unchanged.
		Method specificMethod = ClassUtils.getMostSpecificMethod(method, targetClass);
		A annotation = AnnotationUtils.findAnnotation(specificMethod, annotationClass);
		
		if (annotation != null) {
			log.trace("{} found on specific method: {}", annotation, specificMethod);
			return annotation;
		}

		// Check the class-level (note declaringClass, not targetClass, which may not
		// actually implement the method)
		annotation = AnnotationUtils.findAnnotation(specificMethod.getDeclaringClass(), annotationClass);
		if (annotation != null) {
			log.trace("{} found on: {}", annotation, specificMethod.getDeclaringClass().getName());
			return annotation;
		}
		return null;
	}

}
