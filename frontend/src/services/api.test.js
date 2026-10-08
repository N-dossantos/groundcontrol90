import { describe, it, expect, vi, afterEach } from "vitest"
import { api } from "./api"

const respuesta = (status, body) =>
  new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  })

// api.js lee el token de localStorage/sessionStorage, que en Node no existen.
const sinSesion = () => {
  const vacio = { getItem: () => null, setItem: () => {}, removeItem: () => {} }
  vi.stubGlobal("localStorage", vacio)
  vi.stubGlobal("sessionStorage", vacio)
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe("request()", () => {
  it("acepta una respuesta 204 sin body como éxito", async () => {
    sinSesion()
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(respuesta(204)))

    await expect(api.deleteProduct(3)).resolves.toBe(true)
  })

  it("muestra el detalle (message) antes que el título genérico (error)", async () => {
    sinSesion()
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(respuesta(401, { error: "Unauthorized", message: "Credenciales inválidas" })),
    )

    await expect(api.login("x@test.com", "malpass")).rejects.toThrow("Credenciales inválidas")
  })

  it("usa error cuando el backend no manda message (AdminController)", async () => {
    sinSesion()
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(respuesta(409, { error: "El email ya está registrado" })))

    await expect(api.createUser({ email: "a@test.com" })).rejects.toThrow("El email ya está registrado")
  })
})

describe("api.validateToken", () => {
  it("valida con POST, el método que expone AuthController", async () => {
    sinSesion()
    const fetchMock = vi.fn().mockResolvedValue(
      respuesta(200, { id: 2, email: "user1@test.com", username: "user1", nombre: "User", apellido: "One", role: "USER" }),
    )
    vi.stubGlobal("fetch", fetchMock)

    await api.validateToken("tok")

    const [, config] = fetchMock.mock.calls[0]
    expect(config.method).toBe("POST")
    expect(config.headers.Authorization).toBe("Bearer tok")
  })
})
