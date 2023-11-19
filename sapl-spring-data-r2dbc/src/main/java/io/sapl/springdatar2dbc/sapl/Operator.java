package io.sapl.springdatar2dbc.sapl;

import lombok.Getter;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.data.repository.query.parser.Part;

import java.util.Collections;
import java.util.List;

@Getter
public enum Operator {
    BETWEEN(true, List.of("BETWEEN")), LESS_THAN(false, List.of("<")), LESS_THAN_EQUAL(false, List.of("<=")),
    GREATER_THAN(false, List.of(">")), GREATER_THAN_EQUAL(false, List.of(">=")), BEFORE(false, List.of("<")),
    AFTER(false, List.of(">")), NOT_LIKE(false, List.of("NOT LIKE")), LIKE(false, List.of("LIKE")),
    NOT_IN(true, List.of("NIN")), IN(true, List.of("IN")), REGEX(false, List.of("LIKE")),
    EXISTS(false, List.of("EXISTS")), NEGATING_SIMPLE_PROPERTY(false, List.of("<>", "!=")),
    SIMPLE_PROPERTY(false, List.of("="));

    /**
     * Creates a new {@link Part.Type} using the given keyword, number of arguments
     * to be bound and operator. Keyword and operator can be {@literal null}.
     *
     * @param sqlQueryBasedKeywords are the keywords for relational databases that
     *                              correspond to the corresponding
     *                              {@link Part.Type}.
     */
    Operator(boolean isArray, List<String> sqlQueryBasedKeywords) {
        this.isArray               = isArray;
        this.sqlQueryBasedKeywords = Collections.unmodifiableList(sqlQueryBasedKeywords);
    }

    public static Operator getOperatorByKeyword(String keyword) {
        var replacedAllSpaceKeyword = keyword.toLowerCase().replaceAll("\\s", "");
        for (Operator operator : Operator.values()) {
            var sqlQueryBasedKeywordsContainsSearchedKeyword = operator.sqlQueryBasedKeywords.stream()
                    .map(key -> key.toLowerCase().replaceAll("\\s", "")).toList().contains(replacedAllSpaceKeyword);

            if (sqlQueryBasedKeywordsContainsSearchedKeyword) {
                return operator;
            }
        }
        throw new NotImplementedException();
    }

    final boolean      isArray;
    final List<String> sqlQueryBasedKeywords;
}
