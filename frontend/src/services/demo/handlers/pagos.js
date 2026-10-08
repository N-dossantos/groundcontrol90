import { nextId } from "../store"
import { fechaLocal } from "../fechas"
import { buscarPedido } from "./pedidos"
import { ok, demoError, prohibido } from "../respuestas"

const pedidoPropio = (state, pedidoId, usuario) => {
  const pedido = buscarPedido(state, pedidoId)
  if (pedido.usuarioId !== usuario.id) throw prohibido("No tenés permiso sobre este pedido")
  return pedido
}

// En vez del checkout hosteado de Mercado Pago, la "pasarela" es la pantalla de resultado
// del propio sitio: nadie llega a mercadopago.com ni puede creer que pagó de verdad.
export const crearPreferencia = ({ state, params, usuario }) => {
  const pedido = pedidoPropio(state, params.pedidoId, usuario)
  const id = nextId(state, "pagos")
  const pago = { id, pedidoId: pedido.id, preferenceId: `demo-pref-${id}`, estado: "PENDING", createdAt: fechaLocal() }
  state.pagos.push(pago)
  return ok({ preferenceId: pago.preferenceId, initPoint: `/checkout/resultado?pedidoId=${pedido.id}` })
}

// Hace las veces del webhook: la primera consulta acredita el pago y confirma el pedido,
// con la misma lógica idempotente que PedidoService.confirmarPago.
export const estado = ({ state, params, usuario }) => {
  const pedido = pedidoPropio(state, params.pedidoId, usuario)
  const pago = state.pagos.filter((p) => p.pedidoId === pedido.id).sort((a, b) => b.id - a.id)[0]
  if (!pago) throw demoError(500, "Internal Server Error", "Ocurrió un error inesperado")

  if (pago.estado === "PENDING" && pedido.estado === "PENDIENTE") {
    pago.estado = "APPROVED"
    for (const detalle of state.detalles.filter((d) => d.pedidoId === pedido.id && d.estadoItem === "PENDIENTE")) {
      detalle.estadoItem = "CONFIRMADO"
    }
    pedido.estado = "CONFIRMADO"
    pedido.updatedAt = fechaLocal()
  }
  return ok({ estadoPago: pago.estado, estadoPedido: pedido.estado })
}
