import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './styles/premium/00-tokens.css'
import './index.css'
import './premium.css'
import './styles/premium/20-components.css'
import App from './App.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
