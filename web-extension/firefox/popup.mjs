import { API_BASE_URL } from './api-config.mjs';

export function validatePageUrl(value) {
  try {
    const url = new URL(value);
    return (url.protocol === 'http:' || url.protocol === 'https:')
      && !url.username
      && !url.password;
  } catch {
    return false;
  }
}

export async function savePage(page, fetchImpl = fetch) {
  if (!page || !validatePageUrl(page.url)) {
    throw new Error('Only valid HTTP or HTTPS pages can be saved.');
  }

  let response;
  try {
    response = await fetchImpl(`${API_BASE_URL}/api/v1/content`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        url: page.url,
        title: typeof page.title === 'string' && page.title.trim() ? page.title.trim() : null,
      }),
      credentials: 'omit',
      redirect: 'error',
    });
  } catch {
    throw new Error('Could not reach Open Link Learn. Check that the planner is running and try again.');
  }

  if (response.status === 409) {
    return { kind: 'duplicate' };
  }

  if (!response.ok) {
    const problem = await readProblem(response);
    throw new Error(problem?.detail || `Open Link Learn could not save this page (HTTP ${response.status}).`);
  }

  let content = null;
  try {
    content = await response.json();
  } catch {
    // A successful response may not include a body.
  }
  return { kind: 'saved', content };
}

export async function initializePopup(documentRef = document, browserApi = browser, fetchImpl = fetch) {
  const title = documentRef.querySelector('#page-title');
  const url = documentRef.querySelector('#page-url');
  const button = documentRef.querySelector('#save-button');
  const status = documentRef.querySelector('#status');
  let page = null;

  try {
    const [activeTab] = await browserApi.tabs.query({ active: true, currentWindow: true });
    if (!activeTab || !validatePageUrl(activeTab.url)) {
      throw new Error('This browser page cannot be saved. Open an HTTP or HTTPS page.');
    }

    page = { url: activeTab.url, title: activeTab.title || null };
    title.textContent = page.title || new URL(page.url).hostname;
    url.textContent = page.url;
    button.disabled = false;
  } catch (error) {
    title.textContent = 'Page unavailable';
    status.textContent = error.message;
    status.dataset.state = 'error';
  }

  button.addEventListener('click', async () => {
    if (!page || button.disabled) return;
    button.disabled = true;
    button.textContent = 'Saving...';
    status.textContent = '';
    delete status.dataset.state;

    try {
      const result = await savePage(page, fetchImpl);
      if (result.kind === 'duplicate') {
        status.textContent = 'This page is already in your backlog.';
        status.dataset.state = 'success';
      } else {
        status.textContent = `Saved: ${result.content?.title || page.title || new URL(page.url).hostname}`;
        status.dataset.state = 'success';
      }
      button.textContent = 'Saved';
    } catch (error) {
      status.textContent = error.message;
      status.dataset.state = 'error';
      button.disabled = false;
      button.textContent = 'Try again';
    }
  });
}

async function readProblem(response) {
  try {
    return await response.json();
  } catch {
    return null;
  }
}

if (globalThis.browser && globalThis.document) {
  initializePopup(globalThis.document, globalThis.browser);
}