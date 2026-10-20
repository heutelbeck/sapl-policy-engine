#!/usr/bin/env bash
#
# Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
#
# SPDX-License-Identifier: Apache-2.0
#
# Smoke test for a built SAPL Node. Exercises every CLI command plus a server
# and client round trip, so a broken build is caught before any release asset
# is published. This is the single source of truth for binary level testing.
# The node under test is passed as the launch command, so the same script gates
# the native binary in CI and validates the JVM build locally with no second
# file to drift out of sync.
#
# Usage:
#   .github/scripts/native-node-smoke-test.sh ./sapl-node/target/sapl
#   .github/scripts/native-node-smoke-test.sh java -cp "<classpath>" io.sapl.node.SaplNodeApplication
#
set -u

if [[ "$#" -gt 0 ]]; then SAPL=("$@"); else SAPL=("sapl-node/target/sapl"); fi
PORT="${SMOKE_PORT:-8099}"
WORK="$(mktemp -d)"
POLICIES="$WORK/policies"
SERVER_PID=""
PASS=0
FAIL=0
PERMIT_DECISION='"decision":"PERMIT"'

cleanup() {
    if [[ -n "$SERVER_PID" ]]; then
        kill "$SERVER_PID" 2>/dev/null || true
        wait "$SERVER_PID" 2>/dev/null || true
    fi
    rm -rf "$WORK"
    return
}
trap cleanup EXIT

pass() {
    local description="$1"
    PASS=$((PASS + 1))
    printf 'PASS  %s\n' "$description"
    return
}

fail() {
    local description="$1" detail="${2:-}"
    FAIL=$((FAIL + 1))
    printf 'FAIL  %s\n' "$description"
    [[ -n "$detail" ]] && printf '      %s\n' "$detail"
    return
}

# expect_exit <description> <expected-code> <command...>
expect_exit() {
    local description="$1" expected="$2"
    shift 2
    "$@" >/dev/null 2>&1
    local actual=$?
    if [[ "$actual" = "$expected" ]]; then
        pass "$description (exit $actual)"
    else
        fail "$description" "expected exit $expected, got $actual"
    fi
    return
}

# expect_output <description> <needle> <command...>
expect_output() {
    local description="$1" needle="$2"
    shift 2
    local output
    output="$("$@" 2>/dev/null)"
    case "$output" in
    *"$needle"*) pass "$description" ;;
    *) fail "$description" "output did not contain '$needle': ${output:-<empty>}" ;;
    esac
    return
}

# expect_file <description> <path>
expect_file() {
    local description="$1" path="$2"
    if [[ -s "$path" ]]; then pass "$description"; else fail "$description" "missing or empty file: $path"; fi
    return
}

printf 'SAPL smoke test\n  node: %s\n  workdir: %s\n\n' "${SAPL[*]}" "$WORK"

mkdir -p "$POLICIES"
# indexing is forced to SMTDD so the server round trip below exercises the
# structural-equality index path. That path reads operator record components
# reflectively and fails in a native image unless they are registered, which is
# the regression guard for that class of native-image-only failure.
cat >"$POLICIES/pdp.json" <<'JSON'
{
  "algorithm": { "votingMode": "PRIORITY_DENY", "defaultDecision": "ABSTAIN", "errorHandling": "PROPAGATE" },
  "compilerFlags": { "indexing": "SMTDD" }
}
JSON
cat >"$POLICIES/permit-use.sapl" <<'SAPL_POLICY'
policy "permit use"
permit subject == "housemd" & action == "use" & resource == "MRT";
SAPL_POLICY
cat >"$POLICIES/deny-delete.sapl" <<'SAPL_POLICY'
policy "deny delete"
deny subject == "housemd" & action == "delete" & resource == "MRT";
SAPL_POLICY
# Several policies sharing operands with equality on distinct constants. This is
# what makes the SMTDD index group predicates and compare operators structurally,
# the path that broke in the native image. They do not match the housemd/MRT
# requests above, so the existing assertions are unaffected.
for roleIndex in 1 2 3 4 5 6; do
    cat >"$POLICIES/role-$roleIndex.sapl" <<SAPL_POLICY
policy "role $roleIndex"
permit
    subject.role == "role$roleIndex";
    resource.type == "type$roleIndex";
    action == "read";
SAPL_POLICY
done
cat >"$POLICIES/smoke.sapltest" <<'SAPL_TEST'
requirement "smoke" {
    scenario "house can use mrt"
        when "housemd" attempts "use" on "MRT"
        expect permit;
}
SAPL_TEST

S='"housemd"'
USE='"use"'
DELETE='"delete"'
MRT='"MRT"'

# Startup and discovery
expect_exit   "--version"                0 "${SAPL[@]}" --version
expect_output "--version reports node"   "SAPL Node" "${SAPL[@]}" --version
expect_exit   "--help"                   0 "${SAPL[@]}" --help

# One-shot evaluation against a directory
expect_output "decide-once permit"       "$PERMIT_DECISION" "${SAPL[@]}" decide-once --dir "$POLICIES" -s "$S" -a "$USE" -r "$MRT"
expect_exit   "check permit"             0 "${SAPL[@]}" check --dir "$POLICIES" -s "$S" -a "$USE" -r "$MRT"
expect_exit   "check deny"               2 "${SAPL[@]}" check --dir "$POLICIES" -s "$S" -a "$DELETE" -r "$MRT"
expect_exit   "check not-applicable"     3 "${SAPL[@]}" check --dir "$POLICIES" -s '"nobody"' -a "$USE" -r "$MRT"

# Streaming evaluation, bounded so it cannot hang the run
streamLine="$(timeout 15 "${SAPL[@]}" decide --dir "$POLICIES" -s "$S" -a "$USE" -r "$MRT" 2>/dev/null | head -1)"
case "$streamLine" in
*"$PERMIT_DECISION"*) pass "decide stream first line permit" ;;
*) fail "decide stream first line permit" "got: ${streamLine:-<empty>}" ;;
esac

# Policy tests and coverage
expect_exit "test passes" 0 "${SAPL[@]}" test --dir "$POLICIES" --output "$WORK/coverage"

# Bundle lifecycle: keygen, create, sign, verify, inspect
expect_exit "bundle keygen" 0 "${SAPL[@]}" bundle keygen -o "$WORK/key"
expect_file "bundle keygen private key" "$WORK/key.pem"
expect_file "bundle keygen public key" "$WORK/key.pub"
expect_exit "bundle create" 0 "${SAPL[@]}" bundle create -i "$POLICIES" -o "$WORK/smoke.saplbundle"
expect_file "bundle file created" "$WORK/smoke.saplbundle"
expect_exit "bundle sign" 0 "${SAPL[@]}" bundle sign -b "$WORK/smoke.saplbundle" -k "$WORK/key.pem"
expect_exit "bundle verify" 0 "${SAPL[@]}" bundle verify -b "$WORK/smoke.saplbundle" -k "$WORK/key.pub"
expect_exit "bundle inspect" 0 "${SAPL[@]}" bundle inspect -b "$WORK/smoke.saplbundle"
expect_output "decide-once via signed bundle" "$PERMIT_DECISION" "${SAPL[@]}" decide-once --bundle "$WORK/smoke.saplbundle" --public-key "$WORK/key.pub" -s "$S" -a "$USE" -r "$MRT"

# Credential generation
expect_output "generate basic" "Password:" "${SAPL[@]}" generate basic
expect_output "generate apikey" "sapl_" "${SAPL[@]}" generate apikey

# Performance tools are wired (no full run in a smoke test)
expect_exit "benchmark wired" 0 "${SAPL[@]}" benchmark --help
expect_exit "loadtest wired" 0 "${SAPL[@]}" loadtest --help

# Server and client round trip
printf '\nStarting server on port %s\n' "$PORT"
"${SAPL[@]}" server --no-auth --server.port="$PORT" \
    --io.sapl.pdp.embedded.pdp-config-type=DIRECTORY \
    --io.sapl.pdp.embedded.config-path="$POLICIES" \
    --io.sapl.pdp.embedded.policies-path="$POLICIES" >"$WORK/server.log" 2>&1 &
SERVER_PID=$!

ready=0
for _ in $(seq 1 60); do
    if curl -fsS "http://localhost:$PORT/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; then
        ready=1
        break
    fi
    kill -0 "$SERVER_PID" 2>/dev/null || break
    sleep 1
done

if [[ "$ready" = 1 ]]; then
    pass "server reaches health UP"
    expect_output "client decide-once over HTTP" "$PERMIT_DECISION" \
        "${SAPL[@]}" decide-once --remote --url "http://localhost:$PORT" -s "$S" -a "$USE" -r "$MRT"
else
    fail "server reaches health UP" "see server log below"
    tail -n 20 "$WORK/server.log"
fi

# Graceful shutdown so the bean-destroy path runs, then assert the whole server
# lifecycle (startup and shutdown) is free of native reflection errors. In a
# native image a missing reflection registration only surfaces at runtime, so
# this is the gate against that class of regression.
reflectionPattern='MissingReflectionRegistrationError|Cannot reflectively|UnsupportedFeatureError|reachability-metadata'
if [[ -n "$SERVER_PID" ]]; then
    kill -TERM "$SERVER_PID" 2>/dev/null || true
    wait "$SERVER_PID" 2>/dev/null || true
    SERVER_PID=""
fi
if grep -qE "$reflectionPattern" "$WORK/server.log"; then
    fail "server lifecycle free of native reflection errors" \
        "$(grep -m1 -E "$reflectionPattern" "$WORK/server.log")"
else
    pass "server lifecycle free of native reflection errors"
fi

# Fail-closed startup: no authentication configured and none explicitly allowed.
# The node must refuse to start, and that refusal must be clean, with no native
# reflection errors in the rollback and destroy path.
printf '\nVerifying fail-closed startup is clean\n'
timeout 60 "${SAPL[@]}" server --server.port="$PORT" \
    --io.sapl.pdp.embedded.pdp-config-type=DIRECTORY \
    --io.sapl.pdp.embedded.config-path="$POLICIES" \
    --io.sapl.pdp.embedded.policies-path="$POLICIES" >"$WORK/failclosed.log" 2>&1
if grep -qE "$reflectionPattern" "$WORK/failclosed.log"; then
    fail "fail-closed startup free of native reflection errors" \
        "$(grep -m1 -E "$reflectionPattern" "$WORK/failclosed.log")"
else
    pass "fail-closed startup free of native reflection errors"
fi

printf '\n%s\n' "----------------------------------------"
printf 'Result: %d passed, %d failed\n' "$PASS" "$FAIL"
[[ "$FAIL" -eq 0 ]]
