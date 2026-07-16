import json
import re
import unittest
from pathlib import Path


HOST_SERVICES = Path(__file__).resolve().parents[1]
VIBE = HOST_SERVICES / "bridges" / "vibepollo"


class VibepolloTokenScopesTest(unittest.TestCase):
    def test_every_bridge_api_path_is_scoped(self):
        bridge = (VIBE / "VibepolloBridge.ps1").read_text(encoding="utf-8")
        used_paths = {
            path.split("?", 1)[0]
            for path in re.findall(r'"(/api/[A-Za-z0-9_./?-]+)', bridge)
        }
        document = json.loads(
            (VIBE / "moonwaker-token-scopes.example.json").read_text(encoding="utf-8")
        )
        scopes = {entry["path"]: set(entry["methods"]) for entry in document["scopes"]}
        self.assertEqual(set(), used_paths - set(scopes))
        self.assertEqual({"GET"}, scopes["/api/history/sessions/active"])
        for path in {
            "/api/apps/launch",
            "/api/apps/close",
            "/api/clients/disconnect",
            "/api/restart",
            "/api/reset-display-device-persistence",
        }:
            self.assertEqual({"POST"}, scopes[path])


if __name__ == "__main__":
    unittest.main()
