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

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class PolicyEnforcementMethodSecurityMetadataSource extends AbstractMethodSecurityMetadataSource {

	private final PolicyEnforcementAttributeFactory attributeFactory;

	@Override
	public Collection<ConfigAttribute> getAllConfigAttributes() {
		return null;
	}

	public Collection<ConfigAttribute> getAttributes(Method method, Class<?> targetClass) {
		if (method.getDeclaringClass() == Object.class) {
			return Collections.emptyList();
		}

		logger.trace("Looking for Pre/Post enforcement annotations for method '" + method.getName()
				+ "' on target class '" + targetClass + "'");
		PreEnforce preEnforce = findAnnotation(method, targetClass, PreEnforce.class);

		if (preEnforce != null) {
			logger.trace("Found @PreEnforce: " + preEnforce);
		}
		PostEnforce postEnforce = findAnnotation(method, targetClass, PostEnforce.class);
		if (postEnforce != null) {
			logger.trace("Found @PostEnforce: " + postEnforce);
		}

		if (preEnforce == null && postEnforce == null) {
			// There is no meta-data so return
			logger.trace("No expression annotations found");
			return Collections.emptyList();
		}

		String preEnforceSubject = preEnforce == null ? null : preEnforce.subject();
		String preEnforceAction = preEnforce == null ? null : preEnforce.action();
		String preEnforceResource = preEnforce == null ? null : preEnforce.resource();
		String preEnforceEnvironment = preEnforce == null ? null : preEnforce.environment();

		String postEnforceSubject = postEnforce == null ? null : postEnforce.subject();
		String postEnforceAction = postEnforce == null ? null : postEnforce.action();
		String postEnforceResource = postEnforce == null ? null : postEnforce.resource();
		String postEnforceEnvironment = postEnforce == null ? null : postEnforce.environment();

		ArrayList<ConfigAttribute> attrs = new ArrayList<>(2);

		PreInvocationEnforcementAttribute pre = attributeFactory.createPreInvocationAttribute(preEnforceSubject,
				preEnforceAction, postEnforceResource, postEnforceEnvironment);

		if (pre != null) {
			attrs.add(pre);
		}

		PostInvocationEnforcementAttribute post = attributeFactory.createPostInvocationAttribute(postEnforceSubject,
				postEnforceAction, preEnforceResource, preEnforceEnvironment);

		if (post != null) {
			attrs.add(post);
		}

		attrs.trimToSize();

		return attrs;
	}

	/**
	 * See
	 * {@link org.springframework.security.access.prepost.PrePostAnnotationSecurityMetadataSource #findAnnotation(Method, Class)}
	 * the logic here is the same as for @PreAuthorize and @PostAuthorize.
	 */
	private <A extends Annotation> A findAnnotation(Method method, Class<?> targetClass, Class<A> annotationClass) {
		// The method may be on an interface, but we need attributes from the target
		// class.
		// If the target class is null, the method will be unchanged.
		Method specificMethod = ClassUtils.getMostSpecificMethod(method, targetClass);
		A annotation = AnnotationUtils.findAnnotation(specificMethod, annotationClass);

		if (annotation != null) {
			logger.debug(annotation + " found on specific method: " + specificMethod);
			return annotation;
		}

		// Check the original (e.g. interface) method
		if (specificMethod != method) {
			annotation = AnnotationUtils.findAnnotation(method, annotationClass);

			if (annotation != null) {
				logger.debug(annotation + " found on: " + method);
				return annotation;
			}
		}

		// Check the class-level (note declaringClass, not targetClass, which may not
		// actually implement the method)
		annotation = AnnotationUtils.findAnnotation(specificMethod.getDeclaringClass(), annotationClass);

		if (annotation != null) {
			logger.debug(annotation + " found on: " + specificMethod.getDeclaringClass().getName());
			return annotation;
		}

		return null;
	}
}
