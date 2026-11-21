const DEFAULT_SETTINGS = {
  backendUrl: 'http://localhost:8080',
  tone: 'professional',
}

chrome.runtime.onInstalled.addListener(() => {
  chrome.storage.sync.get(DEFAULT_SETTINGS, (settings) => {
    chrome.storage.sync.set(settings)
  })
})

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (!message || message.type !== 'GENERATE_EMAIL') return false

  generateReply(message).then(sendResponse)
  return true // keep the message channel open for the async response
})

async function generateReply(message) {
  try {
    const settings = await chrome.storage.sync.get(DEFAULT_SETTINGS)
    const baseUrl = String(message.backendUrl || settings.backendUrl || DEFAULT_SETTINGS.backendUrl)
      .trim()
      .replace(/\/+$/, '')

    if (!/^https?:\/\//i.test(baseUrl)) {
      return { ok: false, error: 'Backend URL must start with http:// or https://' }
    }
    if (!String(message.emailContent || '').trim()) {
      return { ok: false, error: 'No email text found in the open message.' }
    }

    const controller = new AbortController()
    const timer = setTimeout(() => controller.abort(), 60000)

    let response
    let body
    try {
      response = await fetch(`${baseUrl}/api/email/generate`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          emailContent: message.emailContent,
          tone: message.tone ?? settings.tone,
        }),
        signal: controller.signal,
      })
      body = await response.text()
    } finally {
      clearTimeout(timer)
    }

    if (!response.ok) {
      return { ok: false, error: `Backend ${response.status}: ${extractMessage(body)}` }
    }
    return { ok: true, text: body }
  } catch (error) {
    if (error.name === 'AbortError') return { ok: false, error: 'The request timed out.' }
    return { ok: false, error: `Cannot reach backend: ${error.message}` }
  }
}

function extractMessage(body) {
  try {
    const parsed = JSON.parse(body)
    return parsed.message || parsed.detail || body
  } catch {
    return body || 'Unknown error'
  }
}
