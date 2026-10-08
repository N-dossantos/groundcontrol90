import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { llamar, usarStorageEnMemoria } from "../../test/llamarDemo"

beforeEach(usarStorageEnMemoria)
afterEach(() => {
  vi.unstubAllGlobals()
})

describe("router de la demo", () => {
  it("responde 501 con un mensaje claro a un endpoint que la demo no simula", async () => {
    const res = await llamar("GET", "/direcciones", { token: "demo-token-2" })

    expect(res.status).toBe(501)
    expect(res.body.message).toBe("GET /direcciones no está disponible en el modo demo")
  })

  it("responde 501 si el método no coincide aunque la ruta exista", async () => {
    const res = await llamar("GET", "/auth/login")

    expect(res.status).toBe(501)
  })
})
