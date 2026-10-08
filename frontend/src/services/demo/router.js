import { cargar, guardar } from "./store"
import { usuarioActual } from "./sesion"
import { DemoError, demoError, prohibido } from "./respuestas"
import * as auth from "./handlers/auth"
import * as productos from "./handlers/productos"
import * as categorias from "./handlers/categorias"
import * as pedidos from "./handlers/pedidos"
import * as pagos from "./handlers/pagos"
import * as ventas from "./handlers/ventas"
import * as admin from "./handlers/admin"

// Endpoints que simula la demo: sólo los que usa alguna pantalla. `acceso` replica las
// reglas de SecurityConfig y los @PreAuthorize: "publico" no mira el token, "usuario"
// exige uno válido y "admin" además rol ADMIN. Gana la primera fila que matchea, así que
// las rutas literales van antes que las que tienen :parametro en la misma posición.
const RUTAS = [
  ["POST", "/auth/login", "publico", auth.login],
  ["POST", "/auth/register", "publico", auth.registrar],
  ["POST", "/auth/validate", "publico", auth.validar],

  ["GET", "/productos", "publico", productos.listar],
  ["GET", "/productos/buscar", "publico", productos.buscar],
  ["GET", "/productos/categoria/:categoryId", "publico", productos.porCategoria],
  ["GET", "/productos/:id", "publico", productos.obtener],
  ["POST", "/productos", "usuario", productos.crear],
  ["PUT", "/productos/:id", "usuario", productos.actualizar],
  ["DELETE", "/productos/:id", "usuario", productos.eliminar],

  ["GET", "/categorias", "publico", categorias.listar],

  ["GET", "/pedidos/mis-pedidos", "usuario", pedidos.misPedidos],
  ["GET", "/pedidos/:id", "usuario", pedidos.obtener],
  ["POST", "/pedidos", "usuario", pedidos.crear],
  ["PUT", "/pedidos/:id/cancelar", "usuario", pedidos.cancelar],

  ["POST", "/pagos/pedidos/:pedidoId/preferencia", "usuario", pagos.crearPreferencia],
  ["GET", "/pagos/pedidos/:pedidoId/estado", "usuario", pagos.estado],

  ["GET", "/ventas/mis-ventas", "usuario", ventas.misVentas],
  ["GET", "/ventas/estadisticas", "usuario", ventas.estadisticas],
  ["PUT", "/ventas/:detalleId/estado", "usuario", ventas.actualizarEstado],

  ["GET", "/admin/usuarios", "admin", admin.listarUsuarios],
  ["GET", "/admin/usuarios/estadisticas", "admin", admin.estadisticasUsuarios],
  ["POST", "/admin/usuarios", "admin", admin.crearUsuario],
  ["PUT", "/admin/usuarios/:id", "admin", admin.actualizarUsuario],
  ["PUT", "/admin/usuarios/:id/rol", "admin", admin.cambiarRol],
  ["DELETE", "/admin/usuarios/:id", "admin", admin.eliminarUsuario],
  ["GET", "/pedidos/admin/reportes", "admin", admin.reporteVentas],
]

const TABLA = RUTAS.map(([metodo, patron, acceso, handler]) => ({
  metodo,
  regex: new RegExp(`^${patron.replace(/:[a-zA-Z]+/g, "([^/]+)")}$`),
  nombres: (patron.match(/:[a-zA-Z]+/g) || []).map((n) => n.slice(1)),
  acceso,
  handler,
}))

const respuestaJson = (status, body) =>
  new Response(status === 204 ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  })

// Devuelve un Response real, así request() en api.js recorre el mismo camino que con fetch.
export const responder = async (endpoint, config = {}) => {
  const metodo = (config.method || "GET").toUpperCase()
  const [path, queryString = ""] = endpoint.split("?")
  const query = Object.fromEntries(new URLSearchParams(queryString))
  const token = (config.headers?.Authorization || "").replace(/^Bearer /, "") || null
  const body = config.body ? JSON.parse(config.body) : undefined

  try {
    const ruta = TABLA.find((r) => r.metodo === metodo && r.regex.test(path))
    if (!ruta) {
      throw demoError(501, "Not Implemented", `${metodo} ${path} no está disponible en el modo demo`)
    }
    const valores = path.match(ruta.regex).slice(1)
    const params = Object.fromEntries(ruta.nombres.map((nombre, i) => [nombre, decodeURIComponent(valores[i])]))

    const state = cargar()
    const usuario = ruta.acceso === "publico" ? null : usuarioActual(state, token)
    if (ruta.acceso === "admin" && usuario.role !== "ADMIN") {
      throw prohibido("No tenés permiso para acceder a este recurso")
    }

    const resultado = ruta.handler({ state, params, query, body, token, usuario })
    // Se persiste sólo si el handler terminó bien. Si lanzó a mitad de camino (por
    // ejemplo, stock insuficiente en el segundo item de un pedido), lo que alcanzó a
    // mutar se descarta entero, igual que el rollback de la transacción en el backend.
    guardar(state)
    return respuestaJson(resultado.status, resultado.body)
  } catch (e) {
    if (e instanceof DemoError) return respuestaJson(e.status, e.body)
    throw e
  }
}
