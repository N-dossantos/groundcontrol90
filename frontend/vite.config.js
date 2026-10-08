import { defineConfig } from "vite"
import react from "@vitejs/plugin-react"

export default defineConfig({
  plugins: [react()],
  // Ver services/api.js. Tiene que ser un literal en el build: así Rollup elimina el código
  // de la demo cuando VITE_DEMO_MODE no está seteada (build del VPS). Con import.meta.env
  // no alcanza: Vite 4 emite igual el chunk del import() dinámico.
  define: {
    __DEMO_MODE__: JSON.stringify(process.env.VITE_DEMO_MODE === "true"),
  },
  server: {
    port: 5173,
    // En producción Caddy rutea /api/* al backend en el mismo dominio. En dev el
    // proxy de Vite cumple ese rol, para que la ruta relativa de api.js funcione igual.
    proxy: {
      "/api": "http://localhost:8081",
    },
  },
})
