-- wrk Lua script for SAPL PDP decide-once endpoint (RBAC deny case).
-- Same authorization question as the OPA RBAC benchmark:
-- bob (role: test) requests write on foo123 -> DENY
wrk.method  = "POST"
wrk.body    = '{"subject":{"username":"bob","role":"test"},"action":"write","resource":{"type":"foo123"}}'
wrk.headers["Content-Type"] = "application/json"
