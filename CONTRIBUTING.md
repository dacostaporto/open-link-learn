# Contributing to Open Link Learn

Bug reports, focused fixes, documentation improvements, and carefully scoped
feature proposals are welcome. Please read the root [README](README.md) first; 
it defines current product scope and API contracts.

## Report an issue

Before opening an issue, search existing issues and discussions for the same
problem. Use the repository's **Issues** tab on GitHub and provide enough detail
for another person to reproduce or evaluate the report.

For a bug, include:

- What you expected and what happened instead.
- Steps to reproduce, including relevant URLs only when they are safe to share.
- Browser/OS, Java, Node, or Docker versions as relevant.
- The smallest useful error message or log excerpt, with secrets and personal
  data removed.
- Whether the problem is consistent or intermittent.

For a feature request, explain the user problem, the expected behavior, and any
important tradeoffs.

Do not report security vulnerabilities in a public issue. Use GitHub's private
vulnerability reporting for the repository if it is enabled; otherwise contact
the maintainers privately through the contact method listed on the repository.
Do not include passwords, access tokens, private URLs, or personal data in an
issue.

## Make a change

1. Fork the repository and create a branch from the default branch. Use a short,
   descriptive name such as `fix/backlog-filter` or `docs/local-setup`.
2. Keep the change focused. Follow the existing feature boundaries and code
   style; avoid unrelated refactors.
3. Add or update tests for behavior changes. Update the README, API contract,
   when the documented behavior changes.
4. For database changes, add a new Flyway migration. Never rewrite an applied
   migration. Preserve owner scoping and the no-login security boundary.
5. Run the checks relevant to your change before opening a pull request.

Typical project checks from the repository root:

```
mvn -B -ntp -f backend/pom.xml verify
npm --prefix frontend ci
npm --prefix frontend run lint
npm --prefix frontend test -- --watch=false
npm --prefix frontend run build
npm --prefix frontend run verify:pwa
npm --prefix web-extension run test:firefox
```

The backend integration tests require Docker for Testcontainers. If a check
cannot run in your environment, say which one and why in the pull request.

## Open a pull request

Push your branch to your fork and open a pull request against the default
branch. In the description:

- Summarize the user-visible or operational change and its motivation.
- Link related issues using GitHub keywords such as `Fixes #123` when relevant.
- List the checks you ran and their results.
- Include screenshots for meaningful UI changes and note any migration or
  configuration steps.
- Call out limitations, security implications, or deliberate plan deviations.

Keep the pull request focused and be ready to respond to review feedback. CI
must pass before merge. Do not include generated build output, local `.env`
files, credentials, or unrelated changes.

## License

By submitting a contribution, you agree that it may be distributed under the
project's [MIT License](LICENSE). You retain copyright in your contribution.