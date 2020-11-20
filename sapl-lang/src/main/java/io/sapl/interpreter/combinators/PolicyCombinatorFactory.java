package io.sapl.interpreter.combinators;

import lombok.experimental.UtilityClass;

@UtilityClass
public class PolicyCombinatorFactory {

	public static PolicyCombinator getCombinator(String algorithm) {
		switch (algorithm) {
		case "deny-unless-permit":
			return new DenyUnlessPermitCombinator();
		case "permit-unless-deny":
			return new PermitUnlessDenyCombinator();
		case "deny-overrides":
			return new DenyOverridesCombinator();
		case "permit-overrides":
			return new PermitOverridesCombinator();
		case "only-one-applicable":
			return new OnlyOneApplicableCombinator();
		default: // "first-applicable":
			return new FirstApplicableCombinator();
		}
	}
}
