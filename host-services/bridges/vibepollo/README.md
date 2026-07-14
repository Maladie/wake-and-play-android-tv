# Vibepollo Bridge

Vibepollo Bridge is a loopback-only PowerShell service used by Wake & Play Host
Gateway. It exposes health information and the allow-listed FIX actions needed
by Wake & Play. Its small Python transport is required because it communicates
with the local Vibepollo HTTPS API using a modern TLS stack.

Run `Configure-VibepolloBridge.ps1` as the Windows user that owns the Vibepollo
API token, then use `Start-VibepolloBridge.ps1` and
`Test-VibepolloBridge.ps1`. The token is stored with Windows DPAPI and is not
portable to another Windows profile.

Use one instance per profile when credentials or runtime state differ, and give
each concurrently installed instance a distinct loopback port. Never commit
`api_token.dpapi`, `config.json`, logs or exported diagnostics.
