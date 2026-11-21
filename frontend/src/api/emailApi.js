import axios from 'axios'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080',
  timeout: 120000,
  headers: { 'Content-Type': 'application/json' },
})

export async function generateReply({ emailContent, tone }) {
  const response = await api.post('/api/email/generate', { emailContent, tone })
  return typeof response.data === 'string' ? response.data : JSON.stringify(response.data)
}

export async function checkHealth() {
  const response = await api.get('/api/email/health')
  return response.data
}

export function describeApiError(error) {
  const data = error.response?.data
  if (data && typeof data === 'object') {
    return [data.message, data.detail].filter(Boolean).join(' — ')
  }
  if (error.code === 'ECONNABORTED') {
    return 'The AI service took too long to respond. Please try again.'
  }
  if (!error.response) {
    return 'Cannot reach the backend. Is it running on ' + api.defaults.baseURL + '?'
  }
  return error.message || 'Something went wrong. Please try again.'
}
