# Open Link Learn Browser Extensions

Browser-specific add-ons are kept in separate folders so each browser can use
its own manifest and APIs:

```text
web-extension/
  firefox/
```

The Firefox toolbar add-on saves the active HTTP(S) page to the planner through
`POST /api/v1/content`. It requests `activeTab` access and permission to contact
only the configured API host. Its origin is fixed in extension source; it is
not an end-user preference. Review [the Firefox guide](firefox/README.md) for
setup, local loading, signing, and API-origin requirements.

Run the dependency-free tests from the repository root:

```
npm --prefix web-extension run test:firefox
```

New browser implementations should have their own subdirectory, manifest,
documentation, and automated checks. Keep permissions limited to the feature's
actual needs.