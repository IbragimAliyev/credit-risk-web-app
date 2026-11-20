import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  // Read VITE_* variables from the project-root .env shared with the backend.
  envDir: '..',
  server: {
    port: 3000,
    strictPort: true,
  },
  preview: {
    port: 3000,
  },
});
