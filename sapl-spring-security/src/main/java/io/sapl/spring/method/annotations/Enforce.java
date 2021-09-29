package io.sapl.spring.method.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The @Enforce annotation establishes a reactive policy enforcement point
 * (PEP). The PEP is only applicable to methods returning a
 * {@link org.reactivestreams.Publisher Publisher}, i.e., a {link
 * reactor.core.publisher.Flux Flux} or a {@link reactor.core.publisher.Mono
 * Mono}.
 * 
 * The publisher returned by the method is wrapped by the PEP. The PEP starts
 * processing, i.e, sending a subscription to the PDP, upon subscription time.
 * 
 * The established PEP also wires in matching handlers for obligations and
 * advice into the matching signal paths of the publisher.
 * 
 * The annotation supports two enforcement modes:
 * 
 * <ul>
 * <li>{@link EnforcementMode.ONCE}: The PEP will only consume the first
 * decision sent by the PDP and enforce the decision and its obligations and
 * advice throughout the entire lifecyle of the publisher.</li>
 * <li>{@link EnforcementMode.UNTIL_DENY}: The PEP will stay subscribed to the
 * decisions of the PDP the decision and its obligations and advice throughout
 * the entire lifecyle of the publisher or until access is denied, when an
 * {@link org.springframework.security.access.AccessDeniedException
 * AccessDeniedException} is raised.</li>
 * <li>{@link EnforcementMode.FILTER_UNLESS_PERMIT}: The PEP will stay
 * subscribed to the decisions of the PDP the decision and its obligations and
 * advice throughout the entire lifecyle of the publisher or until access is
 * denied. The PEP will subscribe to the underlying Publisher (resource access
 * point) immediately but will drop all onNext signals until the first PERMIT is
 * received from the PEP. When a the PEP returns a different decision than
 * PERMIT, all onNext signals will be dropped until a PERMIT is received. This
 * mode never raises an
 * {@link org.springframework.security.access.AccessDeniedException
 * AccessDeniedException}.</li>
 * </ul>
 * 
 * The parameters subject, action, resource, and environment can be used to
 * explicitly set the corresponding keys in the SAPL authorization subscription,
 * assuming that the Spring context and ObjectMapper are configured to be able
 * to serialize the resulting value into JSON.
 */
@Inherited
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
public @interface Enforce {

	/**
	 * @return the Spring-EL expression to whose evaluation result is to be used as
	 *         the subject in the authorization subscription to the PDP. If empty,
	 *         the PEP attempts to derive a best guess to describe the subject based
	 *         on the current Principal.
	 */
	String subject() default "";

	/**
	 * @return the Spring-EL expression to whose evaluation result is to be used as
	 *         the action in the authorization subscription to the PDP. If empty,
	 *         the PEP attempts to derive a best guess to describe the action based
	 *         on reflection.
	 */
	String action() default "";

	/**
	 * @return the Spring-EL expression to whose evaluation result is to be used as
	 *         the action in the authorization subscription to the PDP. If empty,
	 *         the PEP attempts to derive a best guess to describe the resource
	 *         based on reflection.
	 */
	String resource() default "";

	/**
	 * @return the Spring-EL expression to whose evaluation result is to be used as
	 *         the action in the authorization subscription to the PDP. If empty, no
	 *         environment is set in the subscription.
	 */
	String environment() default "";

	/**
	 * @return the enforcement mode to use. The default is ONCE.
	 */
	EnforcementMode mode() default EnforcementMode.ONCE;

	Class<?> genericsType();

}
