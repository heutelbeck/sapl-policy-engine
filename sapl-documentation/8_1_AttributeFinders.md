---
layout: default
title: Attribute Finders
##permalink: /reference/Attribute-Finders/
has_children: true
parent: SAPL Reference
nav_order: 8
has_toc: false
---

## Attribute Finders

Attribute finders are used to receive attributes that are not included in the authorization subscription context from external PIPs. Just like in `subject.age`, the selection step `.age` selects the attribute `age`s value, `subject.<user.age>` could be used to fetch an `age` attribute which is not included in the `subject` but can be obtained from a PIP named `user`.

Attribute finders are organized in libraries as well and follow the same naming conventions as functions, including the use of imports. An attribute finder library constitutes a PIP (e.g., `user`) and can contain any number of attributes (e.g., `age`). They are called by a selection step applied to any value, e.g., `subject.<user.age>`. The attribute finder step receives the previous selection result (in the example: `subject`) and returns the requested attribute.

The concept of attribute finders can be used in a flexible manner: There may be finders that take an object (like in the example above, `subject.<user.age>`) as well as attribute finders which expect a primitive value (e.g., `subject.id.<user.age>` with `id` being a number). In addition, attribute finders may also return an object which can be traversed in subsequent selection steps (e.g., `subject.<user.profile>.age`). It is even possible to join multiple attribute finder steps in one expression (e.g., `subject.<user.profile>.supervisor.<user.profile>.age`).

Optionally, an attribute finder may be supplied with a list of parameters: `x.<finder.name(p1,p2,…​)>`. Also, here nesting is possible. Thus `x.<finder.name(p1.<finder.name2>,p2,…​)>` is a working construct.

Furthermore, attribute finders may be used without any leading value `<finder.name(p1,p2,…​)>`. These are called environment attributes.

The way to read a statement with an attribute finder is as follows. For `subject.<groups.membership("studygroup")>` one would say "get the attribute `group.membership` with parameter `"studygroup"` of the subject".

Attribute finders often receive information from external data sources such as files, databases, or HTTP requests which may take a certain amount of time. Therefore, they must not be used in a target expression. Attribute finders can access environment variables.