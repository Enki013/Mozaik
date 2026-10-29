# Contributing to Mozaik

Thanks for your interest in Mozaik. Bug reports, ideas and pull requests are all welcome.

## Before you start

- **Bugs:** open an issue with the bug report template. Include the app version, your server engine (Worker or Go) and steps to reproduce.
- **Features and new integrations:** open an issue first so we can agree on the approach before you write code.
- **Security problems:** do not open a public issue. Follow [SECURITY.md](SECURITY.md).

## Development setup

| Part | Requirements | Start |
| :--- | :--- | :--- |
| Android app | Android Studio, JDK 17+ | `cd android && ./gradlew assembleDebug` |
| Cloudflare Worker | Node.js 18+ | `cd worker && npm install && cp .dev.vars.example .dev.vars && npm run db:init:local && npm run dev` |
| Go backend | Go 1.24+ | `cd backend && cp .env.example .env && go run ./cmd/personal-records` |

The README has full setup instructions for each part.

## Guidelines

### Android

- Use Jetpack Compose and Material 3 components and tokens (`MaterialTheme.colorScheme`, `MaterialTheme.typography`, shapes). Do not hardcode colors or sizes.
- Reuse the shared components in `ui/components/`, such as the filter controls, stat card grid, grouped list items, empty states and error banners, so every screen looks and behaves the same.
- Put all user-facing text in `res/values/strings.xml` **and** `res/values-tr/strings.xml`. Use plurals for counts.
- Give interactive elements a content description and the right semantics role.

### Server

- Keep the Worker (`worker/`) and Go (`backend/`) APIs compatible. A new endpoint or field should exist in both, or be clearly marked as Worker-only.
- Every route that returns or changes data must require authentication.
- Update `docs/MOBILE_API.md` when you change the API.

### Secrets and personal data

- Never commit API keys, tokens, `.env` or `.dev.vars` files, keystores or database files.
- Use placeholders such as `YOUR_API_KEY` and `username` in docs, code and tests. Do not use your own account names or records in test fixtures.

## Commits and pull requests

- Use [Conventional Commits](https://www.conventionalcommits.org/): `feat(scope): …`, `fix(scope): …`, `docs: …`, `refactor: …`, `chore: …`. Release notes are generated from these messages.
- Keep each pull request focused on one change.
- Before you open the pull request, run:

  ```bash
  cd android && ./gradlew testDebugUnitTest lintDebug
  cd backend && go test ./...
  ```

- For UI changes, add before and after screenshots in light and dark themes.

## License

By contributing, you agree that your contributions are licensed under the [MIT License](LICENSE).
