import { fechaLocal } from "../fechas"
import { ventaDTO } from "../dto"
import { devolverStock } from "./pedidos"
import { ok, pedidoInvalido } from "../respuestas"

const ESTADOS = [
  "PENDIENTE", "CONFIRMADO", "PREPARANDO", "ENVIADO", "EN_TRANSITO", "ENTREGADO", "CANCELADO",
  "CANCELADO_COMPRADOR", "CANCELADO_VENDEDOR", "DEVOLUCION_SOLICITADA", "DEVUELTO", "PAGO_RECHAZADO",
]

// Copia de PedidoService.validarTransicionEstado. Un estado que no figura acá ni en
// FINALES no restringe la transición, igual que en el backend.
const TRANSICIONES = {
  PENDIENTE: ["CONFIRMADO", "CANCELADO_VENDEDOR", "CANCELADO_COMPRADOR"],
  CONFIRMADO: ["PREPARANDO", "CANCELADO_VENDEDOR"],
  PREPARANDO: ["ENVIADO"],
  ENVIADO: ["EN_TRANSITO", "ENTREGADO"],
  EN_TRANSITO: ["ENTREGADO"],
  ENTREGADO: ["DEVOLUCION_SOLICITADA"],
  DEVOLUCION_SOLICITADA: ["DEVUELTO"],
}
const FINALES = ["CANCELADO_COMPRADOR", "CANCELADO_VENDEDOR", "DEVUELTO"]

const validarTransicion = (actual, nuevo) => {
  if (FINALES.includes(actual)) {
    throw pedidoInvalido("No se puede cambiar el estado de un item cancelado o devuelto")
  }
  const permitidos = TRANSICIONES[actual]
  if (permitidos && !permitidos.includes(nuevo)) {
    throw pedidoInvalido(`Transición de estado inválida desde ${actual}`)
  }
}

// Copia de PedidoService.actualizarEstadoPedidoGeneral: el estado del pedido se deriva de
// los de sus items, nunca se setea a mano.
const estadoDerivado = (items) => {
  const cuantos = (...estados) => items.filter((i) => estados.includes(i.estadoItem)).length
  if (cuantos("CANCELADO_COMPRADOR", "CANCELADO_VENDEDOR") > 0) return "CANCELADO_COMPRADOR"
  if (cuantos("ENTREGADO") === items.length) return "ENTREGADO"
  if (cuantos("EN_TRANSITO", "ENVIADO") > 0) return "ENVIADO"
  if (cuantos("CONFIRMADO", "PREPARANDO") > 0) return "CONFIRMADO"
  if (cuantos("PENDIENTE") === items.length) return "PENDIENTE"
  return "CONFIRMADO"
}

const fechaDelPedido = (state, d) => state.pedidos.find((p) => p.id === d.pedidoId)?.createdAt || ""

const ventasDe = (state, usuario) =>
  state.detalles
    .filter((d) => d.vendedorId === usuario.id)
    .sort((a, b) => fechaDelPedido(state, b).localeCompare(fechaDelPedido(state, a)) || b.id - a.id)

export const misVentas = ({ state, usuario }) => ok(ventasDe(state, usuario).map((d) => ventaDTO(state, d)))

export const estadisticas = ({ state, usuario }) => {
  const ventas = ventasDe(state, usuario)
  const cuantas = (...estados) => ventas.filter((v) => estados.includes(v.estadoItem)).length
  return ok({
    totalVentas: ventas.length,
    ventasPendientes: cuantas("PENDIENTE"),
    ventasConfirmadas: cuantas("CONFIRMADO"),
    ventasEnviadas: cuantas("ENVIADO", "EN_TRANSITO"),
    ventasEntregadas: cuantas("ENTREGADO"),
    ventasCanceladas: cuantas("CANCELADO", "CANCELADO_COMPRADOR", "CANCELADO_VENDEDOR"),
  })
}

export const actualizarEstado = ({ state, params, query, usuario }) => {
  const nuevo = (query.estado || "").toUpperCase()
  if (!ESTADOS.includes(nuevo)) throw pedidoInvalido(`Estado inválido: ${query.estado}`)

  const detalle = state.detalles.find((d) => d.id === Number(params.detalleId))
  if (!detalle) throw pedidoInvalido("Detalle de pedido no encontrado")
  if (detalle.vendedorId !== usuario.id) throw pedidoInvalido("No tienes permiso para modificar este item")

  validarTransicion(detalle.estadoItem, nuevo)
  if (nuevo === "CANCELADO_VENDEDOR") devolverStock(state, detalle)
  detalle.estadoItem = nuevo

  const pedido = state.pedidos.find((p) => p.id === detalle.pedidoId)
  const derivado = estadoDerivado(state.detalles.filter((d) => d.pedidoId === pedido.id))
  if (pedido.estado !== derivado) {
    pedido.estado = derivado
    pedido.updatedAt = fechaLocal()
  }
  return ok(ventaDTO(state, detalle))
}
