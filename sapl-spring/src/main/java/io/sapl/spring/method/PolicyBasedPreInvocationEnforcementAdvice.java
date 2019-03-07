package io.sapl.spring.method;

import java.util.Collection;

import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.expression.EvaluationContext;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.core.Authentication;

/**
 * Method pre-invocation handling based on a SAPL policy decision point.
 */
public class PolicyBasedPreInvocationEnforcementAdvice implements PreInvocationEnforcementAdvice {

	protected final Log logger = LogFactory.getLog(getClass());

	private MethodSecurityExpressionHandler expressionHandler = new DefaultMethodSecurityExpressionHandler();

	@Override
	public boolean before(Authentication authentication, MethodInvocation mi, PreInvocationEnforcementAttribute attr) {
//		PreInvocationEnforcementAttribute preAttr = (PreInvocationEnforcementAttribute) attr;
//		EvaluationContext ctx = expressionHandler.createEvaluationContext(authentication, mi);
//		Expression preFilter = preAttr.getFilterExpression();
//		Expression preAuthorize = preAttr.getAuthorizeExpression();
//
//		if (preFilter != null) {
//			Object filterTarget = findFilterTarget(preAttr.getFilterTarget(), ctx, mi);
//
//			expressionHandler.filter(filterTarget, preFilter, ctx);
//		}
//
//		if (preAuthorize == null) {
//			return true;
//		}
		logger.info("Advice -> before -> authentication" + authentication);
		logger.info("Advice -> before -> MethodInvocation" + mi);
		logger.info("Advice -> before -> PreInvocationEnforcementAttribute" + attr);
		return true;
	}

	private Object findFilterTarget(String filterTargetName, EvaluationContext ctx, MethodInvocation mi) {
		Object filterTarget = null;

		if (filterTargetName.length() > 0) {
			filterTarget = ctx.lookupVariable(filterTargetName);
			if (filterTarget == null) {
				throw new IllegalArgumentException(
						"Filter target was null, or no argument with name " + filterTargetName + " found in method");
			}
		} else if (mi.getArguments().length == 1) {
			Object arg = mi.getArguments()[0];
			if (arg.getClass().isArray() || arg instanceof Collection<?>) {
				filterTarget = arg;
			}
			if (filterTarget == null) {
				throw new IllegalArgumentException("A PreFilter expression was set but the method argument type"
						+ arg.getClass() + " is not filterable");
			}
		}

		if (filterTarget.getClass().isArray()) {
			throw new IllegalArgumentException(
					"Pre-filtering on array types is not supported. " + "Using a Collection will solve this problem");
		}

		return filterTarget;
	}

	public void setExpressionHandler(MethodSecurityExpressionHandler expressionHandler) {
		this.expressionHandler = expressionHandler;
	}

}