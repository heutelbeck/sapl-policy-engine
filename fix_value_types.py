filepath = 'C:/devkit/git/sapl-policy-engine/sapl-test/src/main/java/io/sapl/test/plain/ScenarioInterpreter.java'

with open(filepath, 'r') as f:
    content = f.read()

# Add necessary imports if not present
imports_to_add = [
    'import io.sapl.api.model.ArrayValue;',
    'import io.sapl.api.model.BooleanValue;',
    'import io.sapl.api.model.NullValue;',
    'import io.sapl.api.model.NumberValue;',
    'import io.sapl.api.model.ObjectValue;',
    'import io.sapl.api.model.TextValue;',
    'import io.sapl.api.model.UndefinedValue;',
]

# Find the import section - add after existing io.sapl imports
import_insert_point = content.find('import io.sapl.api.model.Value;')
if import_insert_point > 0:
    # Find end of that import line
    end_of_line = content.find('\n', import_insert_point)
    # Add new imports after
    new_imports = '\n'.join(imports_to_add)
    # Check if imports already exist
    if 'import io.sapl.api.model.ArrayValue;' not in content:
        content = content[:end_of_line+1] + new_imports + '\n' + content[end_of_line+1:]

# Remove JsonNode import if present
content = content.replace('import com.fasterxml.jackson.databind.JsonNode;\n', '')

# Fix the matchExtendedConstraint method signature and body
old_method1 = '''    /**
     * Matches an extended constraint (obligation/advice) against a list of
     * constraints.
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
    }'''

new_method1 = '''    /**
     * Matches an extended constraint (obligation/advice) against a list of
     * constraints.
     */
    private boolean matchExtendedConstraint(List<Value> constraints,
            ExtendedObjectMatcherContext extendedMatch) {
        if (extendedMatch instanceof DefaultExtendedMatcherContext defaultMatch) {
            return constraints.stream().anyMatch(c -> matchDefaultObject(c, defaultMatch.defaultObjectMatcher()));
        } else if (extendedMatch instanceof KeyValueObjectMatcherContext keyValueMatch) {
            var key = unquoteString(keyValueMatch.key.getText());
            return constraints.stream().anyMatch(c -> {
                if (!(c instanceof ObjectValue obj) || !obj.containsKey(key)) {
                    return false;
                }
                if (keyValueMatch.matcher == null) {
                    return true; // Just "containing key" check
                }
                return matchNode(obj.get(key), keyValueMatch.matcher);
            });
        }
        return false;
    }'''

content = content.replace(old_method1, new_method1)

# Fix the matchDefaultObject method
old_method2 = '''    /**
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
    }'''

new_method2 = '''    /**
     * Matches a default object matcher against a Value.
     */
    private boolean matchDefaultObject(Value node, DefaultObjectMatcherContext matcher) {
        if (matcher instanceof ExactMatchObjectMatcherContext exactMatch) {
            var expected = ValueConverter.convert(exactMatch.equalTo);
            return expected.equals(node);
        } else if (matcher instanceof MatchingObjectMatcherContext matchingMatch) {
            return matchNode(node, matchingMatch.nodeMatcher());
        }
        return false;
    }'''

content = content.replace(old_method2, new_method2)

# Fix the matchNode method
old_method3 = '''    /**
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
    }'''

new_method3 = '''    /**
     * Matches a node against a node matcher.
     */
    private boolean matchNode(Value node, NodeMatcherContext matcher) {
        if (matcher instanceof NullMatcherContext) {
            return node == null || node instanceof NullValue;
        } else if (matcher instanceof TextMatcherContext textMatch) {
            if (!(node instanceof TextValue textNode)) {
                return false;
            }
            if (textMatch.stringOrStringMatcher() == null) {
                return true; // Just "is text" check
            }
            return matchString(textNode.value(), textMatch.stringOrStringMatcher());
        } else if (matcher instanceof NumberMatcherContext numMatch) {
            if (!(node instanceof NumberValue numNode)) {
                return false;
            }
            if (numMatch.number == null) {
                return true; // Just "is number" check
            }
            return numNode.value().doubleValue() == Double.parseDouble(numMatch.number.getText());
        } else if (matcher instanceof BooleanMatcherContext boolMatch) {
            if (!(node instanceof BooleanValue boolNode)) {
                return false;
            }
            if (boolMatch.booleanLiteral() == null) {
                return true; // Just "is boolean" check
            }
            var expected = boolMatch.booleanLiteral() instanceof TrueLiteralContext;
            return boolNode.value() == expected;
        } else if (matcher instanceof ArrayMatcherContext arrayMatch) {
            if (!(node instanceof ArrayValue arrayNode)) {
                return false;
            }
            if (arrayMatch.arrayMatcherBody() == null) {
                return true; // Just "is array" check
            }
            return matchArrayBody(arrayNode, arrayMatch.arrayMatcherBody());
        } else if (matcher instanceof ObjectMatcherContext objMatch) {
            if (!(node instanceof ObjectValue objNode)) {
                return false;
            }
            if (objMatch.objectMatcherBody() == null) {
                return true; // Just "is object" check
            }
            return matchObjectBody(objNode, objMatch.objectMatcherBody());
        }
        return false;
    }'''

content = content.replace(old_method3, new_method3)

# Fix the matchArrayBody method
old_method4 = '''    /**
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
    }'''

new_method4 = '''    /**
     * Matches an array body.
     */
    private boolean matchArrayBody(ArrayValue array, ArrayMatcherBodyContext body) {
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
    }'''

content = content.replace(old_method4, new_method4)

# Fix the matchObjectBody method
old_method5 = '''    /**
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
    }'''

new_method5 = '''    /**
     * Matches an object body.
     */
    private boolean matchObjectBody(ObjectValue obj, ObjectMatcherBodyContext body) {
        for (var member : body.members) {
            var key = unquoteString(member.key.getText());
            if (!obj.containsKey(key)) {
                return false;
            }
            if (!matchNode(obj.get(key), member.matcher)) {
                return false;
            }
        }
        return true;
    }'''

content = content.replace(old_method5, new_method5)

# Fix the resource check in HasResourceMatcherContext
old_resource_check = '''                var resource = decision.resource();
                if (resCtx.defaultMatcher == null) {
                    return resource != null && resource.isDefined();
                }
                if (resource == null || !resource.isDefined()) {
                    return false;
                }'''

new_resource_check = '''                var resource = decision.resource();
                if (resCtx.defaultMatcher == null) {
                    return resource != null && !(resource instanceof UndefinedValue);
                }
                if (resource == null || resource instanceof UndefinedValue) {
                    return false;
                }'''

content = content.replace(old_resource_check, new_resource_check)

with open(filepath, 'w') as f:
    f.write(content)

print("Fixed Value type issues")
