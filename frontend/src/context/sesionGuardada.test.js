import { describe, it, expect, vi, afterEach } from "vitest"
import { leerSesionGuardada } from "./sesionGuardada"

const storageCon = (datos) => ({ getItem: (k) => datos[k] ?? null })

afterEach(() => {
  vi.unstubAllGlobals()
})

describe("leerSesionGuardada", () => {
  it("prefiere la sesión de localStorage (recordarme)", () => {
    vi.stubGlobal("localStorage", storageCon({ auth_token: "a", auth_user: "{}" }))
    vi.stubGlobal("sessionStorage", storageCon({ auth_token: "b", auth_user: "{}" }))

    expect(leerSesionGuardada()).toEqual({ token: "a", user: "{}", storageType: "localStorage" })
  })

  it("cae en sessionStorage si localStorage no tiene sesión", () => {
    vi.stubGlobal("localStorage", storageCon({}))
    vi.stubGlobal("sessionStorage", storageCon({ auth_token: "b", auth_user: "{}" }))

    expect(leerSesionGuardada()).toEqual({ token: "b", user: "{}", storageType: "sessionStorage" })
  })

  it("no cuenta como sesión un token sin usuario", () => {
    vi.stubGlobal("localStorage", storageCon({ auth_token: "a" }))
    vi.stubGlobal("sessionStorage", storageCon({}))

    expect(leerSesionGuardada()).toBeNull()
  })
})
