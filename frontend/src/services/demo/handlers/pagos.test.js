import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { llamar, usarStorageEnMemoria, TOKENS } from "../../../test/llamarDemo"

beforeEach(usarStorageEnMemoria)
afterEach(() => {
  vi.unstubAllGlobals()
})

const crearPedido = async () => {
  const res = await llamar("POST", "/pedidos", {
    body: { items: [{ productoVarianteId: 2, cantidad: 1 }], direccionEnvio: "Calle 1", notas: "" },
    token: TOKENS.testuser,
  })
  return res.body.id
}

describe("Mercado Pago simulado", () => {
  it("la preferencia lleva a la pantalla de resultado del propio sitio", async () => {
    const id = await crearPedido()

    const res = await llamar("POST", `/pagos/pedidos/${id}/preferencia`, { token: TOKENS.testuser })

    expect(res.status).toBe(200)
    expect(res.body).toEqual({ preferenceId: "demo-pref-3", initPoint: `/checkout/resultado?pedidoId=${id}` })
  })

  it("la consulta de estado aprueba el pago y confirma el pedido y sus items", async () => {
    const id = await crearPedido()
    await llamar("POST", `/pagos/pedidos/${id}/preferencia`, { token: TOKENS.testuser })

    const estado = await llamar("GET", `/pagos/pedidos/${id}/estado`, { token: TOKENS.testuser })

    expect(estado.body).toEqual({ estadoPago: "APPROVED", estadoPedido: "CONFIRMADO" })
    const pedido = await llamar("GET", `/pedidos/${id}`, { token: TOKENS.testuser })
    expect(pedido.body.estado).toBe("CONFIRMADO")
    expect(pedido.body.items[0].estadoItem).toBe("CONFIRMADO")
  })

  it("consultar el estado otra vez no cambia nada (idempotente, como el webhook)", async () => {
    const id = await crearPedido()
    await llamar("POST", `/pagos/pedidos/${id}/preferencia`, { token: TOKENS.testuser })
    await llamar("GET", `/pagos/pedidos/${id}/estado`, { token: TOKENS.testuser })

    const segunda = await llamar("GET", `/pagos/pedidos/${id}/estado`, { token: TOKENS.testuser })

    expect(segunda.body).toEqual({ estadoPago: "APPROVED", estadoPedido: "CONFIRMADO" })
  })

  it("no deja pagar el pedido de otro usuario", async () => {
    const res = await llamar("POST", "/pagos/pedidos/3/preferencia", { token: TOKENS.testuser })

    expect(res.status).toBe(403)
    expect(res.body.message).toBe("No tenés permiso sobre este pedido")
  })
})
