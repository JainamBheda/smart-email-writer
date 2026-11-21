import { useEffect, useRef, useState } from 'react'
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
import { copyText } from './utils/clipboard'
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
  const [replyTone, setReplyTone] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [info, setInfo] = useState('')
  const [copied, setCopied] = useState(false)
  const [apiOnline, setApiOnline] = useState(null)
  const replyRef = useRef(null)

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
    setInfo('')
    setCopied(false)
    try {
      setGeneratedReply(await generateReply({ emailContent, tone }))
      setReplyTone(tone)
    } catch (err) {
      setGeneratedReply('')
      setReplyTone('')
      setError(describeApiError(err))
    } finally {
      setLoading(false)
    }
  }

  const handleToneChange = (event) => {
    const nextTone = event.target.value
    setTone(nextTone)
    if (generatedReply && nextTone !== replyTone) {
      setGeneratedReply('')
      setReplyTone('')
      setCopied(false)
      setError('')
      setInfo('Tone changed — generate a new reply for this tone.')
    }
  }

  const handleClear = () => {
    setEmailContent('')
    setTone('')
    setGeneratedReply('')
    setReplyTone('')
    setError('')
    setInfo('')
    setCopied(false)
  }

  const handleCopy = async () => {
    if (await copyText(generatedReply)) {
      setError('')
      setCopied(true)
      return
    }
    replyRef.current?.focus()
    replyRef.current?.select()
    setError('Copy was blocked by the browser. The reply is selected — press Ctrl + C to copy.')
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
              <Select labelId="tone-label" id="tone" value={tone} label="Tone" onChange={handleToneChange}>
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
              <Stack direction="row" alignItems="center" spacing={1}>
                <Typography variant="h6" component="h3">Generated reply</Typography>
                {generatedReply && (
                  <Chip size="small" variant="outlined" label={`Tone: ${replyTone || 'default'}`} />
                )}
              </Stack>
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
            {info && <Alert severity="info" className="alert">{info}</Alert>}

            {generatedReply ? (
              <TextField
                fullWidth
                multiline
                rows={12}
                variant="outlined"
                label="Reply"
                value={generatedReply}
                inputRef={replyRef}
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
