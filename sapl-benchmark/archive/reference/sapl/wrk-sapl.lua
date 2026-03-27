wrk.method = "POST"
wrk.headers["Content-Type"] = "application/json"
wrk.body = '{"subject":{"username":"bob","role":"test"},"action":"write","resource":{"type":"foo123"}}'
