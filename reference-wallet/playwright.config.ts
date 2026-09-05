import { defineConfig } from '@playwright/test'
export default defineConfig({
  testDir: './tests/browser', workers: 1, use: { baseURL: 'http://127.0.0.1:4174' },
  webServer: [
    { command: 'npx vite --host 127.0.0.1 --port 4174 --strictPort', url: 'http://127.0.0.1:4174/tests/browser/', reuseExistingServer: false },
    { command: 'npm run start -- --hostname 127.0.0.1 --port 4175', url: 'http://127.0.0.1:4175', reuseExistingServer: false },
  ],
})
