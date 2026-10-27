const SESSION_ID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function validSessionId(value) {
  return typeof value === 'string' && SESSION_ID_PATTERN.test(value) ? value : null;
}

function sessionRoute(sessionId) {
  const id = validSessionId(sessionId);
  return id ? `/preview-content/${encodeURIComponent(id)}` : '/calendar';
}

function safeText(value, fallback, limit) {
  if (typeof value !== 'string') return fallback;
  return value.replace(/[\u0000-\u001f\u007f]/g, ' ').trim().slice(0, limit) || fallback;
}

self.addEventListener('push', (event) => {
  event.stopImmediatePropagation();
  let payload = {};
  try {
    const value = event.data?.json();
    payload = typeof value === 'object' && value !== null && !Array.isArray(value) ? value : {};
  } catch {
    payload = {};
  }

  const sessionId = validSessionId(payload.sessionId);
  const title = safeText(payload.title, 'Learning content', 80);
  const body = safeText(payload.body, 'Open your planner to view your session.', 180);
  event.waitUntil(self.registration.showNotification(title, {
    body,
    icon: '/icons/openlinklearn-icon.svg',
    tag: sessionId ? `session-${sessionId}` : 'session-reminder',
    data: { sessionId },
  }));
});

self.addEventListener('notificationclick', (event) => {
  event.stopImmediatePropagation();
  event.notification.close();
  const route = sessionRoute(event.notification.data?.sessionId);

  event.waitUntil((async () => {
    const scope = new URL(self.registration.scope);
    const target = new URL(route, scope.origin);
    const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
    const appWindow = windows.find((client) => {
      try {
        const clientUrl = new URL(client.url);
        return clientUrl.origin === scope.origin && clientUrl.pathname.startsWith(scope.pathname);
      } catch {
        return false;
      }
    });

    if (appWindow) {
      try {
        const navigatedWindow = await appWindow.navigate(target.href);
        if (navigatedWindow) return navigatedWindow.focus();
      } catch {
        // Open the target in a new app window if the existing one cannot navigate.
      }
    }
    return self.clients.openWindow(target.href);
  })());
});

importScripts('./ngsw-worker.js');