---
layout: default
title: Custom Function Libraries
#permalink: /reference/Custom-Function-Libraries/
parent: Functions
grand_parent: SAPL Reference
nav_order: 150
---

## Custom Function Libraries

For a more in-depth look at the process of creating a custom function library, please refer to the demo project. It provides a walkthrough of the entire process and contains extensive examples: <https://github.com/heutelbeck/sapl-demos/tree/master/sapl-demo-extension> .

The standard functions can be extended by custom functions. Function libraries available in SAPL documents are collected in the PDP’s function context. The embedded PDP provides an `AnnotationFunctionContext` where Java classes with annotations can be provided as function libraries:

SAPL functions must not perform any IO operations. Functions are to be used as "immediate" data transformation functions.

- To be recognized as a function library, a class must be annotated with `@FunctionLibrary`. The optional annotation attribute `name` contains the library’s name as it will be available in SAPL policies. The attribute value must be a string consisting of one or more identifiers separated by periods. If the attribute is missing, the name of the Java class is used. The optional annotation attribute `description` contains a string describing the library for documentation purposes.

  ```java
  @FunctionLibrary(name = "sample.functions", description = "a sample library")
  public class SampleFunctionLibrary {
      ...
  }
  ```
- The annotation `@Function` identifies a function in the library. An optional annotation attribute `name` can contain a function name. The attribute is a string containing an identifier. By default, the name of the Java function will be used. The annotation attribute `docs` can contain a string describing the function.

  ```java
  @Function(docs = "returns the length")
  public static Val length(@Text Val parameter) {
      ...
  }
  ```

  Each parameter can be annotated with any number of `@Array`, `@Bool`, `@Int`, `@JsonObject`, `@Long`, `@Number`, and `@Text`. The annotations describe which types are allowed for the parameter (in the case of multiple annotations, each of these types is allowed).
