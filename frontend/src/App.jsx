import { useEffect, useState } from 'react'
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Container,
  FormControl,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import ContentCopyIcon from '@mui/icons-material/ContentCopy'
import SendIcon from '@mui/icons-material/Send'
import CheckIcon from '@mui/icons-material/Check'
import RestartAltIcon from '@mui/icons-material/RestartAlt'
import EmailIcon from '@mui/icons-material/Email'
import { checkHealth, describeApiError, generateReply } from './api/emailApi'
import './App.css'

const TONES = [
  { value: '', label: 'Default (professional)' },
  { value: 'professional', label: 'Professional' },
  { value: 'friendly', label: 'Friendly' },
  { value: 'casual', label: 'Casual' },
  { value: 'assertive', label: 'Assertive' },
  { value: 'empathetic', label: 'Empathetic' },
  { value: 'concise', label: 'Concise' },
]

function App() {
  const [emailContent, setEmailContent] = useState('')
  const [tone, setTone] = useState('')
  const [generatedReply, setGeneratedReply] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [copied, setCopied] = useState(false)
  const [apiOnline, setApiOnline] = useState(null)

  useEffect(() => {
    checkHealth()
      .then(() => setApiOnline(true))
      .catch(() => setApiOnline(false))
  }, [])

  useEffect(() => {
    if (!copied) return undefined
    const timer = setTimeout(() => setCopied(false), 2000)
    return () => clearTimeout(timer)
  }, [copied])

  const handleSubmit = async () => {
    if (!emailContent.trim() || loading) return
    setLoading(true)
    setError('')
    setCopied(false)
    try {
      setGeneratedReply(await generateReply({ emailContent, tone }))
    } catch (err) {
      setGeneratedReply('')
      setError(describeApiError(err))
    } finally {
      setLoading(false)
    }
  }

  const handleClear = () => {
    setEmailContent('')
    setTone('')
    setGeneratedReply('')
    setError('')
    setCopied(false)
  }

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(generatedReply)
      setCopied(true)
    } catch {
      setError('Clipboard access was blocked. Select the text and copy it manually.')
    }
  }

  return (
    <Box className="app-shell">
      <Container maxWidth="lg" className="app-container">
        <Stack direction="row" alignItems="center" justifyContent="space-between" spacing={2} className="app-header">
          <Stack direction="row" alignItems="center" spacing={1.5}>
            <Box className="brand-mark"><EmailIcon fontSize="small" /></Box>
            <Box>
              <Typography variant="h6" component="h1" className="brand-title">Smart Email Writer</Typography>
              <Typography variant="caption" color="text.secondary">AI-assisted replies, ready to send</Typography>
            </Box>
          </Stack>
          {apiOnline !== null && (
            <Chip
              size="small"
              label={apiOnline ? 'API connected' : 'API offline'}
              color={apiOnline ? 'success' : 'warning'}
              variant="outlined"
            />
          )}
        </Stack>

        <Box className="hero">
          <Typography variant="h3" component="h2" className="hero-title">Write better replies in seconds</Typography>
          <Typography color="text.secondary" className="hero-subtitle">
            Paste an email, choose a tone, and generate a polished reply. Nothing is stored.
          </Typography>
        </Box>

        <Box className="workspace">
          <Paper elevation={0} className="panel">
            <Typography variant="h6" component="h3" gutterBottom>Original email</Typography>
            <TextField
              fullWidth
              multiline
              rows={10}
              variant="outlined"
              label="Email content"
              placeholder="Paste the email you want to reply to here..."
              value={emailContent}
              onChange={(event) => setEmailContent(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) {
                  event.preventDefault()
                  handleSubmit()
                }
              }}
              error={Boolean(error) && !emailContent.trim()}
              disabled={loading}
            />
            <Stack direction="row" justifyContent="space-between" alignItems="center" className="meta-row">
              <Typography variant="caption" color="text.secondary">{emailContent.length} / 20000 characters</Typography>
              <Button size="small" startIcon={<RestartAltIcon />} onClick={handleClear} disabled={loading || !emailContent}>
                Clear
              </Button>
            </Stack>

            <FormControl fullWidth disabled={loading}>
              <InputLabel id="tone-label">Tone</InputLabel>
              <Select labelId="tone-label" id="tone" value={tone} label="Tone" onChange={(event) => setTone(event.target.value)}>
                {TONES.map((option) => (
                  <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>
                ))}
              </Select>
            </FormControl>

            <Button
              variant="contained"
              size="large"
              fullWidth
              onClick={handleSubmit}
              disabled={!emailContent.trim() || loading}
              startIcon={loading ? <CircularProgress size={18} color="inherit" /> : <SendIcon />}
              sx={{ mt: 2 }}
            >
              {loading ? 'Generating reply...' : 'Generate reply'}
            </Button>
            <Typography variant="caption" color="text.secondary" className="shortcut-hint">
              Tip: press Ctrl + Enter to generate
            </Typography>
          </Paper>

          <Paper elevation={0} className="panel output-panel">
            <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 2 }}>
              <Typography variant="h6" component="h3">Generated reply</Typography>
              {generatedReply && (
                <Button
                  size="small"
                  variant="outlined"
                  startIcon={copied ? <CheckIcon /> : <ContentCopyIcon />}
                  onClick={handleCopy}
                >
                  {copied ? 'Copied' : 'Copy'}
                </Button>
              )}
            </Stack>

            {error && <Alert severity="error" className="alert">{error}</Alert>}

            {generatedReply ? (
              <TextField
                fullWidth
                multiline
                rows={12}
                variant="outlined"
                label="Reply"
                value={generatedReply}
                InputProps={{ readOnly: true }}
              />
            ) : (
              <Box className="empty-state">
                <EmailIcon className="empty-icon" />
                <Typography color="text.secondary">Your generated reply will appear here.</Typography>
              </Box>
            )}
          </Paper>
        </Box>
      </Container>
    </Box>
  )
}

export default App
