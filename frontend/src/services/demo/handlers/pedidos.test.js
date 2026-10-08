import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { llamar, usarStorageEnMemoria, TOKENS } from "../../../test/llamarDemo"

beforeEach(usarStorageEnMemoria)
afterEach(() => {
  vi.unstubAllGlobals()
})

const stockDe = async (productoId, talle) => {
  const res = await llamar("GET", `/productos/${productoId}`)
  return res.body.variantes.find((v) => v.talle === talle).stock
}

const pedir = (items, token = TOKENS.testuser) =>
  llamar("POST", "/pedidos", { body: { items, direccionEnvio: "Calle 1", notas: "" }, token })

describe("checkout en la demo", () => {
  it("crea el pedido PENDIENTE y descuenta el stock del talle", async () => {
    const res = await pedir([{ productoVarianteId: 1, cantidad: 2 }]) // Boca S, stock 6

    expect(res.status).toBe(201)
    expect(res.body).toMatchObject({ id: 4, usuarioId: 3, estado: "PENDIENTE", total: 90000 })
    expect(res.body.items[0]).toMatchObject({
      productoVarianteId: 1,
      talle: "S",
      cantidad: 2,
      precioUnitario: 45000,
      subtotal: 90000,
      vendedorId: 2,
      vendedorNombre: "User One",
      estadoItem: "PENDIENTE",
    })
    expect(await stockDe(1, "S")).toBe(4)
  })

  it("rechaza un item sin stock suficiente sin descontar nada", async () => {
    const res = await pedir([{ productoVarianteId: 23, cantidad: 3 }]) // Retro XL, stock 2

    expect(res.status).toBe(400)
    expect(res.body.message).toBe(
      "Stock insuficiente para Camiseta Retro Argentina 1986 (talle XL). Disponible: 2, Solicitado: 3",
    )
    expect(await stockDe(6, "XL")).toBe(2)
  })

  it("rechaza entero un pedido que supera el stock repartido en dos líneas del mismo talle", async () => {
    const res = await pedir([
      { productoVarianteId: 1, cantidad: 1 },  // Boca S, alcanza
      { productoVarianteId: 23, cantidad: 1 }, // Retro XL: 2 → 1
      { productoVarianteId: 23, cantidad: 2 }, // Retro XL: pide 2, quedan 1
    ])

    expect(res.status).toBe(400)
    expect(await stockDe(1, "S")).toBe(6)
    expect(await stockDe(6, "XL")).toBe(2)
    expect((await llamar("GET", "/pedidos/mis-pedidos", { token: TOKENS.testuser })).body).toHaveLength(2)
  })

  it("devuelve 404 si la variante no existe", async () => {
    const res = await pedir([{ productoVarianteId: 999, cantidad: 1 }])

    expect(res.status).toBe(404)
  })

  it("rechaza un pedido sin items", async () => {
    const res = await pedir([])

    expect(res.status).toBe(400)
    expect(res.body.message).toBe("El pedido debe tener al menos un producto")
  })
})

describe("pedidos en la demo", () => {
  it("mis pedidos trae sólo los propios, del más nuevo al más viejo", async () => {
    const res = await llamar("GET", "/pedidos/mis-pedidos", { token: TOKENS.testuser })

    expect(res.body.map((p) => p.id)).toEqual([2, 1])
  })

  it("no deja ver el pedido de otro usuario", async () => {
    const res = await llamar("GET", "/pedidos/3", { token: TOKENS.testuser })

    expect(res.status).toBe(403)
  })

  it("el admin ve cualquier pedido", async () => {
    const res = await llamar("GET", "/pedidos/3", { token: TOKENS.admin })

    expect(res.status).toBe(200)
    expect(res.body.usuarioEmail).toBe("user1@test.com")
  })

  it("cancelar un pedido PENDIENTE devuelve el stock", async () => {
    const res = await llamar("PUT", "/pedidos/3/cancelar", { token: TOKENS.user1 })

    expect(res.status).toBe(200)
    expect(res.body.estado).toBe("CANCELADO_COMPRADOR")
    expect(res.body.items[0].estadoItem).toBe("CANCELADO_COMPRADOR")
    expect(await stockDe(5, "M")).toBe(17)
  })

  it("no deja cancelar un pedido que ya no está PENDIENTE", async () => {
    const res = await llamar("PUT", "/pedidos/2/cancelar", { token: TOKENS.testuser })

    expect(res.status).toBe(400)
    expect(res.body.message).toBe("Solo se pueden cancelar pedidos en estado PENDIENTE")
  })

  it("no deja cancelar el pedido de otro usuario", async () => {
    const res = await llamar("PUT", "/pedidos/3/cancelar", { token: TOKENS.testuser })

    expect(res.status).toBe(400)
    expect(await stockDe(5, "M")).toBe(15)
  })
})
