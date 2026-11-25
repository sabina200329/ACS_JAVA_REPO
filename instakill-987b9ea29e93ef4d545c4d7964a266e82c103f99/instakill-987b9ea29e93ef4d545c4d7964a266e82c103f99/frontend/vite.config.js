import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

// Adresa backend-ului. Implicit localhost:8080; pentru backend rulat în WSL se setează
// BACKEND_URL=http://<IP-WSL>:8080 (IP-ul din `wsl hostname -I`) în .env.local sau în terminal.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const backend = env.BACKEND_URL || 'http://localhost:8080'
  console.log(`[vite] backend API -> ${backend}`)

  return {
    plugins: [vue()],
    server: {
      proxy: {
        '/api': {
          target: backend,
          changeOrigin: true
        },
        '/media': {
          target: backend,
          changeOrigin: true
        }
      }
    }
  }
})
