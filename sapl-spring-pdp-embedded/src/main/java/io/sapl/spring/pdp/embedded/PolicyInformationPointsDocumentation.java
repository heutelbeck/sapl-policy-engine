package io.sapl.spring.pdp.embedded;

import java.util.Collection;

import io.sapl.interpreter.pip.PolicyInformationPointDocumentation;
import lombok.Value;

@Value
public class PolicyInformationPointsDocumentation {
	Collection<PolicyInformationPointDocumentation> documentation;
}