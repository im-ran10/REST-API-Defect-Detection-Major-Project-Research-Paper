#!/usr/bin/env python3
"""nocodb_setup.py — generic, defect-independent fixture for the nocodb reference deployment (T5, D25).

Built only from the official spec (D5.1 input) and nocodb's public behaviour:
  1. first user = super admin (sign-up, or sign-in if it already exists);
  2. project "restdd_fixture" with one table "items" (Title, Qty, Done, Notes) and three rows;
  3. writes ~/restdd-targets/nocodb.json (base URL, auth by env var name, fixture names and ids, reset commands).

Needs: `source ~/.nocodb_credentials` (NOCODB_EMAIL, NOCODB_PASSWORD). Prints no token or password; prints
how many hours the sign-in token stays valid (decoded from its expiry claim). Every failed request prints
method, path, status and the (token-free) response body, so the steps can be adjusted.
"""
import base64
import json
import os
import sys
import time
import urllib.error
import urllib.request

BASE = os.environ.get("NOCODB_URL", "http://localhost:8090")
REPO = os.path.expanduser(os.environ.get("RESTDD_REPO", "~/projects/rest-api-defect-detection"))
TARGET = os.path.expanduser(os.environ.get("TARGET_FILE", "~/restdd-targets/nocodb.json"))
PROJECT, TABLE = "restdd_fixture", "items"


def call(method, path, body=None, token=None, ok=(200, 201)):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("xc-auth", token)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            status, text = r.status, r.read().decode()
    except urllib.error.HTTPError as e:
        status, text = e.code, e.read().decode(errors="replace")
    if status not in ok:
        if token:
            text = text.replace(token, "<redacted>")
        print(f"FAILED {method} {path} -> {status}: {text[:500]}", file=sys.stderr)
        sys.exit(1)
    return json.loads(text) if text.strip() else None


def hours_valid(token):
    try:
        payload = token.split(".")[1]
        payload += "=" * (-len(payload) % 4)
        exp = json.loads(base64.urlsafe_b64decode(payload)).get("exp")
        return None if exp is None else round((exp - time.time()) / 3600, 1)
    except Exception:
        return None


def main():
    email, password = os.environ.get("NOCODB_EMAIL"), os.environ.get("NOCODB_PASSWORD")
    if not email or not password:
        sys.exit("run `source ~/.nocodb_credentials` first")

    req = urllib.request.Request(BASE + "/api/v1/db/auth/user/signup", method="POST",
                                 data=json.dumps({"email": email, "password": password}).encode(),
                                 headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            token = json.loads(r.read().decode())["token"]
            print("signed up the admin user (first user = super admin)")
    except urllib.error.HTTPError as e:
        print(f"sign-up answered {e.code} (user probably exists); signing in")
        token = call("POST", "/api/v1/db/auth/user/signin", {"email": email, "password": password})["token"]
    h = hours_valid(token)
    print("sign-in token valid for", "unknown (no expiry claim)" if h is None else f"{h} h")

    me = call("GET", "/api/v1/db/auth/user/me", token=token)
    print("user/me roles:", me.get("roles"))

    projects = call("GET", "/api/v1/db/meta/projects/", token=token)
    plist = projects.get("list", projects) if isinstance(projects, dict) else projects
    project = next((p for p in plist if p.get("title") == PROJECT), None)
    if project is None:
        project = call("POST", "/api/v1/db/meta/projects/", {"title": PROJECT}, token=token)
        print("created project", PROJECT)
    pid = project["id"]

    tables = call("GET", f"/api/v1/db/meta/projects/{pid}/tables", token=token)
    tlist = tables.get("list", tables) if isinstance(tables, dict) else tables
    table = next((t for t in tlist if t.get("title") == TABLE), None)
    if table is None:
        table = call("POST", f"/api/v1/db/meta/projects/{pid}/tables", {
            "table_name": TABLE, "title": TABLE,
            "columns": [
                {"column_name": "id", "title": "Id", "uidt": "ID", "dt": "integer", "pk": True, "ai": True, "rqd": True},
                {"column_name": "title", "title": "Title", "uidt": "SingleLineText"},
                {"column_name": "qty", "title": "Qty", "uidt": "Number"},
                {"column_name": "done", "title": "Done", "uidt": "Checkbox"},
                {"column_name": "notes", "title": "Notes", "uidt": "LongText"},
            ]}, token=token)
        print("created table", TABLE)
    tid = table["id"]

    rows_path = f"/api/v1/db/data/noco/{PROJECT}/{TABLE}"
    rows = call("GET", rows_path, token=token)
    have = len(rows.get("list", [])) if isinstance(rows, dict) else len(rows)
    for r in [{"Title": "alpha", "Qty": 3, "Done": False, "Notes": "first row"},
              {"Title": "beta", "Qty": 0, "Done": True, "Notes": "second row"},
              {"Title": "gamma", "Qty": 12, "Done": False, "Notes": ""}][have:]:
        call("POST", rows_path, r, token=token)
    rows = call("GET", rows_path, token=token)
    print("rows in", TABLE + ":", len(rows.get("list", [])) if isinstance(rows, dict) else len(rows))

    target = {
        "baseUrl": BASE,
        "auth": {"kind": "header", "name": "xc-auth", "env": "NOCODB_TOKEN"},
        "fixtures": {"orgs": "noco", "projectId": pid, "projectName": PROJECT, "tableId": tid, "tableName": TABLE},
        "reset": {
            "kind": "command", "name": "nocodb-sqlite-volume",
            "snapshot": f"bash {REPO}/scripts/reset/nocodb_reset.sh snapshot",
            "restore": f"bash {REPO}/scripts/reset/nocodb_reset.sh restore",
            "healthUrl": BASE + "/api/v1/db/auth/user/me", "healthUsesAuth": True,
            "healthTimeoutSec": 180, "commandTimeoutSec": 600},
    }
    os.makedirs(os.path.dirname(TARGET), exist_ok=True)
    with open(TARGET, "w") as f:
        json.dump(target, f, indent=2)
    print("wrote", TARGET, "with fixtures", json.dumps(target["fixtures"]))


if __name__ == "__main__":
    main()
