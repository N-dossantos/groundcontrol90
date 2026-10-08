import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { llamar, usarStorageEnMemoria, TOKENS } from "../../../test/llamarDemo"

beforeEach(usarStorageEnMemoria)
afterEach(() => {
  vi.unstubAllGlobals()
})

const nuevo = {
  username: "vendedor2",
  email: "vendedor2@test.com",
  password: "clave123",
  nombre: "Vende",
  apellido: "Dor",
  role: "USER",
}

describe("administración de usuarios en la demo", () => {
  it("exige rol ADMIN", async () => {
    const res = await llamar("GET", "/admin/usuarios", { token: TOKENS.user1 })

    expect(res.status).toBe(403)
  })

  it("lista los usuarios sin contraseñas", async () => {
    const res = await llamar("GET", "/admin/usuarios", { token: TOKENS.admin })

    expect(res.body).toHaveLength(3)
    expect(res.body.every((u) => !("password" in u))).toBe(true)
  })

  it("cuenta usuarios por rol", async () => {
    const res = await llamar("GET", "/admin/usuarios/estadisticas", { token: TOKENS.admin })

    expect(res.body).toEqual({ totalUsuarios: 3, adminUsuarios: 1, userUsuarios: 2 })
  })

  it("crea un usuario que después puede loguearse", async () => {
    const res = await llamar("POST", "/admin/usuarios", { body: nuevo, token: TOKENS.admin })

    expect(res.status).toBe(201)
    expect(res.body).toMatchObject({ id: 4, username: "vendedor2", role: "USER" })
    const login = await llamar("POST", "/auth/login", {
      body: { emailOrUsername: "vendedor2", password: "clave123" },
    })
    expect(login.status).toBe(200)
  })

  it("rechaza un email repetido con 409", async () => {
    const res = await llamar("POST", "/admin/usuarios", {
      body: { ...nuevo, email: "user1@test.com" },
      token: TOKENS.admin,
    })

    expect(res.status).toBe(409)
    expect(res.body.message).toBe("El email ya está registrado")
  })

  it("actualiza los datos y cambia la contraseña sólo si viene", async () => {
    const res = await llamar("PUT", "/admin/usuarios/2", {
      body: { username: "user1", email: "user1@test.com", nombre: "Usuario", apellido: "Uno", role: "USER" },
      token: TOKENS.admin,
    })

    expect(res.body.nombre).toBe("Usuario")
    const login = await llamar("POST", "/auth/login", {
      body: { emailOrUsername: "user1@test.com", password: "user123" },
    })
    expect(login.status).toBe(200)
  })

  it("cambia el rol y rechaza uno inválido", async () => {
    const ok = await llamar("PUT", "/admin/usuarios/2/rol", { body: { role: "ADMIN" }, token: TOKENS.admin })
    const mal = await llamar("PUT", "/admin/usuarios/2/rol", { body: { role: "JEFE" }, token: TOKENS.admin })

    expect(ok.body.role).toBe("ADMIN")
    expect(mal.status).toBe(400)
    expect(mal.body.message).toBe("Rol inválido. Debe ser USER o ADMIN")
  })

  it("elimina un usuario y su token deja de valer", async () => {
    const res = await llamar("DELETE", "/admin/usuarios/3", { token: TOKENS.admin })

    expect(res.status).toBe(200)
    expect(res.body.message).toBe("Usuario eliminado exitosamente")
    expect((await llamar("POST", "/auth/validate", { token: TOKENS.testuser })).status).toBe(401)
  })

  it("devuelve 404 al tocar un usuario inexistente", async () => {
    const res = await llamar("DELETE", "/admin/usuarios/99", { token: TOKENS.admin })

    expect(res.status).toBe(404)
  })
})

describe("reporte de ventas en la demo", () => {
  it("por defecto cubre los últimos 30 días y sólo cuenta pedidos pagados", async () => {
    const res = await llamar("GET", "/pedidos/admin/reportes?", { token: TOKENS.admin })

    expect(res.body).toMatchObject({ ventasTotales: 123000, ticketPromedio: 61500, cantidadPedidos: 2 })
    expect(res.body.productosMasVendidos).toHaveLength(2)
    expect(res.body.productosMasVendidos.map((p) => p.nombre).sort()).toEqual([
      "Camiseta Titular Boca Juniors 2026",
      "Camiseta Titular Real Madrid 2026",
    ])
  })

  it("respeta el rango de fechas pedido", async () => {
    const res = await llamar("GET", "/pedidos/admin/reportes?desde=2000-01-01T00:00:00&hasta=2000-12-31T23:59:59", {
      token: TOKENS.admin,
    })

    expect(res.body).toEqual({ ventasTotales: 0, ticketPromedio: 0, cantidadPedidos: 0, productosMasVendidos: [] })
  })

  it("suma una compra recién pagada", async () => {
    const pedido = await llamar("POST", "/pedidos", {
      body: { items: [{ productoVarianteId: 2, cantidad: 2 }], direccionEnvio: "Calle 1", notas: "" },
      token: TOKENS.testuser,
    })
    await llamar("POST", `/pagos/pedidos/${pedido.body.id}/preferencia`, { token: TOKENS.testuser })
    await llamar("GET", `/pagos/pedidos/${pedido.body.id}/estado`, { token: TOKENS.testuser })

    const res = await llamar("GET", "/pedidos/admin/reportes?", { token: TOKENS.admin })

    expect(res.body.cantidadPedidos).toBe(3)
    expect(res.body.productosMasVendidos[0]).toEqual({ nombre: "Camiseta Titular Boca Juniors 2026", cantidadVendida: 3 })
  })

  it("exige rol ADMIN", async () => {
    const res = await llamar("GET", "/pedidos/admin/reportes?", { token: TOKENS.user1 })

    expect(res.status).toBe(403)
  })
})
