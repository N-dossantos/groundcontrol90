import { describe, it, expect } from "vitest"
import { validateNewPassword } from "./validators"

describe("validateNewPassword", () => {
  it("exige al menos 8 caracteres, el mínimo de RegisterRequest en el backend", () => {
    expect(validateNewPassword("1234567")).toBe(false)
    expect(validateNewPassword("clave123")).toBe(true)
  })

  it("rechaza una contraseña vacía o ausente", () => {
    expect(validateNewPassword("")).toBe(false)
    expect(validateNewPassword(undefined)).toBe(false)
  })
})
