# Open Link Learn Frontend

This folder contains the Angular 22 application. For the full product overview,
deployment guidance, and project-wide checks, start with the root
[README](../README.md) and [contributor guide](../CONTRIBUTING.md).

## Development

From the repository root, install the locked dependencies and start the Angular
development server:

```
npm --prefix frontend ci
npm --prefix frontend start
```

The app runs at `http://localhost:4200` and proxies `/api` requests to
`http://localhost:8080`. Start the backend and PostgreSQL first; see the root
README for Docker Compose instructions.

## Checks

```
npm --prefix frontend run lint
npm --prefix frontend test -- --watch=false
npm --prefix frontend run build
npm --prefix frontend run verify:pwa
```

Unit tests use Vitest. The production build includes the web app manifest and
service worker. There is no configured Angular end-to-end test command yet.
Angular CLI does not come with an end-to-end testing framework by default. You can choose one that suits your needs.

## Additional Resources

For more information on using the Angular CLI, including detailed command references, visit the [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli) page.
