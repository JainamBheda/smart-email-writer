const CONTENT_SELECTORS = [
  '.a3s.aiL',
  '.h7',
  '.gmail_quote',
  '[data-message-text]',
]

const TOOLBAR_SELECTORS = [
  '.btC',
  '[role="toolbar"]',
  '.aDh',
  '.gU.Up',
]

function getEmailContent() {
  for (const selector of CONTENT_SELECTORS) {
    const nodes = document.querySelectorAll(selector)
    for (const node of nodes) {
      const text = (node.innerText || node.textContent || '').trim()
      if (text) return text
    }
  }
  return ''
}

function findComposeToolbar() {
  const dialogs = document.querySelectorAll('[role="dialog"]')
  const scopes = dialogs.length ? [...dialogs] : [document]
  for (const scope of scopes) {
    for (const selector of TOOLBAR_SELECTORS) {
      const toolbar = scope.matches?.(selector) ? scope : scope.querySelector(selector)
      if (toolbar) return toolbar
    }
  }
  return null
}

function findComposeBox(toolbar) {
  const dialog = toolbar.closest('[role="dialog"]') || document
  return dialog.querySelector('[role="textbox"][contenteditable="true"]')
}

function createReplyButton() {
  const button = document.createElement('div')
  button.className = 'T-I J-J5-Ji aoO v7 T-I-atl L3 ew-ai-reply-button'
  button.setAttribute('role', 'button')
  button.setAttribute('tabindex', '0')
  button.setAttribute('data-tooltip', 'Generate AI reply')
  button.textContent = 'AI Reply'
  return button
}

async function handleReplyClick(button, toolbar) {
  if (button.dataset.busy === 'true') return
  const emailContent = getEmailContent()

  if (!emailContent) {
    showNotice('Open a message and try again — no email text was found.')
    return
  }

  button.dataset.busy = 'true'
  button.textContent = 'Generating…'
  button.setAttribute('aria-disabled', 'true')

  try {
    const result = await chrome.runtime.sendMessage({ type: 'GENERATE_EMAIL', emailContent })

    if (!result || !result.ok) {
      throw new Error(result?.error || 'Unknown error')
    }

    const composeBox = findComposeBox(toolbar)
    if (!composeBox) {
      throw new Error('Compose box was not found.')
    }

    composeBox.focus()
    document.execCommand('insertText', false, result.text)
    button.textContent = 'Inserted ✓'
  } catch (error) {
    showNotice(`AI reply failed: ${error.message}`)
    button.textContent = 'AI Reply'
  } finally {
    delete button.dataset.busy
    button.removeAttribute('aria-disabled')
    setTimeout(() => {
      if (button.isConnected) button.textContent = 'AI Reply'
    }, 2500)
  }
}

function injectButton() {
  const toolbar = findComposeToolbar()
  if (!toolbar || toolbar.querySelector('.ew-ai-reply-button')) return

  const button = createReplyButton()
  button.addEventListener('click', () => handleReplyClick(button, toolbar))
  button.addEventListener('keydown', (event) => {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      handleReplyClick(button, toolbar)
    }
  })
  toolbar.insertBefore(button, toolbar.firstChild)
}

function showNotice(text) {
  let notice = document.querySelector('.ew-ai-notice')
  if (!notice) {
    notice = document.createElement('div')
    notice.className = 'ew-ai-notice'
    document.body.appendChild(notice)
  }
  notice.textContent = text
  clearTimeout(showNotice.timer)
  showNotice.timer = setTimeout(() => notice.remove(), 5000)
}

let scheduled = false
const observer = new MutationObserver(() => {
  if (scheduled) return
  scheduled = true
  setTimeout(() => {
    scheduled = false
    injectButton()
  }, 300)
})

observer.observe(document.body, { childList: true, subtree: true })
injectButton()
