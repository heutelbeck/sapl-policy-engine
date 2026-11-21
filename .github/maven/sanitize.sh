#!/usr/bin/env bash
# Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail

repository="${1:?Maven repository path is required.}"

# Locally built reactor artifacts and failed downloads never belong in the base.
if [[ -d "$repository/io/sapl" ]]; then
    find "$repository/io/sapl" -type f -delete
    find "$repository/io/sapl" -depth -type d -empty -delete
fi
find "$repository" -type f \( -name '*.lastUpdated' -o -name '*.part' \
    -o -name '*.part.*' -o -name '*.lock' -o -name '*.tmp' \
    -o -name 'resolver-status.properties' \) -delete
