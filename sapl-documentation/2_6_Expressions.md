---
layout: default
title: Expressions
parent: The SAPL Policy Language
grand_parent: SAPL Reference
nav_order: 106
---

## SAPL Expressions

To ensure flexibility, various parts of a policy can be **expressions** that are evaluated at runtime. For example, a policy's target must be an expression evaluating to `true` or `false`. SAPL contains a uniform expression language that offers various useful features while still being easy to read and write.

Since JSON is the base data model, each expression evaluates to a JSON data type. This section covers the lexical building blocks, data types, and expression syntax.

### Identifiers

Multiple elements in policies or policy sets require identifiers. For example, object keys in dot notation, function name components, and variable names all use identifiers.

An identifier consists of alphanumeric characters, `_` and `$`, and must not start with a number.

Valid Identifiers

```
a_long_name
aLongName
$name
_name
name123
```

Invalid Identifiers

```
a#name
1name
```

#### Reserved Identifiers

Certain SAPL keywords can also be used as identifiers in expression contexts such as dot notation key steps, object keys, and function name components. These reserved identifiers are:

- The authorization subscription attributes: `subject`, `action`, `resource`, `environment`
- The combining algorithm keywords: `abstain`, `errors`, `first`, `or`, `priority`, `propagate`, `strict`, `unanimous`, `unique`

For example, `object.subject` uses `subject` as a key step, and `{priority: 5}` uses `priority` as an object key.

#### Variable Names

Variable names (after the `var` keyword) accept plain identifiers and the combining algorithm keywords listed above. All other keywords -- including the subscription attribute keywords (`subject`, `action`, `resource`, `environment`) and hard keywords like `permit`, `true`, `in`, etc. -- require the caret escape to be used as variable names (e.g., `var ^subject = ...`).

#### Caret Escape

A caret `^` before an identifier forces it to be treated as a plain identifier, even if it would otherwise match a keyword. This is needed for keywords that are not [reserved identifiers](#reserved-identifiers): `as`, `advice`, `deny`, `each`, `enforced`, `for`, `import`, `in`, `obligation`, `permit`, `policy`, `schema`, `set`, `transform`, `true`, `false`, `null`, `undefined`, `var`. For reserved identifiers, the caret is unnecessary in expression contexts but required when using subscription attribute keywords as variable names (see [Variable Names](#variable-names)).

### Strings

Whenever strings are expected, the SAPL document must contain any sequence of characters enclosed by double quotes `"`. Any quote character occurring in the string must be escaped by a preceding `\`, e.g., `"the name is \"John Doe\""`.

### Comments

Comments are used to store information in a SAPL document which is only intended for human readers and has no meaning for the PDP. Comments are simply ignored when the PDP evaluates a document.

SAPL supports single-line and multi-line comments. A single-line comment starts with `//` and ends at the end of the line, no matter which characters follow.

Sample Single-Line Comment

```
policy "test" // a policy for testing
```

Multi-line comments start with `/*` and end with `*/`. Everything in between is ignored.

Sample Multi-Line Comment

```
policy "test"
/* A policy for testing.
Remove before deployment! */
```

### JSON Data Types

SAPL is based on the **JavaScript Object Notation** or **JSON**, an [ECMA Standard](http://www.ecma-international.org/publications/files/ECMA-ST/ECMA-404.pdf) for the representation of structured data. Any value occurring within the SAPL language is a JSON data type, and any expression within a policy evaluates to a JSON data type. The types and their JSON notations are:

- Primitive Types 
  - **Number**: A signed decimal number, e.g., `-1.9`. There is no distinction between integer and floating-point numbers. In case an integer is expected (e.g., for a numeric index), the decimal number is rounded to an integer number.
  - **String**: A sequence of zero or more characters, written in double quotes, e.g., `"a string"`.
  - **Boolean**: Either `true` or `false`.
  - **null**: Marks an empty value, `null`.
- Structured Types 
  - **Object**: An unordered set of name/value pairs. The name is a string. The value must be one of the available data types. It can also be an object itself. The name/value pair is also called an attribute of the object. E.g.

    { "firstAttribute" : "first value", "secondAttribute" : 123 }
  - **Array**: An ordered sequence of zero or more values of any JSON data type. E.g.

    \[ "A value", 123, {"attribute" : "value"} \]

### Expression Types

SAPL knows **basic expressions** and **operator expressions** (created from other expressions using operators).

A **basic expression** is either a

- **Value Expression**: a value explicitly defined in the corresponding JSON notation (e.g., `"a value"`)
- **Identifier Expression**: any [identifier](#identifiers), typically the name of a variable or an authorization subscription attribute (`subject`, `resource`, `action`, or `environment`)
- **Function Expression**: a function call (e.g., `simple.get_minimum(resource.array)`)
- **Relative Expression**: `@` or `#`, which refer to context-dependent values (current element and its position)
- **Grouped Expression**: any expression enclosed in parentheses, e.g., `(1 + 1)`

Each of these basic expressions can contain one or more **selection steps** (e.g., `subject.name`, which is the identifier expression `subject` followed by the selection step `.name` selecting the value of the `name` attribute). Additionally, a basic expression can contain a **filter component** (`|- Filter`) which will be applied to the evaluation result. If the expression evaluates to an array, instead of applying a filter, each item can be transformed using a **subtemplate component** (`:: Subtemplate`).

**Operator expressions** can be constructed using prefix or infix **operators** (e.g., `1 + subject.age` or `! subject.isBlocked`). SAPL supports infix and prefix operators. They may be applied in connection with any expression. An operator expression within parentheses (e.g., `(1 + subject.age)`) is a basic expression again and thus may contain selection steps, filter, or subtemplate statements.

### Value Expressions

A basic value expression is the simplest type. The value is denoted in the corresponding JSON format.

`true`, `false`, `null`, and `undefined` are value expressions as well as `"a string"` or any number (like `6` or `100.51`).

For denoting objects, the keys can be strings or bare identifiers, and the values can be any expression, e.g.

```sapl
{
    "id" : (3+5),
    "name" : functions.generate_name()
}
```

For arrays, the items can be any expression, e.g.

```sapl
[
    (3+5),
    subject.name
]
```

### Identifier Expressions

A basic identifier expression consists of any [identifier](#identifiers), typically the name of a variable or an authorization subscription attribute (i.e., `subject`, `resource`, `action`, or `environment`). Any [reserved identifier](#reserved-identifiers) is also valid here.

It evaluates to the variable’s or the subscription attribute’s value.

### Function Expressions

A basic function expression consists of a function name and any number of arguments between parentheses which are separated by commas. The arguments must be expressions, e.g.

```sapl
library.a_function(subject.name, (environment.day_of_week + 1))
```

Each function is available under its fully qualified name. The fully qualified name starts with the library name, consisting of one or more identifiers separated by periods `.` (e.g., `sapl.functions.simple`). The library name is followed by a period `.` and an identifier for the function name (e.g., `sapl.functions.simple.append`). Which function libraries are available depends on the configuration of the PDP.

[Imports](../2_8_Imports/) at the beginning of a SAPL document can be used to make functions available under shorter names. A basic import makes a function available under its simple name (e.g., `import sapl.functions.simple.append` makes `append` available). An aliased import provides an alternative name (e.g., `import sapl.functions.simple.append as add` makes it available as `add`).

If there are no arguments passed to the function, empty parentheses have to be denoted (e.g., `random_number()`).

When evaluating a function expression, the expressions representing the function call arguments are evaluated first. Afterward, the results are passed to the function as arguments. The expression evaluates to the function's return value.

For the conceptual model of functions (purity, determinism, target safety) and how they differ from attribute finders, see [Functions and Attribute Finders](../2_7_FunctionsAndAttributes/).

### Relative Expressions

SAPL provides two relative expressions: `@` (relative value) and `#` (relative location).

These can be used in contexts characterized by an implicit loop, where they dynamically evaluate based on the current iteration:

- **`@` (relative value)**: References the current element's value
- **`#` (relative location)**: References the current element's position - an index (number) for arrays, or a key (string) for objects

Assuming the variable `array` contains an array with multiple numbers, the expression `array[?(@ > 10)]` can be used to return any element greater than 10. In this context, `@` evaluates to the array item for which the condition is currently checked, and `#` evaluates to its index.

The contexts in which `@` and `#` can be used are:

- Expressions within a condition step (`@` evaluates to the array item or attribute value, `#` evaluates to the index or key)
- Subtemplate (`@` evaluates to the current element, `#` evaluates to its index or key)
- Arguments of a filter function if `each` is used (`@` evaluates to the array item to which the filter function is going to be applied)

## Operators

SAPL provides a collection of arithmetic, comparison, logical, string and filtering operators, which can be used to build expressions from other expressions.

### Arithmetic Operators

Assuming `exp1` and `exp2` are expressions evaluating to numbers, the following operators can be applied. All of them evaluate to number.

- `+exp1` (unary plus, no-op)
- `-exp1` (negation)
- `exp1 * exp2` (multiplication)
- `exp1 / exp2` (division)
- `exp1 % exp2` (modulo)
- `exp1 + exp2` (addition)
- `exp1 - exp2` (subtraction)

An expression can contain multiple arithmetic operators. The order in which they are evaluated can be specified using **parentheses**, e.g., `(1 + 2) * 3`.

In case multiple operators are used without parentheses (e.g., `4 + 3 * 2`), the **operator precedence** determines how the expression is evaluated. Operators with higher precedence are evaluated first. The following precedence is assigned to arithmetic operators:

- `+` (unary plus), `-` (negation): precedence **10** (highest)
- `*` (multiplication), `/` (division), `%` (modulo): precedence **9**
- `+` (addition), `-` (subtraction): precedence **8**

As `*` has a higher precedence than `+`, `4 + 3 * 2` would be evaluated as `4 + (3 * 2)`.

Except for the unary operators, multiple operators with the same precedence (e.g., `5 - 2 + 1`) are **left-associative**, i.e., `5 - 2 + 1` is evaluated like `(5 - 2) + 1`. Unary operators are non-associative, i.e., `--1` needs to be replaced by `-(-1)`.

### Comparison Operators

1. Number comparison

   Assuming `exp1` and `exp2` are expressions evaluating to numbers, the following operators can be applied. All of them evaluate to `true` or `false`.
   1. `exp1 < exp2` (`true` if `exp2` is greater than `exp1`)
   2. `exp1 > exp2` (`true` if `exp1` is greater than `exp2`)
   3. `exp1 <= exp2` (`true` if `exp2` is equal to or greater than `exp1`)
   4. `exp1 >= exp2` (`true` if `exp1` is equal to or greater than `exp2`)
2. Equals and Not Equals

   Assuming `exp1` and `exp2` are expressions, the equals and not-equals operators can be used to compare the results:

   `exp1 == exp2` evaluates to `true` if the results are equal.

   `exp1 != exp2` evaluates to `true` if the results are not equal.

3. Regular Expression

   Assuming `exp1` and `exp2` are expressions evaluating to strings, the regular expression match operator can be used:

   `exp1 =~ exp2`

   The expression evaluates to `true` if the result of evaluating `exp1` matches the pattern contained in the result of evaluating `exp2`. The pattern needs to be specified according to the [java.util.regex package](https://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html).
4. `in` (element of)

   Assuming `exp1` is an expression and `exp2` is an expression evaluating to an array, the `in` operator can be used:

   `exp1 in exp2`

   The expression evaluates to `true` if the array `exp2` evaluates to contains the result of evaluating `exp1`. Otherwise, the expression evaluates to `false`.
5. Precedence and Associativity

   Comparison operators (`<`, `>`, `<=`, `>=`, `in`) have precedence **7**. Equality operators (`==`, `!=`, `=~`) have precedence **6**. This is important for combining them with logical operators (see below).

   All comparison and equality operators are **non-associative**, i.e., an expression may not contain multiple comparison operators (like `3 < var < 5`). However, they can be combined with logical operators which have a different precedence (thus, the faulty example could be replaced by `3 < var && var < 5`).

### Logical Operators

Assuming `exp1` and `exp2` are expressions evaluating to `true` or `false`, the following operators can be applied. The new expression evaluates to `true` or `false`:

- `!exp1` (negation), precedence **10** (highest)
- `exp1 & exp2` (AND), precedence **5**
- `exp1 ^ exp2` (XOR), precedence **4**
- `exp1 | exp2` (OR), precedence **3**
- `exp1 && exp2` (AND), precedence **2**
- `exp1 || exp2` (OR), precedence **1** (lowest)

SAPL provides two forms of AND (`&`, `&&`) and two forms of OR (`|`, `||`). Both forms of each operator use the same [cost-stratified short-circuit evaluation](../2_10_EvaluationSemantics/#cost-stratified-short-circuit-evaluation). The **only current difference is precedence**: `&` and `|` bind tighter than `&&` and `||`. For example, `a && b | c` is parsed as `a && (b | c)`, not `(a && b) | c`.

This precedence difference allows policy authors to group conditions naturally without parentheses. For example:

```sapl
(subject.role == "doctor" | subject.role == "nurse") && resource.type == "patient_record"
```

can be written without parentheses as:

```sapl
subject.role == "doctor" | subject.role == "nurse" && resource.type == "patient_record"
```

because `|` binds tighter than `&&`.

The XOR operator (`^`) always evaluates both operands since both are needed to determine the result.

`&&`, `||`, `&`, `|`, and `^` are left-associative. `!` is non-associative, i.e., `!!true` must be replaced by `!(!true)`.

#### Why Two Forms Exist

The two AND/OR forms exist to support a future optimization for the streaming evaluation stratum. Currently, when an expression involves attribute finder subscriptions (the streaming stratum), the engine optimizes for **subscription and resource consumption**: it avoids subscribing to attribute finders whose values are not needed for the result. This is the behavior for both `&&`/`||` and `&`/`|`.

In a future version, `&`/`|` will select an alternative evaluation strategy for the streaming stratum that optimizes for **latency**: eagerly subscribing to all attribute sources in parallel and returning the result as soon as it can be determined, at the cost of maintaining more concurrent subscriptions. The `&&`/`||` operators will retain the current resource-optimized strategy.

This distinction does not affect the **semantics** of a policy. A policy using `&` produces the same authorization decisions as one using `&&` (given the same precedence grouping). The difference is purely in runtime behavior: how the engine manages subscriptions and how quickly it can deliver a result. Choosing between the two forms will never change whether access is granted or denied.

Until the latency-optimized strategy is implemented, choosing between the two forms is purely a matter of precedence preference.

#### Cost-Stratified Short-Circuit Evaluation

All AND and OR operators (`&`, `&&`, `|`, `||`) use **cost-stratified short-circuit evaluation**. The engine categorizes operands into three cost strata (constants, pure expressions, streaming expressions) and evaluates cheaper strata first, regardless of operand position in the source. If a cheaper operand short-circuits the result, more expensive operands are never evaluated and their subscriptions are never created.

For the full evaluation rules, examples, and implications for attribute finder subscriptions, see [Evaluation Semantics](../2_10_EvaluationSemantics/).

### String Concatenation

The operator `+` concatenates two strings, e.g., `"Hello" + " World!"` evaluates to `"Hello World!"`.

String concatenation is applied if the left operand is an expression evaluating to a string. If the right expression evaluates to a string as well, the two strings are concatenated. Otherwise, an error is thrown.

#### Selection Steps

SAPL provides an easy way of accessing attributes of an object (or items of an array). The **basic access** mechanism has a similar syntax to programming languages like JavaScript or Java (e.g., `object.attribute`, `user.address.street` or `array[10]`). Beyond that, SAPL offers **extended possibilities** for expressing more sophisticated queries against JSON structures (e.g., `persons[?(@.age >= 50)]`).

### Overview

The following table provides an overview of the different types of selection steps.

Given that the following object is stored in the variable `object`:

Structure of `object`

```json
{
    "key" : "value1",
    "array1" : [
        { "key" : "value2" },
        { "key" : "value3" }
    ],
    "array2" : [
        1, 2, 3, 4, 5
    ]
}
```

| Expression                                                  | Returned Value                                                                                                                         | Explanation                                                                                                                                     |
|-------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| `object.key`  <br>`object["key"]`                            | `"value1"`                                                                                                                             | **Key step** in dot notation and bracket notation                                                                                               |
| `object.array1[0]`                                          | `{ "key" : "value2" }`                                                                                                                 | **Index step**                                                                                                                                  |
| `object.array2[-1]`                                         | `5`                                                                                                                                    | **Index step** with negative value n returns the n-th last element                                                                              |
| `object.*`  <br>`object[*]`                                 | ["value1",<br>      [<br>        { "key" : "value2" },<br>        { "key" : "value3" }<br>      ],<br>      [ 1, 2, 3, 4, 5 ]<br>    ] | **Wildcard step** applied to an object, it returns an array with the value of each attribute - applied to an array, it returns the array itself |
| `object.array2[0:-2:2]`                                     | `[ 1, 3 ]`                                                                                                                             | **Array slicing step** starting from first to second last element with a step size of two                                                       |
| `object..key`  <br>`object..["key"]`                         | `[ "value1", "value2", "value3" ]`                                                                                                     | **Recursive descent step** looking for an attribute                                                                                             |
| `object..[0]`                                               | `[ { "key" : "value2" }, 1 ]`                                                                                                          | **Recursive descent step** looking for an array index                                                                                           |
| `object.array2[(3+1)]`                                      | `5`                                                                                                                                    | **Expression step** that evaluates to number (index) - can also evaluate to an attribute name                                                   |
| `object.array2[?(@>2)]`                                     | `[ 3, 4, 5 ]`                                                                                                                          | **Condition step** that evaluates to true/false, `@` references the current item, `#` its index/key - can also be applied to an object           |
| `object.array2[2,3]`                                        | `[ 3 , 4 ]`                                                                                                                            | **Union step** for more than one array index                                                                                                    |
| `object["key","array2"]`                                    | `[ "value1", [ 1, 2, 3, 4, 5 ] ]`                                                                                                      | **Union step** for more than one attribute                                                                                                      |

*Table 1. Selection Steps Overview*

### Basic Access

The basic access syntax is quite similar to accessing an object’s attributes in JavaScript or Java:

- **Attributes of an object** can be accessed by their key (**key step**) using the *dot notation* (`resource.key`) or the *bracket notation* (`resource["key"]`). Both expressions return the value of the specified attribute. For using the dot notation, the specified key must be an [identifier](#identifiers). Otherwise, the bracket notation with a string between square brackets is necessary, e.g., if the key contains whitespace characters (`resource["another key"]`).
- **Indices of an array** may be accessed by putting the index between square brackets (**index step**, `array[3]`). The index can be a negative number `-n`, which evaluates to the `n`\-th element from the end of the array, starting with -1 as the last element's index. `array[-2]` would return the second last element of the array `array`.

**Handling missing data:**

- **Missing keys:** When a key step accesses an attribute that doesn't exist in an object, the result is `undefined`. This allows policies to gracefully handle optional attributes using conditional logic.
- **Out-of-bounds indices:** When an index step accesses an array index that doesn't exist (either beyond the array length or more negative than the array allows), an error is returned. Unlike missing object keys, out-of-bounds array access is treated as a programming error since array lengths are typically known or should be checked beforehand.

Multiple selection steps can be **chained**. The steps are evaluated from left to right. Each step is applied to the result returned from the previous step.

{: .info }
**Example** <br /><br />The expression `object.array\[2\]` first selects the attribute with key array from the object `object` (first step). Then it returns the third element (index 2) of that array (second step). |


### Extended Possibilities

SAPL supports querying for specific parts of a JSON structure. Except for an **expression step**, all of these steps return an array since the number of elements found can vary. Even if only a single result is retrieved, the expression returns an array containing one item.

#### Expression Step `[(Expression)]`

An expression step returns the value of an attribute with a key or an array item with an index specified by an expression. `Expression` must evaluate to a string or a number. If `Expression` evaluates to a string, the selection can only be applied to an object. If `Expression` evaluates to a number, the selection can only be applied to an array. When an expression evaluates to a non-integer number (e.g., `3.7`), the value is truncated toward zero to produce an integer index (e.g., `3`).


> The expression step can be used to refer to custom variables (`object.array[(anIndex+2)]`) or apply custom functions (`object.array[(max_value(object.array))]`.


#### Wildcard Step `.*` or `[*]`

A wildcard step can be applied to an object or an array. When applied to an object, it returns an array containing all attribute values. As attributes of an object have no order, the sorting of the result is not defined. When applied to an array, the step just leaves the array untouched.


> Applied to an object
>```sapl
> {
>   "key1":"value1",
>   "key2":"value2"
> }
>```
> the selection step `.*` or `[*]` returns the following array: `["value1", "value2"]` (possibly with a different sorting of the items). Applied to an array `[1, 2, 3]`, the selection step `.*` **or** `[*]` returns the original array `[1, 2, 3]`.


#### Recursive Descent Step `..key`, `..["key"]`, `..[1]`, `..*` or `..[*]`

Looks for the specified key or array index in the current object or array and, recursively, in its children (i.e., the values of its attributes or its items). The recursive descent step can be applied to both an object and an array. It returns an array containing all attribute values or array items found. If the specified key is an asterisk (`..` **or** `[]`, wildcard), all attribute values and array items in the whole structure are returned.

As attributes of an object are not sorted, the order of items in the result array may vary.

{: .info }
**Depth limit:** To prevent runaway recursion on deeply nested structures, recursive descent is limited to a maximum depth of 500 levels. If this limit is exceeded, an error is returned.


> Applied to an `object`
>
>```sapl
> {
>   "key" : "value1",
>   "anotherkey" : {
>       "key" : "value2"
>   }
> }
>```
>
> The selection step `object..key` returns the following array: `["value1", "value2"]` (any attribute value with key `key`, the items may be in a different order).
>
> The wildcard selection step `object..` **or** `object..[]` returns `["value1", {"key":"value2"}, "value2"]` (recursively each attribute value and array item in the whole structure `object`, the sorting may be different).


#### Condition `[?(Condition)]`

Condition steps return an array containing all attribute values or array items for which `Condition` evaluates to `true`. It can be applied to both an object (then it checks each attribute value) and an array (then it checks each item). `Condition` must be an expression in which [relative expressions](#relative-expressions) `@` and `#` can be used. `@` evaluates to the current attribute value or array item, and `#` to its key or index. Both can be followed by further selection steps.

As attributes have no order, the sorting of the result array of a condition step applied to an object is not specified.


> Applied to the array `[1, 2, 3, 4, 5]`, the selection step `[?(@ > 2)]` returns the array `[3, 4, 5]` (containing all values that are greater than 2).


#### Array Slicing `[Start:Stop:Step]`

The slice contains the items with indices between `Start` and `Stop`, with `Start` being inclusive and `Stop` being exclusive. `Step` describes the distance between the elements to be included in the slice, i.e., with a `Step` of 2, only each second element would be included (with `Start` as the first element's index). All parts except the first colon are optional. `Step` defaults to 1.

**Default values:** When `Step` is positive (or omitted), `Start` defaults to `0` and `Stop` defaults to the array length. When `Step` is negative, `Start` defaults to the last index and `Stop` defaults to before the first element—allowing the slice to traverse the array in reverse. A `Step` of `0` results in an error.

**Negative indices:** Both `Start` and `Stop` support negative indices, which count from the end of the array. For example, `-1` refers to the last element, `-2` to the second-to-last, and so on. These indices are converted to their positive equivalents before slicing.

**How slicing works:** The `Step` value determines both the direction and stride of iteration:

- **Positive step:** Iteration proceeds forward from `Start` toward `Stop`, selecting every `Step`-th element
- **Negative step:** Iteration proceeds backward from `Start` toward `Stop`, selecting every `|Step|`-th element

If the direction of `Step` is inconsistent with the range (e.g., trying to go forward when `Start > Stop`, or backward when `Start < Stop`), the result is an empty array.

{: .warning }
**Changed in SAPL 4.0:** In SAPL 3.x, negative step values did not reverse iteration direction. Starting with SAPL 4.0, a negative step now iterates backward through the array. For example, `[::-1]` previously returned elements in their original order, but now returns the array reversed. Review any policies using negative step values when upgrading.

**Examples:**

> Applied to `[0, 1, 2, 3, 4, 5, 6, 7, 8, 9]`:
> - `[1:4]` returns `[1, 2, 3]` (elements from index 1 up to, but not including, index 4)
> - `[::3]` returns `[0, 3, 6, 9]` (every third element, starting from the beginning)
> - `[::-1]` returns `[9, 8, 7, 6, 5, 4, 3, 2, 1, 0]` (all elements in reverse order)
> - `[::-3]` returns `[9, 6, 3, 0]` (every third element, in reverse)
> - `[5:2:-1]` returns `[5, 4, 3]` (from index 5 down to, but not including, index 2)
> - `[1:5:-1]` returns `[]` (empty—cannot go backward from 1 to 5)
> - `[-3:]` returns `[7, 8, 9]` (the last three elements)
> - `[:-3]` returns `[0, 1, 2, 3, 4, 5, 6]` (all but the last three elements)


#### Index Union `[index1, index2, …​]`

By using the bracket notation, a set of multiple array indices (numbers) can be denoted separated by commas. This returns an array containing the items of the original array if the item’s index is contained in the specified indices. Since a **set** of indices is specified, the indices' order is ignored, and duplicate elements are removed. The result array contains the specified elements in their original order. Indices that do not exist in the original array are ignored.


> Both `[3, 2, 2]` and `[2, 3]` return the same result.


#### Attribute Union `["attribute1", "attribute2", …​]`

By using the bracket notation, a set of multiple attribute keys (strings) can be denoted separated by commas. This returns an array containing the values of the denoted attributes. Since a **set** of attribute keys is specified, the keys' order is ignored, and duplicate elements are removed. As attributes have no order, the sorting of the resulting array is not specified. Attributes that do not exist are ignored.

#### Attribute Selection on Array

Although arrays do not have attributes (they have items), a key step can be applied to an array (e.g., `array.value`). This will loop through each item of the array and look for the specified attribute in this item. An array containing all values of the attributes found is returned. In other words, the selection step is not applied to the result of the previous step (the array) but to each item of the result, and the (sub-)results are concatenated. In case an array item is no object or does not contain the specified attribute, it is skipped.


> Applied to an object
>
>```sapl
> {
>   "array":[
>       {"key":"value1"},
>       {"key":"value2"}
>   ]
> }
>```
>
> `array.key` returns the following array: `["value1", "value2"]` (the value of the `key` attribute of each item of `array`).


#### Attribute Finder `.<finder.name>`

In SAPL, it is possible to receive attributes that are not contained in the authorization subscription. Those attributes can be provided by external PIPs and obtained through attribute finders.

The standard attributes in SAPL are intended to gather more information in regard to a given JSON value, i.e., the subject, action, resource, environment objects in the subscription, or any other JSON value.

A standard attribute finder is called via the selection step `.<finder.name>`. Where `finder.name` either is a fully qualified attribute finder name or can be a shorter name if imports are used (the finder name or the library alias followed by a period `.` and the finder name). Any number of selection steps can be appended after such a step.

An attribute accessed this way is treated as a subscription. I.e., the PDP will subscribe to the data source, and whenever a new value is returned, the policy is reevaluated, and a new decision is calculated.

The attribute finder receives the result of the previous selection as an argument and returns a JSON value. Optionally, an attribute finder may be supplied with a list of parameters: `.<finder.name(p1,p2,...)>`. Additionally, an attribute finder may accept options in brackets: `.<finder.name[optionsExpression]>`. The options expression is evaluated and passed to the attribute finder alongside the regular parameters.

Attribute finders may be nested: `subject.<finder.name2>.<finder.name(p1,action.<finder.name3>,…​)>`. Here, whenever the attributes with `name2` and `name3` all have an initial result, and whenever one of the results change, the attribute with name `name` is re-subscribed with the new input parameters.

An environment attribute finder is an attribute finder intended for accessing information possibly independent of subscription data, e.g., current time or an organization-wide emergency level. These environment attributes are not to be confused with the data which is contained in the environment object in the subscription. The data contained there is environment data provided by the PEP from its application context at subscription time and may not be accessible from the PDP otherwise. Environment attributes do not require a left-hand input and can be accessed without a leading value, variable, or sequence of selection steps: `<organization.emergencyLevel>` may refer to a stream indicating an emergency level in an organization. Analogous to standard attributes, these attributes may be parameterized and nested.

All attribute finders may be followed by arbitrary selection steps.

In some scenarios, it may not be the right thing to subscribe to attributes, but to just retrieve the data once on subscription time. For this, SAPL offers the head operator for both standard and environment attributes. Prepending the pipe symbol `|` in front of an attribute finder step will only return the first value returned by the attribute finder. E.g.: `subject.id.|<geo.location>`. However, such an attribute may still return a stream if used with nested attributes which do not employ the head operator.


> Assuming a doctor should only be allowed to access patient data from patients on her unit. The following expression retrieves the unit (attribute finder `pip.hospital_units.by_patientid`) by the requested patient id (`action.patientid`) and selects the id of the supervising doctor (`.doctorid`):
>
> action.patientid.<pip.hospital_units.by_patientid>.doctorid


For the conceptual model of attribute finders, streaming, and the attribute broker, see [Functions and Attribute Finders](../2_7_FunctionsAndAttributes/).

## Filtering

SAPL provides syntax elements filtering values by applying **filters**, and that can potentially modify the value.

Filters can only be applied to basic expressions (remember that an expression in parentheses is a basic expression). Filtering is denoted by the `|-` operator after the expression. Which **filter function** is applied in what way can be defined by a **simple filtering component** or by an **extended filtering component**, which consists of several filter statements.

### Filter Functions

SAPL provides three **built-in filter functions**:

remove

Removes a whole attribute (key and value pair) of an object or an item of an array without leaving a replacement.

filter.replace(replacement)

Replaces an attribute or an element by the result of evaluating the expression `replacement`.

filter.blacken(disclose\\\_left=0,disclose\\\_right=0,replacement="X")

Replaces each char of an attribute or item (which must be a string) by `replacement`, leaving `show\_left` chars from the beginning and `show\_right` chars from the end unchanged. By default, no chars are visible, and each char is replaced by `X`.


> `filter.blacken` could be used to reveal only the first digit of the credit card number and replace the other digits by `X`.



> `filter.replace` and `filter.blacken` are part of the library `filter`. Importing this library through `import filter` makes the functions available under their simple names.


Example:

We take the following object:

Object Structure

```sapl
{
    "value" : "aValue",
    "id" : 5
}
```

If value is removed, the resulting object is ```{ "id" : 5 }```.

If instead ```filter.replace``` is applied to value with the Expression null, the resulting object is ```{ "value" : null, "id" : 5 }```.

If the function ```filter.blacken``` is applied to value without specifying any arguments, the result would be ```{ "value" : "XXXXXX", "id" : 5 }```.

### Simple Filtering

A simple filter component applies a **filter function** to the preceding value. The syntax is:

```
BasicExpression |- Function
```

`BasicExpression` is evaluated to a value, the function is applied to this value, and the result is returned. If no other arguments are passed to the function, the empty parentheses `()` after the function name can be omitted.

In case `BasicExpression` evaluates to an array, the whole array is passed to the filter function. The **keyword** `each` before `Function` can be used to apply the function to each array item instead:

```
Expression |- each Function
```

Example:

Let us assume our resource contains an array of credit card numbers:

```sapl
{
    "numbers": [
        "1234123412341234",
        "2345234523452345",
        "3456345634563456"
    ]
}
```

The function ```blacken(1)``` without any additional parameters takes a string and replaces everything by ```X``` except the first character. We can receive the blackened numbers through the basic expression ```resource.numbers |- each blacken(1)```:

```sapl
[
    "1XXXXXXXXXXXXXXX",
    "2XXXXXXXXXXXXXXX",
    "3XXXXXXXXXXXXXXX"
]
```
Without the keyword each, the function blacken would be applied to the array itself, resulting in an error, as stated above, blacken can only be applied to a String.

### Extended Filtering

Extended filtering can be used to state more precisely how a value should be altered.

E.g., the expression

```sapl
resource |- { @.credit_card : blacken }
```

would return the original resource except for the value of the attribute `credit_card` being blackened.

Extended filtering components consist of one or more **filter statements**. Each filter statement has a target expression and specifies a filter function that shall be applied to the attribute value (or to each of its items if the keyword `each` is used). The basic syntax is:

```sapl
Expression |- { 
				FilterStatement, 
				FilterStatement, 
				... 
}
```

The syntax of a filter statement is:

```sapl
each TargetRelativeExpression : Function
```

`each` is an optional keyword. If used, the `TargetRelativeExpression` must evaluate to an array. In this case, `Function` is applied to each item of that array.

`TargetRelativeExpression` contains a basic relative expression starting with `@`. The character `@` references the result of the evaluation of `Expression`, so attributes of the value to filter can be accessed easily. Bear in mind that attribute finder steps are not allowed at this place. The value of the attribute selected by the target expression is replaced by the result of the filter function.

The filter statements are applied successively from top to bottom.

> Some filter functions can be applied to both arrays and other types (e.g., `remove`). Yet, there are selection steps resulting in a "helper array" that cannot be modified. If, for instance, `.*` is applied to the object `{"key1" : "value1", "key2" : "value2"}`, the result would be `["value1", "value2"]`. It is not possible to apply a filter function directly to this array because changing the array itself would not have any effect. The array has been constructed merely to hold multiple values for further processing. In this case, the policy would **have to** use the keyword `each` and apply the function to each item. The attempt to alter a helper array will result in an error.


### Custom Filter Functions

Any function available in SAPL can be used in a filter statement. Hence, it is easy to add custom filter functions.

When used in a filter statement, the value to filter is passed to the function as its first argument. Consequently, the arguments specified in the function call are passed as second, third, etc., arguments.


> Assuming a filter function `roundto` should round a value to the closest multiple of a given number, e.g., `207 |- roundto(100)` should return `200`. In its definition, the function needs two formal parameters. The first parameter is reserved for the original value and the second one for the number to round to.


## Subtemplate

A subtemplate applies a template expression to a value, enabling transformation of data structures. The subtemplate is denoted after a double colon using the `::` operator. A subtemplate component is an optional part of a basic expression.

E.g., the basic expression:

```
resource.patients :: { "name" : @.name }
```

This expression would return the `patients` array from the resource but with each item containing only one attribute `name`.

The subtemplate syntax is:

```
Value :: Expression
```

The `Expression` represents the replacement template. Within this expression, the relative expressions `@` and `#` can be used:

- **`@` (relative value)**: References the current element being transformed
- **`#` (relative location)**: References the current position - index for arrays, key for objects

**Subtemplate behavior:**

- **Arrays:** The template is mapped over each array element. For each item, `Expression` is evaluated with `@` referencing that item and `#` referencing its index (0, 1, 2, ...). The result is an array of the same length with transformed elements. Empty arrays remain empty.

- **Objects:** The template is mapped over each object value. For each entry, `Expression` is evaluated with `@` referencing the value and `#` referencing the key (as a string). The result is an **array** containing the transformed values. Empty objects return an empty array.

- **Scalar values:** The template is applied directly to the value, with `@` referencing that value and `#` set to `0`. The result is the evaluation of the template expression.

- **Error and undefined values:** Error and undefined values are propagated without applying the template.

**Example: Array transformation**

Given the variable `array` contains the following array:

```json
[
    { "id" : 1 },
    { "id" : 2 }
]
```

The basic expression

```sapl
array :: {
    "aKey" : "aValue",
    "identifier" : @.id
}
```

would evaluate to:

```json
[
    {"aKey" : "aValue", "identifier" : 1 },
    {"aKey" : "aValue", "identifier" : 2 }
]
```

**Example: Using `#` for index access**

The `#` symbol provides access to the current index (for arrays) or key (for objects):

```sapl
[10, 20, 30] :: #
```

evaluates to `[0, 1, 2]` (the indices).

```sapl
[10, 20, 30] :: (@ + #)
```

evaluates to `[10, 21, 32]` (each value plus its index).

{: .warning }
**Operator precedence:** The `::` operator binds tighter than arithmetic and comparison operators. This means `[1, 2, 3] :: @ * 2` parses as `([1, 2, 3] :: @) * 2`, not `[1, 2, 3] :: (@ * 2)`. Always use parentheses around complex template expressions: `[1, 2, 3] :: (@ * 2)`, `array :: (@ > 5)`, etc.

**Example: Object transformation**

When a subtemplate is applied to an object, it iterates over the object's values and returns an **array**:

Given the variable `scores` contains:

```json
{
    "alice" : 95,
    "bob" : 87,
    "carol" : 92
}
```

The expression `scores :: @` returns `[95, 87, 92]` (an array of values).

The expression `scores :: #` returns `["alice", "bob", "carol"]` (an array of keys).

The expression:

```sapl
scores :: { "player" : #, "score" : @ }
```

returns an array of objects:

```json
[
    { "player" : "alice", "score" : 95 },
    { "player" : "bob", "score" : 87 },
    { "player" : "carol", "score" : 92 }
]
```

**Example: Scalar transformation**

Given the variable `user` contains the following object:

```json
{
    "id" : 123,
    "name" : "Alice",
    "email" : "alice@example.com"
}
```

The basic expression

```sapl
user :: {
    "userId" : @.id,
    "displayName" : @.name
}
```

would evaluate to:

```json
{
    "userId" : 123,
    "displayName" : "Alice"
}
```

Note: In this case, the entire `user` object is treated as a scalar (not iterated), so `@` references the whole object and `#` equals `0`.
