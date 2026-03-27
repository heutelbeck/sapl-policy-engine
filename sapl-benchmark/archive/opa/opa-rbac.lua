-- wrk Lua script for OPA v1 data API (RBAC deny case).
-- Same authorization question as the SAPL RBAC benchmark:
-- bob (role: test) requests write on foo123 -> deny
wrk.method  = "POST"
wrk.body    = '{"input":{"subject":"bob","resource":"foo123","action":"write"}}'
wrk.headers["Content-Type"] = "application/json"
