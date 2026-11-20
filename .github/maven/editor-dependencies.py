#!/usr/bin/env python3
# Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
# SPDX-License-Identifier: Apache-2.0
import json
import sys
from html import escape
from pathlib import Path

FIELDS = ("groupId", "artifactId", "version", "type", "classifier")


def external_dependencies(node):
    dependencies = set()
    group = node["groupId"]
    if group != "io.sapl" and not group.startswith("io.sapl."):
        dependencies.add(tuple(node.get(field, "") for field in FIELDS))
    for child in node.get("children", []):
        dependencies.update(external_dependencies(child))
    return dependencies


tree = json.loads(Path(sys.argv[1]).read_text())
lines = [
    '<project xmlns="http://maven.apache.org/POM/4.0.0">',
    "<modelVersion>4.0.0</modelVersion>",
    "<groupId>io.sapl.ci</groupId><artifactId>editor-dependencies</artifactId>",
    "<version>1.0.0</version><packaging>pom</packaging><dependencies>",
]
for dependency in sorted(external_dependencies(tree)):
    lines.append("<dependency>")
    for field, value in zip(FIELDS, dependency):
        if value:
            lines.append(f"<{field}>{escape(value)}</{field}>")
    # The collected graph already contains the consumer's complete mediation and exclusions.
    lines.append("<exclusions><exclusion><groupId>*</groupId><artifactId>*</artifactId></exclusion></exclusions>")
    lines.append("</dependency>")
lines.append("</dependencies></project>")
output = Path(sys.argv[2])
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text("\n".join(lines) + "\n")
