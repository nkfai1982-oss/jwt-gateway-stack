local jwks = require("resty.jwt-verification-jwks")
local cjson = require("cjson.safe")

local function reject(status, code)
    ngx.status = status
    ngx.header["Content-Type"] = "application/json"
    ngx.say(cjson.encode({ error = code }))
    return ngx.exit(status)
end

local authorization = ngx.var.http_authorization
if not authorization then
    return reject(401, "missing_bearer_token")
end

local token = authorization:match("^[Bb]earer ([A-Za-z0-9._%-]+)$")
if not token then
    return reject(401, "invalid_authorization_header")
end

local expected_issuer = os.getenv("JWT_ISSUER") or "http://jwt-gateway-stack/auth"
local verified, err = jwks.verify_jwt_with_jwks(token, ngx.var.jwks_uri, {
    valid_signing_algorithms = { ["RS256"] = "RS256" },
    issuer = expected_issuer,
    audiences = ngx.var.expected_audience,
    timestamp_skew_seconds = 5,
})

if not verified then
    ngx.log(ngx.WARN, "JWT rejected: ", tostring(err))
    return reject(401, "invalid_or_expired_token")
end

if not verified.header or verified.header.alg ~= "RS256" then
    return reject(401, "invalid_signing_algorithm")
end

local claims = verified.payload or {}
if type(claims.sub) ~= "string" or claims.sub == "" then
    return reject(401, "invalid_subject")
end

local claim_scopes = claims.scope
if type(claim_scopes) ~= "string" then
    return reject(403, "insufficient_scope")
end

local required = ngx.var.required_scope
local has_required_scope = false
for scope in claim_scopes:gmatch("%S+") do
    if scope == required then
        has_required_scope = true
        break
    end
end
if not has_required_scope then
    return reject(403, "insufficient_scope")
end

ngx.req.set_header("X-Authenticated-User", claims.sub)
ngx.req.set_header("X-Authenticated-Audience", ngx.var.expected_audience)
ngx.req.set_header("X-Authenticated-Scopes", claim_scopes)
