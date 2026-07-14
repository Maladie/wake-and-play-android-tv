import unittest
import subprocess
from pathlib import Path


HOST_SERVICES = Path(__file__).resolve().parents[1]
REPOSITORY = HOST_SERVICES.parent


def versionable_files():
    result = subprocess.run(
        ["git", "ls-files", "--cached", "--others", "--exclude-standard", "host-services"],
        cwd=REPOSITORY,
        check=True,
        capture_output=True,
        text=True,
    )
    return [REPOSITORY / line for line in result.stdout.splitlines() if line]


class RepositoryLayoutTest(unittest.TestCase):
    def test_runtime_secrets_and_state_are_not_present(self):
        forbidden_names = {
            "gateway.json",
            "gateway-cert.pem",
            "gateway-key.pem",
            "client_secret.dpapi",
            "oauth_token.dpapi",
            "api_token.dpapi",
            "discord_bridge_config.json",
            "discord_remote_state.json",
            "config.json",
        }
        forbidden_directories = {"logs", "exports", "diagnostics", "__pycache__"}
        offenders = []
        for path in versionable_files():
            relative = path.relative_to(HOST_SERVICES)
            lowered_parts = {part.lower() for part in relative.parts}
            if path.name.lower() in forbidden_names:
                offenders.append(str(relative))
            elif lowered_parts.intersection(forbidden_directories):
                offenders.append(str(relative))
            elif ".backup_" in path.name.lower() or ".bak_" in path.name.lower():
                offenders.append(str(relative))
        self.assertEqual([], offenders)

    def test_only_example_json_configuration_is_versioned(self):
        unexpected = []
        for path in versionable_files():
            if path.suffix.lower() != ".json":
                continue
            if not path.name.endswith(".example.json"):
                unexpected.append(str(path.relative_to(HOST_SERVICES)))
        self.assertEqual([], unexpected)


if __name__ == "__main__":
    unittest.main()
