#
# Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
#
# SPDX-License-Identifier: Apache-2.0
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# OPA RBAC benchmark policy. Uses input.subject, input.resource, input.action
# so the same policy works with both opa bench (--input flag) and
# opa run --server (HTTP POST body).
#
# Expected input: {"subject":"bob","resource":"foo123","action":"write"}
# Expected result: allow = false (bob has role test, test can only read)

package rbac

import rego.v1

bindings := [
    {
        "user": "alice",
        "roles": ["dev", "test"],
    },
    {
        "user": "bob",
        "roles": ["test"],
    },
]

roles := [
    {
        "name": "dev",
        "permissions": [
            {"resource": "foo123", "action": "write"},
            {"resource": "foo123", "action": "read"},
        ],
    },
    {
        "name": "test",
        "permissions": [{"resource": "foo123", "action": "read"}],
    },
]

default allow := false

allow if {
    some role_name
    user_has_role[role_name]
    role_has_permission[role_name]
}

user_has_role contains role_name if {
    binding := bindings[_]
    binding.user == input.subject
    role_name := binding.roles[_]
}

role_has_permission contains role_name if {
    role := roles[_]
    role_name := role.name
    perm := role.permissions[_]
    perm.resource == input.resource
    perm.action == input.action
}
