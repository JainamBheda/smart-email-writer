# Smart Email Writer — Gmail extension

Manifest V3 extension that adds an **AI Reply** button to Gmail's compose toolbar. It reads the open email, calls the local backend through a service worker, and inserts the generated reply.

## Install

1. Start the backend (`..\backend`); see the root `README.md`.
2. `chrome://extensions` → enable **Developer mode** → **Load unpacked** → select this folder.
3. Click the extension icon to set **Backend URL** (`http://localhost:8080`) and **Default tone**.
4. Open Gmail → **Compose** → click **AI Reply**.

## Files

| File | Purpose |
| --- | --- |
| `manifest.json` | MV3 config, Gmail content script, service worker, popup |
| `content.js` / `content.css` | Compose toolbar button, email text detection, reply insertion |
| `background.js` | Backend fetch (avoids Gmail's page CSP) |
| `popup.html` / `popup.js` / `popup.css` | Settings UI backed by `chrome.storage.sync` |

## Notes

- Declares `http://localhost/*` and `http://127.0.0.1/*` host permissions. For a different backend host, add its origin and reload the extension.
- Gmail's DOM changes occasionally; selector lists are grouped at the top of `content.js`.
- If no compose box is found, the extension shows a notice rather than failing silently.
