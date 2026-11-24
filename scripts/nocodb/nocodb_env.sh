# source this file: signs in and exports NOCODB_TOKEN for the runner, without printing it.
#   source ~/projects/rest-api-defect-detection/scripts/nocodb/nocodb_env.sh
source "$HOME/.nocodb_credentials"
NOCODB_TOKEN="$(python3 -c '
import json, os, urllib.request
req = urllib.request.Request(os.environ.get("NOCODB_URL", "http://localhost:8090") + "/api/v1/db/auth/user/signin",
    data=json.dumps({"email": os.environ["NOCODB_EMAIL"], "password": os.environ["NOCODB_PASSWORD"]}).encode(),
    headers={"Content-Type": "application/json"}, method="POST")
print(json.loads(urllib.request.urlopen(req, timeout=30).read())["token"])')" && export NOCODB_TOKEN \
  && echo "NOCODB_TOKEN set (not printed)" || echo "sign-in failed: is nocodb running?"
