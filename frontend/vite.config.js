import os from 'node:os';
import { defineConfig } from 'vite';

const isWsl = Boolean(process.env.WSL_DISTRO_NAME)
    || os.release().toLowerCase().includes('microsoft');

export default defineConfig({
    server: {
        host: '0.0.0.0',
        port: 5174,
        strictPort: true,
        watch: isWsl
            ? {
                usePolling: true,
                interval: 300
            }
            : undefined,
        proxy: {
            '/api': {
                target: 'http://127.0.0.1:8081',
                changeOrigin: true
            }
        }
    }
});
