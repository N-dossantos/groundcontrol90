import { nextId } from "../store"
import { fechaLocal, restarDias } from "../fechas"
import { usuarioDTO } from "../dto"
import { ok, creado, conflicto, noEncontrado, pedidoInvalido } from "../respuestas"

const ROLES = ["USER", "ADMIN"]

// Copia de PedidoService.ESTADOS_NO_VENTA: pedidos que no representan plata facturada.
const ESTADOS_NO_VENTA = ["PENDIENTE", "PAGO_RECHAZADO", "CANCELADO", "CANCELADO_COMPRADOR", "CANCELADO_VENDEDOR", "DEVUELTO"]

const buscarUsuario = (state, id) => {
  const usuario = state.usuarios.find((u) => u.id === Number(id))
  if (!usuario) throw noEncontrado(`Usuario con ID ${id} no encontrado`)
  return usuario
}

export const listarUsuarios = ({ state }) => ok(state.usuarios.map(usuarioDTO))

export const estadisticasUsuarios = ({ state }) =>
  ok({
    totalUsuarios: state.usuarios.length,
    adminUsuarios: state.usuarios.filter((u) => u.role === "ADMIN").length,
    userUsuarios: state.usuarios.filter((u) => u.role === "USER").length,
  })

export const crearUsuario = ({ state, body }) => {
  if (state.usuarios.some((u) => u.email === body.email)) throw conflicto("El email ya está registrado")
  if (state.usuarios.some((u) => u.username === body.username)) throw conflicto("El nombre de usuario ya está registrado")
  const usuario = {
    id: nextId(state, "usuarios"),
    username: body.username,
    email: body.email,
    password: body.password,
    nombre: body.nombre,
    apellido: body.apellido,
    role: body.role || "USER",
    createdAt: fechaLocal(),
    updatedAt: null,
  }
  state.usuarios.push(usuario)
  return creado(usuarioDTO(usuario))
}

// Igual que AdminController.updateUsuario: sólo se pisan los campos que vienen.
export const actualizarUsuario = ({ state, params, body }) => {
  const usuario = buscarUsuario(state, params.id)
  if (body.email && body.email !== usuario.email) {
    if (state.usuarios.some((u) => u.email === body.email)) throw conflicto("El email ya está registrado")
    usuario.email = body.email
  }
  if (body.username && body.username !== usuario.username) {
    if (state.usuarios.some((u) => u.username === body.username)) throw conflicto("El nombre de usuario ya está registrado")
    usuario.username = body.username
  }
  if (body.nombre) usuario.nombre = body.nombre
  if (body.apellido) usuario.apellido = body.apellido
  if (body.password) usuario.password = body.password
  if (body.role) usuario.role = body.role
  usuario.updatedAt = fechaLocal()
  return ok(usuarioDTO(usuario))
}

export const cambiarRol = ({ state, params, body }) => {
  const usuario = buscarUsuario(state, params.id)
  const rol = (body?.role || "").toUpperCase()
  if (!ROLES.includes(rol)) throw pedidoInvalido("Rol inválido. Debe ser USER o ADMIN")
  usuario.role = rol
  usuario.updatedAt = fechaLocal()
  return ok(usuarioDTO(usuario))
}

export const eliminarUsuario = ({ state, params }) => {
  const usuario = buscarUsuario(state, params.id)
  state.usuarios = state.usuarios.filter((u) => u.id !== usuario.id)
  return ok({ message: "Usuario eliminado exitosamente" })
}

// Las fechas de la demo son strings "YYYY-MM-DDTHH:mm:ss": comparan bien como texto.
export const reporteVentas = ({ state, query }) => {
  const ahora = new Date()
  const desde = query.desde || fechaLocal(restarDias(ahora, 30))
  const hasta = query.hasta || fechaLocal(ahora)

  const pagados = state.pedidos.filter(
    (p) => p.createdAt >= desde && p.createdAt <= hasta && !ESTADOS_NO_VENTA.includes(p.estado),
  )
  const ventasTotales = pagados.reduce((suma, p) => suma + p.total, 0)
  const ticketPromedio = pagados.length === 0 ? 0 : Math.round((ventasTotales / pagados.length) * 100) / 100

  const vendidos = new Map()
  for (const d of state.detalles.filter((d) => pagados.some((p) => p.id === d.pedidoId))) {
    vendidos.set(d.productoNombre, (vendidos.get(d.productoNombre) || 0) + d.cantidad)
  }
  const productosMasVendidos = [...vendidos]
    .map(([nombre, cantidadVendida]) => ({ nombre, cantidadVendida }))
    .sort((a, b) => b.cantidadVendida - a.cantidadVendida)

  return ok({ ventasTotales, ticketPromedio, cantidadPedidos: pagados.length, productosMasVendidos })
}
