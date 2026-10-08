import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { llamar, usarStorageEnMemoria, TOKENS } from "../../../test/llamarDemo"

beforeEach(usarStorageEnMemoria)
afterEach(() => {
  vi.unstubAllGlobals()
})

const mover = (detalleId, estado, token = TOKENS.user1) =>
  llamar("PUT", `/ventas/${detalleId}/estado?estado=${estado}`, { token })

describe("ventas del vendedor en la demo", () => {
  it("lista sólo las ventas propias con la forma de VentaDTO, de la más nueva a la más vieja", async () => {
    const res = await llamar("GET", "/ventas/mis-ventas", { token: TOKENS.user1 })

    expect(res.body.map((v) => v.detalleId)).toEqual([2, 1])
    expect(res.body[0]).toMatchObject({
      pedidoId: 2,
      productoNombre: "Camiseta Titular Real Madrid 2026",
      talle: "L",
      estadoItem: "CONFIRMADO",
      compradorId: 3,
      compradorNombre: "Test User",
      compradorEmail: "test@test.com",
      direccionEnvio: "Av. Corrientes 1234, CABA",
    })
  })

  it("cuenta las ventas por estado", async () => {
    const res = await llamar("GET", "/ventas/estadisticas", { token: TOKENS.user1 })

    expect(res.body).toEqual({
      totalVentas: 2,
      ventasPendientes: 0,
      ventasConfirmadas: 1,
      ventasEnviadas: 0,
      ventasEntregadas: 1,
      ventasCanceladas: 0,
    })
  })

  it("avanza un item por las transiciones válidas y deriva el estado del pedido", async () => {
    expect((await mover(2, "PREPARANDO")).status).toBe(200)
    const enviado = await mover(2, "ENVIADO")

    expect(enviado.body.estadoItem).toBe("ENVIADO")
    const pedido = await llamar("GET", "/pedidos/2", { token: TOKENS.testuser })
    expect(pedido.body.estado).toBe("ENVIADO")
  })

  it("rechaza una transición inválida", async () => {
    const res = await mover(2, "ENTREGADO")

    expect(res.status).toBe(400)
    expect(res.body.message).toBe("Transición de estado inválida desde CONFIRMADO")
  })

  it("al cancelar el vendedor devuelve el stock y el pedido queda cancelado", async () => {
    const res = await mover(3, "CANCELADO_VENDEDOR", TOKENS.testuser)

    expect(res.body.estadoItem).toBe("CANCELADO_VENDEDOR")
    const producto = await llamar("GET", "/productos/5")
    expect(producto.body.variantes.find((v) => v.talle === "M").stock).toBe(17)
    const pedido = await llamar("GET", "/pedidos/3", { token: TOKENS.user1 })
    expect(pedido.body.estado).toBe("CANCELADO_COMPRADOR")
  })

  it("no deja mover un item cancelado", async () => {
    await mover(3, "CANCELADO_VENDEDOR", TOKENS.testuser)

    const res = await mover(3, "CONFIRMADO", TOKENS.testuser)

    expect(res.status).toBe(400)
    expect(res.body.message).toBe("No se puede cambiar el estado de un item cancelado o devuelto")
  })

  it("no deja mover el item de otro vendedor", async () => {
    const res = await mover(3, "CONFIRMADO", TOKENS.user1)

    expect(res.status).toBe(400)
    expect(res.body.message).toBe("No tienes permiso para modificar este item")
  })

  it("rechaza un estado que no existe", async () => {
    const res = await mover(2, "VOLANDO")

    expect(res.status).toBe(400)
    expect(res.body.message).toBe("Estado inválido: VOLANDO")
  })
})
