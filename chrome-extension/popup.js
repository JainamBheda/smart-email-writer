const DEFAULTS = { backendUrl: 'http://localhost:8080', tone: 'professional' }

const backendInput = document.getElementById('backendUrl')
const toneInput = document.getElementById('tone')
const statusText = document.getElementById('status')

chrome.storage.sync.get(DEFAULTS, (settings) => {
  backendInput.value = settings.backendUrl
  toneInput.value = settings.tone
})

document.getElementById('save').addEventListener('click', () => {
  const backendUrl = backendInput.value.trim().replace(/\/+$/, '')

  try {
    const parsed = new URL(backendUrl)
    if (!['http:', 'https:'].includes(parsed.protocol)) throw new Error('Unsupported protocol')
  } catch {
    statusText.textContent = 'Enter a valid http:// or https:// URL.'
    statusText.className = 'error'
    return
  }

  chrome.storage.sync.set({ backendUrl, tone: toneInput.value }, () => {
    statusText.textContent = 'Settings saved.'
    statusText.className = 'success'
    setTimeout(() => { statusText.textContent = '' }, 2000)
  })
})
