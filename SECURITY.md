# Security policy

Mozaik stores personal data such as meals, health metrics and phone notifications, so security reports are taken seriously.

## Supported versions

Only the [latest release](https://github.com/Enki013/personal-records/releases/latest) and the `main` branch receive security fixes.

## Reporting a vulnerability

Please **do not** open a public issue for security problems.

Report privately through GitHub: open the repository's **Security** tab and choose **Report a vulnerability**. Include:

- what is affected (Android app, Cloudflare Worker, Go backend or web dashboard)
- steps to reproduce, or a proof of concept
- the impact you expect

You should get a first reply within 7 days. Once a fix is released, the report can be made public, with credit to you if you want it.

## Running your own server safely

- Use a long random `API_KEY`, for example from `openssl rand -hex 32`. Rotate it if it is ever exposed.
- Serve the API over HTTPS. Cloudflare Workers do this by default. For the Go backend, put it behind a reverse proxy with TLS if it is reachable from the internet.
- Prefer sending the key in a header (`X-API-Key`, `Authorization: Bearer` or `X-Webhook-Secret`). Only use `?api_key=` for apps that cannot set headers, because URLs can end up in logs.
- Set `ALLOWED_ORIGINS` only to origins you control.
- Keep `.env`, `.dev.vars` and database backups out of version control.
