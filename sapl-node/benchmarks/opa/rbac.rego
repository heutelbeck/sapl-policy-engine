# OPA RBAC benchmark policy - exact replica of the OPA docs example.
# Source: https://www.openpolicyagent.org/docs/policy-performance

package rbac

import rego.v1

inp := {
    "subject": "bob",
    "resource": "foo123",
    "action": "write",
}

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
    binding.user == inp.subject
    role_name := binding.roles[_]
}

role_has_permission contains role_name if {
    role := roles[_]
    role_name := role.name
    perm := role.permissions[_]
    perm.resource == inp.resource
    perm.action == inp.action
}
