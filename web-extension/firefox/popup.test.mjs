import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { test } from 'node:test';
import { API_BASE_URL } from './api-config.mjs';
import { initializePopup, savePage, validatePageUrl } from './popup.mjs';

const page = {
  url: 'https://www.youtube.com/watch?v=wi3mTANVdgI',
  title: 'A useful lesson',
};

test('accepts only credential-free HTTP and HTTPS URLs', () => {
  assert.equal(validatePageUrl(page.url), true);
  assert.equal(validatePageUrl('http://example.org/article'), true);
  assert.equal(validatePageUrl('about:config'), false);
  assert.equal(validatePageUrl('javascript:alert(1)'), false);
  assert.equal(validatePageUrl('https://user:pass@example.org/'), false);
  assert.equal(validatePageUrl('not a URL'), false);
});

test('posts the active page and its title to the fixed content endpoint', async () => {
  let request;
  const result = await savePage(page, async (url, options) => {
    request = { url, options };
    return jsonResponse(201, { title: page.title });
  });

  assert.equal(request.url, `${API_BASE_URL}/api/v1/content`);
  assert.equal(request.options.method, 'POST');
  assert.equal(request.options.credentials, 'omit');
  assert.equal(request.options.redirect, 'error');
  assert.deepEqual(JSON.parse(request.options.body), { url: page.url, title: page.title });
  assert.deepEqual(result, { kind: 'saved', content: { title: page.title } });
});

test('uses a null title when the active tab has no nonblank title', async () => {
  let sentBody;
  await savePage({ ...page, title: '  ' }, async (_url, options) => {
    sentBody = JSON.parse(options.body);
    return jsonResponse(201, {});
  });

  assert.deepEqual(sentBody, { url: page.url, title: null });
});

test('reports a duplicate response distinctly', async () => {
  const result = await savePage(page, async () => jsonResponse(409, { detail: 'duplicate' }));
  assert.deepEqual(result, { kind: 'duplicate' });
});

test('surfaces API validation details and network failures', async () => {
  await assert.rejects(
    savePage(page, async () => jsonResponse(400, { detail: 'URL is not supported.' })),
    { message: 'URL is not supported.' },
  );
  await assert.rejects(
    savePage(page, async () => { throw new TypeError('connection refused'); }),
    { message: 'Could not reach Open Link Learn. Check that the planner is running and try again.' },
  );
});

test('rejects invalid active page URLs before making a request', async () => {
  let called = false;
  await assert.rejects(savePage({ url: 'about:home' }, async () => { called = true; }), {
    message: 'Only valid HTTP or HTTPS pages can be saved.',
  });
  assert.equal(called, false);
});

test('previews the active tab and saves only after an explicit button press', async () => {
  const elements = createPopupElements();
  let requestCount = 0;
  await initializePopup(createDocument(elements), {
    tabs: { query: async (query) => {
      assert.deepEqual(query, { active: true, currentWindow: true });
      return [page];
    } },
  }, async () => {
    requestCount += 1;
    return jsonResponse(201, { title: page.title });
  });

  assert.equal(elements['#page-title'].textContent, page.title);
  assert.equal(elements['#page-url'].textContent, page.url);
  assert.equal(elements['#save-button'].disabled, false);
  assert.equal(requestCount, 0);

  await elements['#save-button'].click();
  assert.equal(requestCount, 1);
  assert.equal(elements['#status'].textContent, `Saved: ${page.title}`);
  assert.equal(elements['#status'].dataset.state, 'success');
  assert.equal(elements['#save-button'].textContent, 'Saved');
});

test('shows API errors in the popup and allows retry', async () => {
  const elements = createPopupElements();
  await initializePopup(createDocument(elements), {
    tabs: { query: async () => [page] },
  }, async () => jsonResponse(400, { detail: 'URL is not supported.' }));

  await elements['#save-button'].click();
  assert.equal(elements['#status'].textContent, 'URL is not supported.');
  assert.equal(elements['#status'].dataset.state, 'error');
  assert.equal(elements['#save-button'].disabled, false);
  assert.equal(elements['#save-button'].textContent, 'Try again');
});

test('manifest permissions stay narrow and match the internal API origin', async () => {
  const manifest = JSON.parse(await readFile(new URL('./manifest.json', import.meta.url), 'utf8'));

  assert.equal(manifest.manifest_version, 3);
  assert.equal(manifest.name, 'Open Link Learn: Save to backlog');
  assert.equal(manifest.action.default_title, 'Save page to Open Link Learn');
  assert.ok(manifest.permissions.includes('activeTab'));
  assert.deepEqual(manifest.host_permissions, [`${new URL(API_BASE_URL).origin}/*`]);
  assert.equal(manifest.action.default_popup, 'popup.html');
  assert.deepEqual(manifest.browser_specific_settings.gecko.data_collection_permissions.required, [
    'browsingActivity',
  ]);
});

function jsonResponse(status, body) {
  return {
    status,
    ok: status >= 200 && status < 300,
    async json() { return body; },
  };
}

function createPopupElements() {
  return Object.fromEntries(['#page-title', '#page-url', '#save-button', '#status'].map((selector) => {
    const element = {
      textContent: '',
      disabled: selector === '#save-button',
      dataset: {},
      addEventListener(type, listener) {
        assert.equal(type, 'click');
        this.click = listener;
      },
    };
    return [selector, element];
  }));
}

function createDocument(elements) {
  return { querySelector: (selector) => elements[selector] };
}