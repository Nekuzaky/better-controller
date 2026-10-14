# Security policy

## Supported versions

Only the latest minor release receives security fixes. Older versions are not
patched.

| Version | Supported |
|---------|-----------|
| 0.1.x   | ✅        |

## Reporting a vulnerability

This is a client-side Minecraft mod with no network surface and no
authentication. The most plausible vulnerabilities involve:

- crash-on-input from malformed `config/bettercontroller.json`
- input handling that escapes the sandbox (e.g. arbitrary key injection)

If you find something that fits, please **don't open a public issue**.
Instead, use GitHub's private vulnerability reporting:

1. Go to <https://github.com/Nekuzaky/better-controller/security/advisories/new>.
2. Describe the vulnerability, reproduction steps, and impact.

You should expect an acknowledgement within a few days. A fix and a public
advisory will follow once a patch is available.

For routine bugs (crashes that aren't security-relevant, weird input
behaviour) please use the regular [issue tracker](https://github.com/Nekuzaky/better-controller/issues).
