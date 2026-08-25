import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '../tflite_web_api_client': '@tensorflow/tfjs-tflite/wasm/tflite_web_api_client.js',
      './tflite_web_api_client': '@tensorflow/tfjs-tflite/wasm/tflite_web_api_client.js'
    }
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:5000',
        changeOrigin: true
      },
      '/uploads': {
        target: 'http://127.0.0.1:5000',
        changeOrigin: true
      }
    }
  }
})
