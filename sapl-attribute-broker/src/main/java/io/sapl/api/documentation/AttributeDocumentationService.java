package io.sapl.api.documentation;

import java.util.Collection;

public interface AttributeDocumentationService {

    Collection<PolicyInformationPointDocumentation> getDocumentationOfAllPolicyInformationPoints();

    Collection<AttributeDocumentation> getDocumentationOfAllAttributesWithNamePrefix(String namePrefix);

}
