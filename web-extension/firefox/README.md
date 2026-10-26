# Firefox Extension

The toolbar popup displays the active tab's title and URL. The user must press
**Save to backlog** before the extension sends `{ "url", "title" }` to
`POST /api/v1/content`. Only credential-free HTTP(S) URLs are accepted. The
popup handles duplicates (`409`), validation failures, and network errors.

## API origin and permissions

The API origin is defined in `api-config.mjs`; it is not an extension setting.
The same origin must appear in `manifest.json` under `host_permissions`. The
checked-in origin is `http://localhost:8080` for local development. Set your
production origin in both files before creating a release package. Requests use
`credentials: "omit"` and reject redirects.

The extension reads the current tab using `activeTab` and sends its URL and page
title to the configured planner. Firefox's manifest declares the transmitted
data as required `browsingActivity`. Do not add broad host permissions or page
scripts unless the feature genuinely requires them.

## Load temporarily

1. Start the planner and confirm it is reachable at the configured API origin.
2. Open `about:debugging#/runtime/this-firefox` in Firefox.
3. Select **Load Temporary Add-on...** and choose this folder's `manifest.json`.
4. Open an HTTP or HTTPS page, select the Open Link Learn toolbar button, then choose
	**Save to backlog**.

Temporary add-ons are removed when Firefox restarts. Release packages must be
signed by Mozilla; this repository does not yet publish an AMO listing.

## Tests

From the repository root, run:

```
npm --prefix web-extension run test:firefox
```

Tests cover URL validation, request shape, success and duplicate responses,
server and network errors, popup interaction, and manifest permissions.