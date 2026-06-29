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

if [ "$#" -gt 0 ]; then SAPL=("$@"); else SAPL=("sapl-node/target/sapl"); fi
PORT="${SMOKE_PORT:-8099}"
WORK="$(mktemp -d)"
POLICIES="$WORK/policies"
SERVER_PID=""
PASS=0
FAIL=0

cleanup() {
    if [ -n "$SERVER_PID" ]; then
        kill "$SERVER_PID" 2>/dev/null || true
        wait "$SERVER_PID" 2>/dev/null || true
    fi
    rm -rf "$WORK"
}
trap cleanup EXIT

pass() { PASS=$((PASS + 1)); printf 'PASS  %s\n' "$1"; }
fail() { FAIL=$((FAIL + 1)); printf 'FAIL  %s\n' "$1"; [ -n "${2:-}" ] && printf '      %s\n' "$2"; }

# expectExit <description> <expected-code> <command...>
expectExit() {
    local description="$1" expected="$2"
    shift 2
    "$@" >/dev/null 2>&1
    local actual=$?
    if [ "$actual" = "$expected" ]; then
        pass "$description (exit $actual)"
    else
        fail "$description" "expected exit $expected, got $actual"
    fi
}

# expectOutput <description> <needle> <command...>
expectOutput() {
    local description="$1" needle="$2"
    shift 2
    local output
    output="$("$@" 2>/dev/null)"
    case "$output" in
    *"$needle"*) pass "$description" ;;
    *) fail "$description" "output did not contain '$needle': ${output:-<empty>}" ;;
    esac
}

# expectFile <description> <path>
expectFile() {
    if [ -s "$2" ]; then pass "$1"; else fail "$1" "missing or empty file: $2"; fi
}

printf 'SAPL smoke test\n  node: %s\n  workdir: %s\n\n' "${SAPL[*]}" "$WORK"

mkdir -p "$POLICIES"
# indexing is forced to SMTDD so the server round trip below exercises the
# structural-equality index path. That path reads operator record components
# reflectively and fails in a native image unless they are registered, which is
# the regression guard for that class of native-image-only failure.
cat >"$POLICIES/pdp.json" <<'JSON'
{
  "configurationId": "smoke",
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
expectExit   "--version"                0 "${SAPL[@]}" --version
expectOutput "--version reports node"   "SAPL Node" "${SAPL[@]}" --version
expectExit   "--help"                   0 "${SAPL[@]}" --help

# One-shot evaluation against a directory
expectOutput "decide-once permit"       '"decision":"PERMIT"' "${SAPL[@]}" decide-once --dir "$POLICIES" -s "$S" -a "$USE" -r "$MRT"
expectExit   "check permit"             0 "${SAPL[@]}" check --dir "$POLICIES" -s "$S" -a "$USE" -r "$MRT"
expectExit   "check deny"               2 "${SAPL[@]}" check --dir "$POLICIES" -s "$S" -a "$DELETE" -r "$MRT"
expectExit   "check not-applicable"     3 "${SAPL[@]}" check --dir "$POLICIES" -s '"nobody"' -a "$USE" -r "$MRT"

# Streaming evaluation, bounded so it cannot hang the run
streamLine="$(timeout 15 "${SAPL[@]}" decide --dir "$POLICIES" -s "$S" -a "$USE" -r "$MRT" 2>/dev/null | head -1)"
case "$streamLine" in
*'"decision":"PERMIT"'*) pass "decide stream first line permit" ;;
*) fail "decide stream first line permit" "got: ${streamLine:-<empty>}" ;;
esac

# Policy tests and coverage
expectExit "test passes" 0 "${SAPL[@]}" test --dir "$POLICIES" --output "$WORK/coverage"

# Bundle lifecycle: keygen, create, sign, verify, inspect
expectExit "bundle keygen" 0 "${SAPL[@]}" bundle keygen -o "$WORK/key"
expectFile "bundle keygen private key" "$WORK/key.pem"
expectFile "bundle keygen public key" "$WORK/key.pub"
expectExit "bundle create" 0 "${SAPL[@]}" bundle create -i "$POLICIES" -o "$WORK/smoke.saplbundle"
expectFile "bundle file created" "$WORK/smoke.saplbundle"
expectExit "bundle sign" 0 "${SAPL[@]}" bundle sign -b "$WORK/smoke.saplbundle" -k "$WORK/key.pem"
expectExit "bundle verify" 0 "${SAPL[@]}" bundle verify -b "$WORK/smoke.saplbundle" -k "$WORK/key.pub"
expectExit "bundle inspect" 0 "${SAPL[@]}" bundle inspect -b "$WORK/smoke.saplbundle"
expectOutput "decide-once via signed bundle" '"decision":"PERMIT"' "${SAPL[@]}" decide-once --bundle "$WORK/smoke.saplbundle" --public-key "$WORK/key.pub" -s "$S" -a "$USE" -r "$MRT"

# Credential generation
expectOutput "generate basic" "Password:" "${SAPL[@]}" generate basic
expectOutput "generate apikey" "sapl_" "${SAPL[@]}" generate apikey

# Performance tools are wired (no full run in a smoke test)
expectExit "benchmark wired" 0 "${SAPL[@]}" benchmark --help
expectExit "loadtest wired" 0 "${SAPL[@]}" loadtest --help

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

if [ "$ready" = 1 ]; then
    pass "server reaches health UP"
    expectOutput "client decide-once over HTTP" '"decision":"PERMIT"' \
        "${SAPL[@]}" decide-once --remote --url "http://localhost:$PORT" -s "$S" -a "$USE" -r "$MRT"
else
    fail "server reaches health UP" "see server log below"
    tail -n 20 "$WORK/server.log"
fi

printf '\n%s\n' "----------------------------------------"
printf 'Result: %d passed, %d failed\n' "$PASS" "$FAIL"
[ "$FAIL" -eq 0 ]
