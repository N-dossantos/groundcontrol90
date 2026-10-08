import { describe, it, expect } from "vitest"
import { authReducer, authInitialState } from "./authReducer"

const restaurando = { ...authInitialState, restoring: true }

describe("authReducer", () => {
  it("arranca sin restauración en curso", () => {
    expect(authInitialState.restoring).toBe(false)
  })

  it("AUTH_RESTORE termina la restauración con el usuario logueado", () => {
    const estado = authReducer(restaurando, {
      type: "AUTH_RESTORE",
      payload: { token: "t", user: { id: 2 }, storageType: "localStorage" },
    })

    expect(estado).toMatchObject({ restoring: false, isAuthenticated: true, token: "t", user: { id: 2 } })
  })

  it("AUTH_RESTORE_FAILED termina la restauración sin sesión", () => {
    const estado = authReducer(restaurando, { type: "AUTH_RESTORE_FAILED" })

    expect(estado).toEqual(authInitialState)
  })
})
