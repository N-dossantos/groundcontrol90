import { nextId } from "../store"
import { fechaLocal } from "../fechas"
import { productoDTO } from "../dto"
import { ok, creado, sinContenido, demoError, noEncontrado, prohibido } from "../respuestas"

const buscarProducto = (state, id) => {
  const producto = state.productos.find((p) => p.id === Number(id))
  if (!producto) throw noEncontrado(`Producto con ID ${id} no encontrado`)
  return producto
}

// Mismo criterio que ProductoService.verificarPropiedad: el dueño o un admin.
const verificarPropiedad = (producto, usuario) => {
  if (usuario.role !== "ADMIN" && producto.ownerUserId !== usuario.id) {
    throw prohibido("No tenés permiso sobre este producto")
  }
}

// El backend real descarta categoriaId (la entidad Producto no lo mapea). La demo
// implementa el contrato que arma buildProductPayload en api.js.
const aplicarCampos = (producto, datos) => {
  producto.name = datos.name
  producto.description = datos.description
  producto.price = Number(datos.price)
  producto.club = datos.club
  producto.liga = datos.liga
  producto.temporada = datos.temporada
  producto.tipo = datos.tipo
  producto.images = datos.images || []
  producto.categoriaId = datos.categoriaId ?? null
}

// Igual que ProductoService.reemplazarVariantes: se aparean por talle y se actualizan en
// su lugar, porque el id de la variante es lo que referencian los pedidos y los carritos.
const reemplazarVariantes = (state, producto, deseadas = []) => {
  const talles = new Set(deseadas.map((v) => v.talle))
  const aQuitar = state.variantes.filter((v) => v.productoId === producto.id && !talles.has(v.talle))
  for (const variante of aQuitar) {
    if (state.detalles.some((d) => d.productoVarianteId === variante.id)) {
      throw demoError(
        400,
        "Validation Error",
        `No se puede eliminar el talle ${variante.talle} porque ya tiene ventas. Poné su stock en 0 para dejar de ofrecerlo.`,
      )
    }
  }
  state.variantes = state.variantes.filter((v) => !aQuitar.includes(v))

  for (const dto of deseadas) {
    const existente = state.variantes.find((v) => v.productoId === producto.id && v.talle === dto.talle)
    if (existente) {
      existente.stock = Number(dto.stock)
      existente.sku = dto.sku
    } else {
      state.variantes.push({
        id: nextId(state, "variantes"),
        productoId: producto.id,
        talle: dto.talle,
        stock: Number(dto.stock),
        sku: dto.sku,
      })
    }
  }
}

const aDTOs = (state, productos) => productos.map((p) => productoDTO(state, p))

export const listar = ({ state }) => ok(aDTOs(state, state.productos))

export const buscar = ({ state, query }) => {
  const nombre = (query.nombre || "").trim().toLowerCase()
  const encontrados = nombre ? state.productos.filter((p) => p.name.toLowerCase().includes(nombre)) : state.productos
  return ok(aDTOs(state, encontrados))
}

export const porCategoria = ({ state, params }) =>
  ok(aDTOs(state, state.productos.filter((p) => p.categoriaId === Number(params.categoryId))))

export const obtener = ({ state, params }) => ok(productoDTO(state, buscarProducto(state, params.id)))

export const crear = ({ state, body, usuario }) => {
  const producto = { id: nextId(state, "productos"), ownerUserId: usuario.id, createdAt: fechaLocal(), updatedAt: null }
  aplicarCampos(producto, body.producto)
  state.productos.push(producto)
  reemplazarVariantes(state, producto, body.variantes)
  return creado(productoDTO(state, producto))
}

export const actualizar = ({ state, params, body, usuario }) => {
  const producto = buscarProducto(state, params.id)
  verificarPropiedad(producto, usuario)
  aplicarCampos(producto, body.producto)
  producto.updatedAt = fechaLocal()
  reemplazarVariantes(state, producto, body.variantes)
  return ok(productoDTO(state, producto))
}

export const eliminar = ({ state, params, usuario }) => {
  const producto = buscarProducto(state, params.id)
  verificarPropiedad(producto, usuario)
  state.productos = state.productos.filter((p) => p.id !== producto.id)
  state.variantes = state.variantes.filter((v) => v.productoId !== producto.id)
  return sinContenido()
}
