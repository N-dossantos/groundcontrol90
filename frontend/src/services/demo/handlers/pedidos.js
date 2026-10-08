import { nextId } from "../store"
import { fechaLocal } from "../fechas"
import { pedidoDTO } from "../dto"
import { ok, creado, demoError, noEncontrado, prohibido, pedidoInvalido } from "../respuestas"

export const buscarPedido = (state, id) => {
  const pedido = state.pedidos.find((p) => p.id === Number(id))
  if (!pedido) throw noEncontrado(`Pedido con ID ${id} no encontrado`)
  return pedido
}

// Si la variante ya no existe (se borró el producto), no hay a dónde devolver el stock.
export const devolverStock = (state, detalle) => {
  const variante = state.variantes.find((v) => v.id === detalle.productoVarianteId)
  if (variante) variante.stock += detalle.cantidad
}

const masNuevoPrimero = (a, b) => b.createdAt.localeCompare(a.createdAt) || b.id - a.id

export const misPedidos = ({ state, usuario }) =>
  ok(
    state.pedidos
      .filter((p) => p.usuarioId === usuario.id)
      .sort(masNuevoPrimero)
      .map((p) => pedidoDTO(state, p)),
  )

export const obtener = ({ state, params, usuario }) => {
  const pedido = buscarPedido(state, params.id)
  if (pedido.usuarioId !== usuario.id && usuario.role !== "ADMIN") {
    throw prohibido("No tienes permiso para ver este pedido")
  }
  return ok(pedidoDTO(state, pedido))
}

// Igual que PedidoService.crearPedido: valida y descuenta item por item. Si un item no
// tiene stock se lanza a mitad de camino, y el router descarta lo ya descontado.
export const crear = ({ state, body, usuario }) => {
  const items = body?.items || []
  if (items.length === 0) throw pedidoInvalido("El pedido debe tener al menos un producto")

  const pedido = {
    id: nextId(state, "pedidos"),
    usuarioId: usuario.id,
    estado: "PENDIENTE",
    direccionEnvio: body.direccionEnvio,
    notas: body.notas,
    total: 0,
    createdAt: fechaLocal(),
    updatedAt: null,
  }

  for (const item of items) {
    const variante = state.variantes.find((v) => v.id === Number(item.productoVarianteId))
    if (!variante) throw noEncontrado(`Producto con ID ${item.productoVarianteId} no encontrado`)
    const producto = state.productos.find((p) => p.id === variante.productoId)
    if (variante.stock < item.cantidad) {
      throw demoError(
        400,
        "Stock Insuficiente",
        `Stock insuficiente para ${producto.name} (talle ${variante.talle}). Disponible: ${variante.stock}, Solicitado: ${item.cantidad}`,
      )
    }
    state.detalles.push({
      id: nextId(state, "detalles"),
      pedidoId: pedido.id,
      productoId: producto.id,
      productoVarianteId: variante.id,
      talle: variante.talle,
      productoNombre: producto.name,
      productoImagen: producto.images?.[0] ?? null,
      cantidad: item.cantidad,
      precioUnitario: producto.price,
      vendedorId: producto.ownerUserId,
      estadoItem: "PENDIENTE",
    })
    variante.stock -= item.cantidad
    pedido.total += producto.price * item.cantidad
  }

  state.pedidos.push(pedido)
  return creado(pedidoDTO(state, pedido))
}

export const cancelar = ({ state, params, usuario }) => {
  const pedido = buscarPedido(state, params.id)
  if (pedido.usuarioId !== usuario.id) throw pedidoInvalido("No tienes permiso para cancelar este pedido")
  if (pedido.estado !== "PENDIENTE") throw pedidoInvalido("Solo se pueden cancelar pedidos en estado PENDIENTE")

  for (const detalle of state.detalles.filter((d) => d.pedidoId === pedido.id && d.estadoItem === "PENDIENTE")) {
    devolverStock(state, detalle)
    detalle.estadoItem = "CANCELADO_COMPRADOR"
  }
  pedido.estado = "CANCELADO_COMPRADOR"
  pedido.updatedAt = fechaLocal()
  return ok(pedidoDTO(state, pedido))
}
