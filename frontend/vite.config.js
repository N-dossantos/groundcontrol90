import { defineConfig } from "vite"
import react from "@vitejs/plugin-react"

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // En producción Caddy rutea /api/* al backend en el mismo dominio. En dev el
    // proxy de Vite cumple ese rol, para que la ruta relativa de api.js funcione igual.
    proxy: {
      "/api": "http://localhost:8081",
    },
  },
})
