import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { llamar, usarStorageEnMemoria, TOKENS } from "../../../test/llamarDemo"

beforeEach(usarStorageEnMemoria)
afterEach(() => {
  vi.unstubAllGlobals()
})

const registro = {
  username: "nuevo",
  email: "nuevo@test.com",
  password: "clave-larga",
  nombre: "Nueva",
  apellido: "Cuenta",
  aceptaTerminos: true,
}

describe("auth de la demo", () => {
  it("loguea por email y devuelve token y UsuarioDTO sin password", async () => {
    const res = await llamar("POST", "/auth/login", {
      body: { emailOrUsername: "user1@test.com", password: "user123" },
    })

    expect(res.status).toBe(200)
    expect(res.body.token).toBe(TOKENS.user1)
    expect(res.body.user).toEqual({
      id: 2,
      username: "user1",
      nombre: "User",
      apellido: "One",
      email: "user1@test.com",
      role: "USER",
      createdAt: expect.any(String),
      updatedAt: null,
    })
  })

  it("loguea también por username", async () => {
    const res = await llamar("POST", "/auth/login", { body: { emailOrUsername: "admin", password: "admin123" } })

    expect(res.body.user.role).toBe("ADMIN")
  })

  it("rechaza una contraseña incorrecta con 401", async () => {
    const res = await llamar("POST", "/auth/login", {
      body: { emailOrUsername: "user1@test.com", password: "otra" },
    })

    expect(res.status).toBe(401)
    expect(res.body).toEqual({ status: 401, error: "Unauthorized", message: "Credenciales inválidas" })
  })

  it("registra un usuario USER que después puede loguearse", async () => {
    const res = await llamar("POST", "/auth/register", { body: registro })

    expect(res.status).toBe(200)
    expect(res.body.user).toMatchObject({ id: 4, role: "USER", email: "nuevo@test.com" })
    const login = await llamar("POST", "/auth/login", {
      body: { emailOrUsername: "nuevo@test.com", password: "clave-larga" },
    })
    expect(login.status).toBe(200)
  })

  it("rechaza un email ya registrado con 409", async () => {
    const res = await llamar("POST", "/auth/register", { body: { ...registro, email: "user1@test.com" } })

    expect(res.status).toBe(409)
    expect(res.body.message).toBe("El email 'user1@test.com' ya existe")
  })

  it("exige aceptar los términos", async () => {
    const res = await llamar("POST", "/auth/register", { body: { ...registro, aceptaTerminos: false } })

    expect(res.status).toBe(400)
  })

  it("exige una contraseña de al menos 8 caracteres", async () => {
    const res = await llamar("POST", "/auth/register", { body: { ...registro, password: "corta" } })

    expect(res.status).toBe(400)
  })

  it("validate devuelve el usuario del token", async () => {
    const res = await llamar("POST", "/auth/validate", { token: TOKENS.testuser })

    expect(res.status).toBe(200)
    expect(res.body).toMatchObject({ id: 3, username: "testuser" })
  })

  it("validate rechaza con 401 el token de un usuario que ya no existe", async () => {
    const res = await llamar("POST", "/auth/validate", { token: "demo-token-99" })

    expect(res.status).toBe(401)
  })

  it("validate rechaza con 401 un pedido sin token", async () => {
    const res = await llamar("POST", "/auth/validate")

    expect(res.status).toBe(401)
  })
})
