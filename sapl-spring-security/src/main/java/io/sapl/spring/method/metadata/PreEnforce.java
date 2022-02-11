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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The @PreEnforce annotation establishes a policy enforcement point (PEP) before the
 * invocation of the annotated method.
 */
@Inherited
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
public @interface PreEnforce {

	/**
	 * @return the Spring-EL expression to whose evaluation result is to be used as the
	 * subject in the authorization subscription to the PDP. If empty, the PEP attempts to
	 * derive a guess to describe the subject based on the current Principal.
	 */
	String subject() default "";

	/**
	 * @return the Spring-EL expression to whose evaluation result is to be used as the
	 * action in the authorization subscription to the PDP. If empty, the PEP attempts to
	 * derive a guess to describe the action based on reflection.
	 */
	String action() default "";

	/**
	 * @return the Spring-EL expression to whose evaluation result is to be used as the
	 * action in the authorization subscription to the PDP. If empty, the PEP attempts to
	 * derive a guess to describe the resource based on reflection.
	 */
	String resource() default "";

	/**
	 * @return the Spring-EL expression to whose evaluation result is to be used as the
	 * action in the authorization subscription to the PDP. If empty, no environment is
	 * set in the subscription.
	 */
	String environment() default "";

	/**
	 * @return the type of the generic parameter of the return type being secured. Helps
	 * due to Java type erasure at runtime. Defaults to {@code Object.class}.
	 */
	Class<?> genericsType() default Object.class;

}
