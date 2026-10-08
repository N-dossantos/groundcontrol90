// Convierte las entidades del store en los DTOs que devuelve el backend real
// (backend/src/main/java/com/ecommerce/dto/). api.js los mapea igual que en producción.

export const usuarioDTO = (u) => ({
  id: u.id,
  username: u.username,
  nombre: u.nombre,
  apellido: u.apellido,
  email: u.email,
  role: u.role,
  createdAt: u.createdAt,
  updatedAt: u.updatedAt,
})

export const categoriaDTO = (c) => ({
  id: c.id,
  nombre: c.nombre,
  descripcion: c.descripcion,
  createdAt: c.createdAt,
  updatedAt: c.updatedAt,
})

// stockTotal es derivado, igual que en ProductoDTO: el stock vive en cada talle.
export const productoDTO = (state, p) => {
  const variantes = state.variantes
    .filter((v) => v.productoId === p.id)
    .map(({ id, talle, stock, sku }) => ({ id, talle, stock, sku }))
  const categoria = state.categorias.find((c) => c.id === p.categoriaId)
  const owner = state.usuarios.find((u) => u.id === p.ownerUserId)
  return {
    id: p.id,
    name: p.name,
    description: p.description,
    price: p.price,
    club: p.club,
    liga: p.liga,
    temporada: p.temporada,
    tipo: p.tipo,
    variantes,
    stockTotal: variantes.reduce((suma, v) => suma + v.stock, 0),
    images: p.images,
    categoriaId: categoria ? categoria.id : null,
    categoriaNombre: categoria ? categoria.nombre : null,
    ownerUserId: p.ownerUserId,
    ownerUserNombre: owner ? owner.nombre : null,
    createdAt: p.createdAt,
    updatedAt: p.updatedAt,
  }
}

const nombreCompleto = (u) => (u ? `${u.nombre} ${u.apellido}` : null)

export const detalleDTO = (state, d) => {
  const vendedor = state.usuarios.find((u) => u.id === d.vendedorId)
  return {
    id: d.id,
    productoId: d.productoId,
    productoVarianteId: d.productoVarianteId,
    talle: d.talle,
    productoNombre: d.productoNombre,
    productoImagen: d.productoImagen,
    cantidad: d.cantidad,
    precioUnitario: d.precioUnitario,
    subtotal: d.precioUnitario * d.cantidad,
    vendedorId: vendedor ? vendedor.id : null,
    vendedorNombre: nombreCompleto(vendedor),
    vendedorEmail: vendedor ? vendedor.email : null,
    estadoItem: d.estadoItem,
  }
}

export const pedidoDTO = (state, p) => {
  const usuario = state.usuarios.find((u) => u.id === p.usuarioId)
  return {
    id: p.id,
    usuarioId: usuario ? usuario.id : null,
    usuarioNombre: usuario ? usuario.nombre : null,
    usuarioEmail: usuario ? usuario.email : null,
    items: state.detalles.filter((d) => d.pedidoId === p.id).map((d) => detalleDTO(state, d)),
    total: p.total,
    estado: p.estado,
    direccionEnvio: p.direccionEnvio,
    notas: p.notas,
    createdAt: p.createdAt,
    updatedAt: p.updatedAt,
  }
}

export const ventaDTO = (state, d) => {
  const pedido = state.pedidos.find((p) => p.id === d.pedidoId)
  const comprador = pedido ? state.usuarios.find((u) => u.id === pedido.usuarioId) : null
  return {
    detalleId: d.id,
    productoId: d.productoId,
    productoNombre: d.productoNombre,
    talle: d.talle,
    productoImagen: d.productoImagen,
    cantidad: d.cantidad,
    precioUnitario: d.precioUnitario,
    subtotal: d.precioUnitario * d.cantidad,
    estadoItem: d.estadoItem,
    pedidoId: pedido ? pedido.id : null,
    fechaPedido: pedido ? pedido.createdAt : null,
    compradorId: comprador ? comprador.id : null,
    compradorNombre: nombreCompleto(comprador),
    compradorEmail: comprador ? comprador.email : null,
    direccionEnvio: pedido ? pedido.direccionEnvio : null,
  }
}
