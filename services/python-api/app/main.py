from fastapi import FastAPI, Header, HTTPException

app = FastAPI(title="JWT Gateway Component B", docs_url=None, redoc_url=None)


@app.get("/healthz")
def healthz() -> dict[str, str]:
    return {"status": "ok"}


@app.get("/whoami")
def whoami(
    authenticated_user: str | None = Header(default=None, alias="X-Authenticated-User"),
    authenticated_audience: str | None = Header(default=None, alias="X-Authenticated-Audience"),
    authenticated_scopes: str | None = Header(default=None, alias="X-Authenticated-Scopes"),
) -> dict[str, object]:
    if not authenticated_user or authenticated_audience != "component-b":
        raise HTTPException(status_code=401, detail="gateway_identity_required")
    scopes = set((authenticated_scopes or "").split())
    if "component-b.read" not in scopes:
        raise HTTPException(status_code=403, detail="insufficient_scope")
    return {
        "component": "component-b",
        "user": authenticated_user,
        "audience": authenticated_audience,
        "scopes": sorted(scopes),
    }
