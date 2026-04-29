import path from "path"
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
  ],
  resolve: {
    alias: {
      "@/modules": path.resolve(__dirname, "./src/modules"),
      "@/shared": path.resolve(__dirname, "./src/shared"),
      "@/app": path.resolve(__dirname, "./src/app"),
      "@": path.resolve(__dirname, "./src"),
    },
  },
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
  },
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
      '/v3': 'http://localhost:8080',
      '/swagger-ui': 'http://localhost:8080',
      '/oauth2': 'http://localhost:8080',
      '/auth': 'http://localhost:8080',
    },
  },
})
