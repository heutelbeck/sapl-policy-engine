package io.sapl.test.dsl.interfaces;

import java.util.List;

public interface IntegrationTestConfiguration {
    List<String> getDocumentInputStrings();

    String getPDPConfigInputString();
}
