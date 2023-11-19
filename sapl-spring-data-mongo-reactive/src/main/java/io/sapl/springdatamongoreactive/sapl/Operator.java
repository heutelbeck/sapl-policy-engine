package io.sapl.springdatamongoreactive.sapl;

import io.sapl.springdatamongoreactive.sapl.utils.SaplCondition;
import lombok.Getter;
import org.apache.commons.lang3.NotImplementedException;

import java.util.Collections;
import java.util.List;

/**
 * This enum class is used to use a uniform language when it comes to creating
 * queries and converting conditions from {@link io.sapl.api.pdp.Decision}s to
 * {@link SaplCondition}s.
 */
@Getter
public enum Operator {

    LESS_THAN(List.of("IsLessThan", "LessThan"), List.of("$lt", "lt")),
    LESS_THAN_EQUAL(List.of("IsLessThanEqual", "LessThanEqual"), List.of("$lte", "lte")),
    GREATER_THAN(List.of("IsGreaterThan", "GreaterThan"), List.of("$gt", "gt")),
    GREATER_THAN_EQUAL(List.of("IsGreaterThanEqual", "GreaterThanEqual"), List.of("$gte", "gte")),
    BEFORE(List.of("IsBefore", "Before"), List.of("$lt", "lt")),
    AFTER(List.of("IsAfter", "After"), List.of("$gt", "gt")),
    NOT_IN(List.of("IsNotIn", "NotIn"), List.of("$nin", "nin")), IN(List.of("IsIn", "In"), List.of("$in", "in")),
    NEAR(List.of("IsNear", "Near"), List.of("$near", "near")),
    REGEX(List.of("MatchesRegex", "Matches", "Regex", "IsStartingWith", "StartingWith", "StartsWith", "IsEndingWith",
            "EndingWith", "EndsWith", "IsLike", "Like", "IsContaining", "Containing", "Contains"),
            List.of("$regex", "regex")),
    EXISTS(List.of("Exists"), List.of("$exists", "exists")),
    NEGATING_SIMPLE_PROPERTY(List.of("IsNot", "Not"), List.of("$ne", "ne")),
    SIMPLE_PROPERTY(List.of("Is", "Equals"), List.of("$eq", "eq"));

    final List<String> methodNameBasedKeywords;
    final List<String> mongoBasedKeywords;

    Operator(List<String> methodNameBasedKeywords, List<String> mongoBasedKeywords) {
        this.methodNameBasedKeywords = Collections.unmodifiableList(methodNameBasedKeywords);
        this.mongoBasedKeywords      = Collections.unmodifiableList(mongoBasedKeywords);
    }

    /**
     * Searches the entire enum for a keyword.
     *
     * @param keyword the keyword to search for.
     * @return the found {@link Operator} that contains the keyword.
     */
    public static Operator getOperatorByKeyword(String keyword) {
        var replacedAllSpaceKeyword = keyword.toLowerCase().replaceAll("\\s", "");
        for (Operator operator : Operator.values()) {
            var methodNameBasedKeywordsContainsSearchedKeyword = operator.methodNameBasedKeywords.stream()
                    .map(key -> key.toLowerCase().replaceAll("\\s", "")).toList().contains(replacedAllSpaceKeyword);
            var mongoBasedKeywordsContainsSearchedKeyword      = operator.mongoBasedKeywords.stream()
                    .map(key -> key.toLowerCase().replaceAll("\\s", "")).toList().contains(replacedAllSpaceKeyword);

            if (methodNameBasedKeywordsContainsSearchedKeyword || mongoBasedKeywordsContainsSearchedKeyword) {
                return operator;
            }
        }
        throw new NotImplementedException();
    }

}
