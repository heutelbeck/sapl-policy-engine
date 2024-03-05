package io.sapl.prp;

import io.sapl.grammar.sapl.SAPL;

public record Document(String id, String source, SAPL sapl) {

}
