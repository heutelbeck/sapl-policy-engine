-- wrk Lua script for OPA benchmarking.
-- Reads the subscription from a JSON file passed via the SUBSCRIPTION_FILE
-- environment variable, or defaults to subscription.json in the current directory.
-- Wraps the subscription in OPA's {"input": ...} format.

local subscription_file = os.getenv("SUBSCRIPTION_FILE") or "subscription.json"
local file = io.open(subscription_file, "r")
if not file then
    io.stderr:write("Error: cannot open " .. subscription_file .. "\n")
    os.exit(1)
end
local body = file:read("*a")
file:close()

wrk.method  = "POST"
wrk.body    = '{"input":' .. body .. '}'
wrk.headers["Content-Type"] = "application/json"
