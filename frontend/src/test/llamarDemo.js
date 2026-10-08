import { vi } from "vitest"
import { responder } from "../services/demo/router"
import { crearStorageEnMemoria } from "./storageEnMemoria"

export const TOKENS = { admin: "demo-token-1", user1: "demo-token-2", testuser: "demo-token-3" }

export const usarStorageEnMemoria = () => {
  vi.stubGlobal("localStorage", crearStorageEnMemoria())
}

// Llama al router con el mismo `config` que armaría request() en api.js.
export const llamar = async (metodo, endpoint, { body, token } = {}) => {
  const res = await responder(endpoint, {
    method: metodo,
    headers: { "Content-Type": "application/json", ...(token && { Authorization: `Bearer ${token}` }) },
    ...(body !== undefined && { body: JSON.stringify(body) }),
  })
  return { status: res.status, body: res.status === 204 ? null : await res.json() }
}
