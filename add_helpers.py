filepath = 'C:/devkit/git/sapl-policy-engine/sapl-test/src/main/java/io/sapl/test/plain/ScenarioInterpreter.java'

with open(filepath, 'r') as f:
    content = f.read()

# Helper methods to add
helper_methods = '''
    /**
     * Matches an extended constraint (obligation/advice) against a list of constraints.
     */
    private boolean matchExtendedConstraint(java.util.List<JsonNode> constraints,
            ExtendedObjectMatcherContext extendedMatch) {
        if (extendedMatch instanceof DefaultExtendedMatcherContext defaultMatch) {
            return constraints.stream().anyMatch(c -> matchDefaultObject(c, defaultMatch.defaultObjectMatcher()));
        } else if (extendedMatch instanceof KeyValueObjectMatcherContext keyValueMatch) {
            var key = unquoteString(keyValueMatch.key.getText());
            return constraints.stream().anyMatch(c -> {
                if (!c.isObject() || !c.has(key)) {
                    return false;
                }
                if (keyValueMatch.matcher == null) {
                    return true; // Just "containing key" check
                }
                return matchNode(c.get(key), keyValueMatch.matcher);
            });
        }
        return false;
    }

    /**
     * Matches a default object matcher against a JSON node.
     */
    private boolean matchDefaultObject(JsonNode node, DefaultObjectMatcherContext matcher) {
        if (matcher instanceof ExactMatchObjectMatcherContext exactMatch) {
            var expected = ValueConverter.convert(exactMatch.equalTo);
            return expected.equals(node);
        } else if (matcher instanceof MatchingObjectMatcherContext matchingMatch) {
            return matchNode(node, matchingMatch.nodeMatcher());
        }
        return false;
    }

    /**
     * Matches a node against a node matcher.
     */
    private boolean matchNode(JsonNode node, NodeMatcherContext matcher) {
        if (matcher instanceof NullMatcherContext) {
            return node == null || node.isNull();
        } else if (matcher instanceof TextMatcherContext textMatch) {
            if (!node.isTextual()) {
                return false;
            }
            if (textMatch.stringOrStringMatcher() == null) {
                return true; // Just "is text" check
            }
            return matchString(node.asText(), textMatch.stringOrStringMatcher());
        } else if (matcher instanceof NumberMatcherContext numMatch) {
            if (!node.isNumber()) {
                return false;
            }
            if (numMatch.number == null) {
                return true; // Just "is number" check
            }
            return node.asDouble() == Double.parseDouble(numMatch.number.getText());
        } else if (matcher instanceof BooleanMatcherContext boolMatch) {
            if (!node.isBoolean()) {
                return false;
            }
            if (boolMatch.booleanLiteral() == null) {
                return true; // Just "is boolean" check
            }
            var expected = boolMatch.booleanLiteral() instanceof TrueLiteralContext;
            return node.asBoolean() == expected;
        } else if (matcher instanceof ArrayMatcherContext arrayMatch) {
            if (!node.isArray()) {
                return false;
            }
            if (arrayMatch.arrayMatcherBody() == null) {
                return true; // Just "is array" check
            }
            return matchArrayBody(node, arrayMatch.arrayMatcherBody());
        } else if (matcher instanceof ObjectMatcherContext objMatch) {
            if (!node.isObject()) {
                return false;
            }
            if (objMatch.objectMatcherBody() == null) {
                return true; // Just "is object" check
            }
            return matchObjectBody(node, objMatch.objectMatcherBody());
        }
        return false;
    }

    /**
     * Matches a string against a string matcher.
     */
    private boolean matchString(String text, StringOrStringMatcherContext matcher) {
        if (matcher instanceof PlainStringMatcherContext plain) {
            var expected = unquoteString(plain.text.getText());
            return expected.equals(text);
        } else if (matcher instanceof ComplexStringMatcherContext complex) {
            return matchComplexString(text, complex.stringMatcher());
        }
        return false;
    }

    /**
     * Matches a string against a complex string matcher.
     */
    private boolean matchComplexString(String text, StringMatcherContext matcher) {
        if (matcher instanceof StringIsNullContext) {
            return text == null;
        } else if (matcher instanceof StringIsBlankContext) {
            return text != null && text.isBlank();
        } else if (matcher instanceof StringIsEmptyContext) {
            return text != null && text.isEmpty();
        } else if (matcher instanceof StringIsNullOrEmptyContext) {
            return text == null || text.isEmpty();
        } else if (matcher instanceof StringIsNullOrBlankContext) {
            return text == null || text.isBlank();
        } else if (matcher instanceof StringEqualCompressedWhitespaceContext ctx) {
            var expected = unquoteString(ctx.matchValue.getText());
            return text.replaceAll("\\\\s+", " ").equals(expected);
        } else if (matcher instanceof StringEqualIgnoringCaseContext ctx) {
            var expected = unquoteString(ctx.matchValue.getText());
            return text.equalsIgnoreCase(expected);
        } else if (matcher instanceof StringMatchesRegexContext ctx) {
            var regex = unquoteString(ctx.regex.getText());
            return java.util.regex.Pattern.matches(regex, text);
        } else if (matcher instanceof StringStartsWithContext ctx) {
            var prefix = unquoteString(ctx.prefix.getText());
            return ctx.caseInsensitive != null
                ? text.toLowerCase().startsWith(prefix.toLowerCase())
                : text.startsWith(prefix);
        } else if (matcher instanceof StringEndsWithContext ctx) {
            var postfix = unquoteString(ctx.postfix.getText());
            return ctx.caseInsensitive != null
                ? text.toLowerCase().endsWith(postfix.toLowerCase())
                : text.endsWith(postfix);
        } else if (matcher instanceof StringContainsContext ctx) {
            var substring = unquoteString(ctx.text.getText());
            return ctx.caseInsensitive != null
                ? text.toLowerCase().contains(substring.toLowerCase())
                : text.contains(substring);
        } else if (matcher instanceof StringContainsInOrderContext ctx) {
            var currentPos = 0;
            for (var substringToken : ctx.substrings) {
                var substring = unquoteString(substringToken.getText());
                var index = text.indexOf(substring, currentPos);
                if (index < 0) {
                    return false;
                }
                currentPos = index + substring.length();
            }
            return true;
        } else if (matcher instanceof StringWithLengthContext ctx) {
            var expected = Integer.parseInt(ctx.length.getText());
            return text.length() == expected;
        }
        return false;
    }

    /**
     * Matches an array body.
     */
    private boolean matchArrayBody(JsonNode array, ArrayMatcherBodyContext body) {
        var matchers = body.matchers;
        if (array.size() != matchers.size()) {
            return false;
        }
        for (int i = 0; i < matchers.size(); i++) {
            if (!matchNode(array.get(i), matchers.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Matches an object body.
     */
    private boolean matchObjectBody(JsonNode obj, ObjectMatcherBodyContext body) {
        for (var member : body.members) {
            var key = unquoteString(member.key.getText());
            if (!obj.has(key)) {
                return false;
            }
            if (!matchNode(obj.get(key), member.matcher)) {
                return false;
            }
        }
        return true;
    }

'''

# Find where to insert - before "Creates a basic DecisionMatcher"
insertion_point = content.find('    /**\n     * Creates a basic DecisionMatcher')
if insertion_point > 0:
    content = content[:insertion_point] + helper_methods + content[insertion_point:]
    with open(filepath, 'w') as f:
        f.write(content)
    print("Added helper methods")
else:
    print("Could not find insertion point")
