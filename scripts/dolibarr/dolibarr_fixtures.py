#!/usr/bin/env python3
"""dolibarr_fixtures.py — generic, defect-independent Dolibarr fixture through the REST API (T5, 25 Nov 2026).

Creates: one customer (thirdparty), one contact, one product, and one draft proposal, order and invoice for that
customer, each with one line for the product; then (2-hour time box) a second user with no permissions and its own
API key, for the `secondary` role. Built from the project spec and Dolibarr's public API only; identical for every
arm; never looks at any defect.

Run once on a freshly restored deployment, then take a new snapshot:
    source ~/.dolibarr_credentials && python3 scripts/dolibarr/dolibarr_fixtures.py
Updates the fixtures (and secondary role) in ~/restdd-targets/dolibarr.json, keeping its other settings, and, if the second user was
created, ~/.dolibarr_credentials_2 (private, chmod 600) with DOLIBARR_API_KEY_2. No key or password is printed.
"""
import json
import os
import secrets
import sys
import time
import urllib.error
import urllib.request

BASE = os.environ.get("DOLIBARR_URL", "http://localhost:8080/api/index.php")
REPO = os.path.expanduser(os.environ.get("RESTDD_REPO", "~/projects/rest-api-defect-detection"))
TARGET = os.path.expanduser(os.environ.get("TARGET_FILE", "~/restdd-targets/dolibarr.json"))
CRED2 = os.path.expanduser("~/.dolibarr_credentials_2")
KEY = os.environ.get("DOLIBARR_API_KEY")


def call(method, path, body=None, ok=(200, 201)):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(BASE + path, data=data, method=method,
                                 headers={"Content-Type": "application/json", "DOLAPIKEY": KEY})
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            status, text = r.status, r.read().decode()
    except urllib.error.HTTPError as e:
        status, text = e.code, e.read().decode(errors="replace")
    if status not in ok:
        print(f"FAILED {method} {path} -> {status}: {text.replace(KEY, '<redacted>')[:400]}", file=sys.stderr)
        return None
    return json.loads(text) if text.strip() else None


def main():
    if not KEY:
        sys.exit("run `source ~/.dolibarr_credentials` first")
    now = int(time.time())
    fx = {}
    tp = call("POST", "/thirdparties", {"name": "RESTDD Fixture Customer", "client": "1", "code_client": "-1",
                                        "email": "customer@example.com"})
    if tp is None:
        sys.exit("cannot create the customer; stopping")
    fx["thirdpartyId"] = str(tp)
    ct = call("POST", "/contacts", {"lastname": "Fixture", "firstname": "Contact", "socid": tp,
                                    "email": "contact@example.com"})
    if ct is not None:
        fx["contactId"] = str(ct)
    pid = call("POST", "/products", {"ref": "RESTDD-PRODUCT-1", "label": "Fixture product", "price": "10",
                                     "tva_tx": "0", "type": "0", "status": "1", "status_buy": "1"})
    if pid is not None:
        fx["productId"] = str(pid)
        fx["productRef"] = "RESTDD-PRODUCT-1"
    line = {"desc": "Fixture line", "subprice": 10, "qty": 1, "tva_tx": 0, "product_type": 0}
    if pid is not None:
        line["fk_product"] = pid
    for name, path, extra in [("proposalId", "/proposals", {}), ("orderId", "/orders", {}),
                              ("invoiceId", "/invoices", {"type": 0})]:
        obj = call("POST", path, {"socid": tp, "date": now, **extra})
        if obj is None:
            continue
        fx[name] = str(obj)
        if call("POST", f"{path}/{obj}/lines", line) is None:
            print(f"note: no line added to {path}/{obj}", file=sys.stderr)

    # second role (2-hour time box): a user with no rights and its own API key
    second = None
    key2 = secrets.token_hex(16)
    uid = call("POST", "/users", {"login": "restdd_second", "lastname": "Second", "firstname": "Role",
                                  "email": "second@example.com", "password": secrets.token_urlsafe(18),
                                  "api_key": key2})
    if uid is not None:
        call("PUT", f"/users/{uid}", {"api_key": key2})
        fx["secondUserId"] = str(uid)
        probe = urllib.request.Request(BASE + "/users/info", headers={"DOLAPIKEY": key2})
        try:
            with urllib.request.urlopen(probe, timeout=30) as r:
                second = r.status == 200
        except urllib.error.HTTPError:
            second = False
        if second:
            old = os.umask(0o077)
            with open(CRED2, "w") as f:
                f.write(f"export DOLIBARR_API_KEY_2='{key2}'\n")
            os.umask(old)
            os.chmod(CRED2, 0o600)
    print("second role:", "ready (key in ~/.dolibarr_credentials_2)" if second else "not available")

    # update the existing target file (keeps its reset settings); fall back to the repository example
    src = TARGET if os.path.exists(TARGET) else os.path.join(REPO, "scripts/targets/dolibarr.example.json")
    with open(src) as f:
        target = json.load(f)
    target["fixtures"] = fx
    if second:
        target["secondaryAuth"] = {"kind": "header", "name": "DOLAPIKEY", "env": "DOLIBARR_API_KEY_2"}
    else:
        target.pop("secondaryAuth", None)
    os.makedirs(os.path.dirname(TARGET), exist_ok=True)
    with open(TARGET, "w") as f:
        json.dump(target, f, indent=2)
    print("fixtures:", json.dumps(fx))
    print("wrote", TARGET)


if __name__ == "__main__":
    main()
