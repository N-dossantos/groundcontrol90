import { ERROR_MESSAGES, TOKEN_PREFIX } from "../constants"

// El frontend se sirve detrás del mismo dominio que la API (Caddy rutea /api/* al
// backend), así que la ruta relativa funciona igual en dev y en prod. Una URL
// absoluta con localhost apuntaría a la máquina del visitante, no al servidor.
const API_BASE_URL = "/api"

// Modo demo (deploy de Vercel, sin backend): las respuestas salen de un backend simulado
// en el navegador (services/demo/). __DEMO_MODE__ es un literal que fija vite.config.js:
// en el build del VPS vale false, Rollup elimina los branches y los import() de abajo, y
// el código de la demo no llega a dist/ ni como chunk separado.
const DEMO = __DEMO_MODE__

const fetchApi = async (endpoint, config) => {
  if (DEMO) {
    const { responder } = await import("./demo/router")
    return responder(endpoint, config)
  }
  return fetch(`${API_BASE_URL}${endpoint}`, config)
}

// Helper function to get JWT token from storage
const getAuthToken = () => {
  return localStorage.getItem('auth_token') || sessionStorage.getItem('auth_token')
}

// Helper function to make HTTP requests with JWT support
const request = async (endpoint, options = {}) => {
  const token = getAuthToken()
  
  const config = {
    headers: {
      'Content-Type': 'application/json',
      ...(token && { 'Authorization': `Bearer ${token}` }),
      ...options.headers,
    },
    ...options,
  }

  try {
    const response = await fetchApi(endpoint, config)
    
    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}))
      // `message` trae el detalle en castellano ("Credenciales inválidas"); `error` es el
      // título genérico del GlobalExceptionHandler ("Unauthorized"). Algunos endpoints
      // (AdminController) sólo mandan `error`, por eso queda como segunda opción.
      throw new Error(errorData.message || errorData.error || `HTTP error! status: ${response.status}`)
    }

    // Los DELETE responden 204 sin body: response.json() fallaría sobre una operación exitosa.
    if (response.status === 204) {
      return null
    }

    return await response.json()
  } catch (error) {
    if (error.name === 'TypeError' && error.message.includes('fetch')) {
      throw new Error('No se puede conectar con el servidor. Intentá de nuevo en unos minutos.')
    }
    throw error
  }
}

// Mapea un producto del backend al formato del frontend.
// El stock ya no es un número del producto: vive en cada variante (talle).
const mapProduct = (product) => ({
  id: product.id,
  name: product.name,
  description: product.description,
  price: product.price,
  club: product.club,
  liga: product.liga,
  temporada: product.temporada,
  tipo: product.tipo,
  variantes: (product.variantes || []).map(v => ({
    id: v.id,
    talle: v.talle,
    stock: v.stock,
    sku: v.sku,
  })),
  stockTotal: product.stockTotal ?? 0,
  images: product.images || [],
  categoryId: product.categoriaId, // Backend usa 'categoriaId'
  categoryName: product.categoriaNombre,
  ownerUserId: product.ownerUserId,
  ownerUserName: product.ownerUserNombre,
  createdAt: product.createdAt,
  updatedAt: product.updatedAt,
})

// Arma el body de POST/PUT /api/productos: producto + su lista completa de variantes
const buildProductPayload = (productData) => ({
  producto: {
    name: productData.name,
    description: productData.description,
    price: Number(productData.price),
    club: productData.club,
    liga: productData.liga,
    temporada: productData.temporada,
    tipo: productData.tipo,
    images: productData.images || [],
    categoriaId: productData.categoryId ? Number(productData.categoryId) : null,
  },
  variantes: (productData.variantes || []).map(v => ({
    talle: v.talle,
    stock: Number(v.stock),
    sku: v.sku,
  })),
})

export const api = {
  // Auth endpoints - AUTENTICACIÓN REAL con Spring Boot + JWT
  async login(emailOrUsername, password) {
    try {
      const response = await request('/auth/login', {
        method: 'POST',
        body: JSON.stringify({ emailOrUsername, password })
      })
      
      // Transformar la respuesta del backend al formato del frontend
      return {
        user: {
          id: response.user.id,
          email: response.user.email,
          username: response.user.username || response.user.email.split('@')[0],
          firstName: response.user.nombre,
          lastName: response.user.apellido || response.user.nombre,
          role: response.user.role.toLowerCase()
        },
        token: response.token
      }
    } catch (error) {
      throw new Error(error.message || ERROR_MESSAGES.INVALID_CREDENTIALS)
    }
  },
  
  async register(userData) {
    try {
      const response = await request('/auth/register', {
        method: 'POST',
        body: JSON.stringify({
          username: userData.username || userData.email.split('@')[0],
          email: userData.email,
          password: userData.password,
          nombre: userData.firstName || userData.name?.split(' ')[0] || 'Usuario',
          apellido: userData.lastName || userData.name?.split(' ')[1] || 'Apellido',
          aceptaTerminos: userData.aceptaTerminos === true
        })
      })
      
      // Transformar la respuesta del backend al formato del frontend
      return {
        user: {
          id: response.user.id,
          email: response.user.email,
          username: response.user.username || userData.username || response.user.email.split('@')[0],
          firstName: response.user.nombre,
          lastName: response.user.apellido || response.user.nombre,
          role: response.user.role.toLowerCase()
        },
        token: response.token
      }
    } catch (error) {
      throw new Error(error.message || 'Error al crear la cuenta')
    }
  },
  
  async validateToken(token) {
    try {
      // AuthController expone /auth/validate sólo como POST: con el GET por defecto de
      // fetch la validación fallaba siempre y la sesión guardada se borraba en cada recarga.
      const response = await request('/auth/validate', {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${token}`
        }
      })
      
      return {
        id: response.id,
        email: response.email,
        username: response.username || response.email.split('@')[0],
        firstName: response.nombre,
        lastName: response.apellido || response.nombre,
        role: response.role.toLowerCase()
      }
    } catch (error) {
      throw new Error('Sesión expirada')
    }
  },
  
  // Products endpoints
  async getProducts(filters = {}) {
    let products = []
    
    // Si hay filtros de categoría, usar el endpoint específico
    if (filters.categoryId) {
      products = await request(`/productos/categoria/${filters.categoryId}`)
    }
    // Si hay búsqueda por texto, usar el endpoint de búsqueda
    else if (filters.search) {
      products = await request(`/productos/buscar?nombre=${encodeURIComponent(filters.search)}`)
    }
    // Obtener todos los productos
    else {
      products = await request('/productos')
    }

    // Mapear campos del backend al formato del frontend
    const mappedProducts = products.map(mapProduct)

    // Ordenar alfabéticamente
    mappedProducts.sort((a, b) => a.name.localeCompare(b.name))
    return mappedProducts
  },

  /**
   * Filtrar el catálogo por club, liga, tipo, talle y rango de precio
   */
  async getProductsFiltered(filtro = {}) {
    const params = new URLSearchParams()
    if (filtro.club) params.set('club', filtro.club)
    if (filtro.liga) params.set('liga', filtro.liga)
    if (filtro.tipo) params.set('tipo', filtro.tipo)
    if (filtro.talle) params.set('talle', filtro.talle)
    if (filtro.precioMin) params.set('precioMin', filtro.precioMin)
    if (filtro.precioMax) params.set('precioMax', filtro.precioMax)

    const products = await request(`/productos/filtrar?${params.toString()}`)
    return products.map(mapProduct)
  },

  async getProduct(id) {
    const product = await request(`/productos/${id}`)
    return mapProduct(product)
  },
  
  async createProduct(productData) {
    const created = await request('/productos', {
      method: 'POST',
      body: JSON.stringify(buildProductPayload(productData)),
    })

    return mapProduct(created)
  },

  async updateProduct(id, productData) {
    const updated = await request(`/productos/${id}`, {
      method: 'PUT',
      body: JSON.stringify(buildProductPayload(productData)),
    })

    return mapProduct(updated)
  },
  
  async deleteProduct(id) {
    await request(`/productos/${id}`, {
      method: 'DELETE',
    })
    return true
  },
  
  // Categories endpoints - REAL desde el backend
  async getCategories() {
    const categories = await request('/categorias')
    
    // Mapear campos del backend (nombre) al formato del frontend (name)
    return categories.map(cat => ({
      id: cat.id,
      name: cat.nombre // Backend usa 'nombre', frontend espera 'name'
    }))
  },
  
  // ===== DIRECCIONES =====

  async getDirecciones() {
    return request('/direcciones')
  },

  async createDireccion(data) {
    return request('/direcciones', { method: 'POST', body: JSON.stringify(data) })
  },

  async updateDireccion(id, data) {
    return request(`/direcciones/${id}`, { method: 'PUT', body: JSON.stringify(data) })
  },

  async deleteDireccion(id) {
    await request(`/direcciones/${id}`, { method: 'DELETE' })
    return true
  },

  // ===== PEDIDOS/ÓRDENES =====
  
  /**
   * Obtener historial de pedidos del usuario autenticado
   */
  async getMyOrders() {
    try {
      const pedidos = await request('/pedidos/mis-pedidos')
      return pedidos
    } catch (error) {
      throw new Error(error.message || 'Error al obtener pedidos')
    }
  },
  
  /**
   * Obtener detalle de un pedido por ID
   */
  async getOrder(orderId) {
    return request(`/pedidos/${orderId}`)
  },
  
  /**
   * Crear un nuevo pedido (checkout del carrito)
   */
  async createOrder(orderData) {
    try {
      const newOrder = {
        items: orderData.items.map(item => ({
          productoVarianteId: item.productoVarianteId,
          cantidad: item.cantidad
        })),
        direccionEnvio: orderData.shippingAddress || orderData.direccionEnvio || '',
        notas: orderData.notes || orderData.notas || ''
      }
      
      const created = await request('/pedidos', {
        method: 'POST',
        body: JSON.stringify(newOrder)
      })
      
      return created
    } catch (error) {
      if (error.message.includes('Stock insuficiente')) {
        throw new Error(error.message)
      }
      throw new Error(error.message || 'Error al crear el pedido')
    }
  },
  
  /**
   * Cancelar un pedido (solo si está en estado PENDIENTE)
   */
  async cancelOrder(orderId) {
    try {
      const canceled = await request(`/pedidos/${orderId}/cancelar`, {
        method: 'PUT'
      })
      return canceled
    } catch (error) {
      throw new Error(error.message || 'Error al cancelar el pedido')
    }
  },
  
  /**
   * Actualizar estado de un pedido (solo ADMIN)
   */
  async updateOrderStatus(orderId, newStatus) {
    try {
      const updated = await request(`/pedidos/${orderId}/estado?estado=${newStatus}`, {
        method: 'PUT'
      })
      return updated
    } catch (error) {
      throw new Error(error.message || 'Error al actualizar el estado del pedido')
    }
  },
  
  /**
   * Obtener todos los pedidos (solo ADMIN)
   */
  async getAllOrders() {
    try {
      const pedidos = await request('/pedidos')
      return pedidos
    } catch (error) {
      throw new Error(error.message || 'Error al obtener pedidos')
    }
  },
  
  /**
   * Obtener pedidos por estado (solo ADMIN)
   */
  async getOrdersByStatus(status) {
    try {
      const pedidos = await request(`/pedidos/estado/${status}`)
      return pedidos
    } catch (error) {
      throw new Error(error.message || 'Error al obtener pedidos')
    }
  },

  /**
   * Obtener el reporte de ventas (solo ADMIN).
   * `desde`/`hasta` llegan como YYYY-MM-DD (de un <input type="date">); el
   * backend espera ISO-8601 con hora, así que se completa el horario acá:
   * `desde` al inicio del día y `hasta` al final, para incluir todo ese día.
   * Si no se pasan, el backend usa los últimos 30 días por defecto.
   */
  async getReporteVentas({ desde, hasta } = {}) {
    const params = new URLSearchParams()
    if (desde) params.set('desde', `${desde}T00:00:00`)
    if (hasta) params.set('hasta', `${hasta}T23:59:59`)
    return request(`/pedidos/admin/reportes?${params.toString()}`)
  },

  // ===== PAGOS (MERCADO PAGO) =====

  /**
   * Crear la preferencia de pago de un pedido.
   * Devuelve { preferenceId, initPoint } — initPoint es la URL del checkout
   * hosteado de Mercado Pago a la que hay que redirigir al comprador.
   */
  async crearPreferenciaPago(pedidoId) {
    try {
      const preferencia = await request(`/pagos/pedidos/${pedidoId}/preferencia`, {
        method: 'POST'
      })
      return preferencia
    } catch (error) {
      throw new Error(error.message || 'Error al iniciar el pago')
    }
  },

  /**
   * Consultar el estado del pago de un pedido.
   * Devuelve { estadoPago, estadoPedido } — el frontend hace polling porque el
   * webhook de Mercado Pago puede llegar después del redirect de vuelta.
   */
  async getEstadoPago(pedidoId) {
    try {
      const estado = await request(`/pagos/pedidos/${pedidoId}/estado`)
      return estado
    } catch (error) {
      throw new Error(error.message || 'Error al consultar el estado del pago')
    }
  },

  // ===== VENTAS (PARA VENDEDORES) =====
  
  /**
   * Obtener todas las ventas del usuario autenticado (como vendedor)
   */
  async getMySales() {
    try {
      const ventas = await request('/ventas/mis-ventas')
      return ventas
    } catch (error) {
      throw new Error(error.message || 'Error al obtener ventas')
    }
  },

  /**
   * Obtener ventas filtradas por estado
   */
  async getMySalesByStatus(status) {
    try {
      const ventas = await request(`/ventas/mis-ventas/estado/${status}`)
      return ventas
    } catch (error) {
      throw new Error(error.message || 'Error al obtener ventas')
    }
  },

  /**
   * Obtener estadísticas de ventas del vendedor
   */
  async getSalesStats() {
    try {
      const stats = await request('/ventas/estadisticas')
      return stats
    } catch (error) {
      throw new Error(error.message || 'Error al obtener estadísticas')
    }
  },

  /**
   * Actualizar estado de una venta (item)
   */
  async updateSaleStatus(detalleId, newStatus) {
    try {
      const updated = await request(`/ventas/${detalleId}/estado?estado=${newStatus}`, {
        method: 'PUT'
      })
      return updated
    } catch (error) {
      throw new Error(error.message || 'Error al actualizar el estado de la venta')
    }
  },

  /**
   * Obtener detalle de una venta específica
   */
  async getSale(detalleId) {
    try {
      const venta = await request(`/ventas/${detalleId}`)
      return venta
    } catch (error) {
      throw new Error(error.message || 'Error al obtener detalle de venta')
    }
  },

  // ===== ADMIN - VENTAS TOTALES =====
  
  /**
   * Obtener todas las ventas de todos los vendedores (solo ADMIN)
   */
  async getAllSales() {
    try {
      const ventas = await request('/pedidos/admin/ventas-totales')
      return ventas
    } catch (error) {
      throw new Error(error.message || 'Error al obtener ventas totales')
    }
  },

  /**
   * Obtener estadísticas generales del marketplace (solo ADMIN)
   */
  async getGeneralStats() {
    try {
      const stats = await request('/pedidos/admin/estadisticas-generales')
      return stats
    } catch (error) {
      throw new Error(error.message || 'Error al obtener estadísticas generales')
    }
  },

  // ===== ADMIN - GESTIÓN DE USUARIOS =====
  
  /**
   * Obtener todos los usuarios (solo ADMIN)
   */
  async getAllUsers() {
    try {
      const users = await request('/admin/usuarios')
      return users.map(user => ({
        id: user.id,
        username: user.username,
        nombre: user.nombre,
        apellido: user.apellido,
        email: user.email,
        role: user.role?.toLowerCase() || 'user',
        createdAt: user.createdAt,
        updatedAt: user.updatedAt
      }))
    } catch (error) {
      throw new Error(error.message || 'Error al obtener usuarios')
    }
  },
  
  /**
   * Obtener usuario por ID (solo ADMIN)
   */
  async getUserById(userId) {
    try {
      const user = await request(`/admin/usuarios/${userId}`)
      return {
        id: user.id,
        username: user.username,
        nombre: user.nombre,
        apellido: user.apellido,
        email: user.email,
        role: user.role?.toLowerCase() || 'user',
        createdAt: user.createdAt,
        updatedAt: user.updatedAt
      }
    } catch (error) {
      throw new Error(error.message || 'Error al obtener usuario')
    }
  },
  
  /**
   * Crear nuevo usuario (solo ADMIN)
   */
  async createUser(userData) {
    try {
      const newUser = {
        username: userData.username,
        email: userData.email,
        password: userData.password,
        nombre: userData.nombre,
        apellido: userData.apellido,
        role: userData.role?.toUpperCase() || 'USER'
      }
      
      const created = await request('/admin/usuarios', {
        method: 'POST',
        body: JSON.stringify(newUser)
      })
      
      return {
        id: created.id,
        username: created.username,
        nombre: created.nombre,
        apellido: created.apellido,
        email: created.email,
        role: created.role?.toLowerCase() || 'user',
        createdAt: created.createdAt,
        updatedAt: created.updatedAt
      }
    } catch (error) {
      throw new Error(error.message || 'Error al crear usuario')
    }
  },
  
  /**
   * Actualizar usuario (solo ADMIN)
   */
  async updateUser(userId, userData) {
    try {
      const updatedUser = {
        username: userData.username,
        email: userData.email,
        nombre: userData.nombre,
        apellido: userData.apellido,
        role: userData.role?.toUpperCase()
      }
      
      // Solo incluir password si se proporciona
      if (userData.password) {
        updatedUser.password = userData.password
      }
      
      const updated = await request(`/admin/usuarios/${userId}`, {
        method: 'PUT',
        body: JSON.stringify(updatedUser)
      })
      
      return {
        id: updated.id,
        username: updated.username,
        nombre: updated.nombre,
        apellido: updated.apellido,
        email: updated.email,
        role: updated.role?.toLowerCase() || 'user',
        createdAt: updated.createdAt,
        updatedAt: updated.updatedAt
      }
    } catch (error) {
      throw new Error(error.message || 'Error al actualizar usuario')
    }
  },
  
  /**
   * Actualizar solo el rol de un usuario (solo ADMIN)
   */
  async updateUserRole(userId, newRole) {
    try {
      const updated = await request(`/admin/usuarios/${userId}/rol`, {
        method: 'PUT',
        body: JSON.stringify({ role: newRole.toUpperCase() })
      })
      
      return {
        id: updated.id,
        username: updated.username,
        nombre: updated.nombre,
        apellido: updated.apellido,
        email: updated.email,
        role: updated.role?.toLowerCase() || 'user',
        createdAt: updated.createdAt,
        updatedAt: updated.updatedAt
      }
    } catch (error) {
      throw new Error(error.message || 'Error al actualizar rol de usuario')
    }
  },
  
  /**
   * Eliminar usuario (solo ADMIN)
   */
  async deleteUser(userId) {
    try {
      await request(`/admin/usuarios/${userId}`, {
        method: 'DELETE'
      })
      return true
    } catch (error) {
      throw new Error(error.message || 'Error al eliminar usuario')
    }
  },
  
  /**
   * Obtener estadísticas de usuarios (solo ADMIN)
   */
  async getUsersStats() {
    try {
      const stats = await request('/admin/usuarios/estadisticas')
      return stats
    } catch (error) {
      throw new Error(error.message || 'Error al obtener estadísticas de usuarios')
    }
  },

  /**
   * Sube una imagen de producto y devuelve la URL con la que quedó guardada en el
   * servidor. No usa request() porque el body es FormData: hay que dejar que el
   * navegador ponga el Content-Type con su boundary, y un 'application/json' fijo
   * rompería el parseo del multipart del lado del backend.
   */
  async uploadImage(file) {
    if (DEMO) {
      const { leerImagenDemo } = await import("./demo/imagenes")
      return leerImagenDemo(file)
    }

    const formData = new FormData()
    formData.append('file', file)

    const token = getAuthToken()
    const response = await fetch(`${API_BASE_URL}/imagenes`, {
      method: 'POST',
      headers: { ...(token && { Authorization: `Bearer ${token}` }) },
      body: formData,
    })

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}))
      throw new Error(errorData.message || errorData.error || 'No se pudo subir la imagen')
    }

    const { url } = await response.json()
    return url
  },
}