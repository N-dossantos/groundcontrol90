import { fechaLocal, restarDias } from "./fechas"

// Portado de DataInitializer.java: mismos usuarios, categorías, productos, talles y stock.
// Las ids son posicionales (como las asignaría la base vacía) y los tests dependen de ellas.
const CATEGORIAS = [
  ["Clubes Argentinos", "Camisetas y shorts de clubes del fútbol argentino"],
  ["Clubes Europeos", "Camisetas y shorts de clubes de las principales ligas europeas"],
  ["Selecciones", "Camisetas y shorts de selecciones nacionales"],
  ["Retro", "Reediciones de camisetas históricas"],
]

const USUARIOS = [
  { username: "admin", email: "admin@test.com", password: "admin123", nombre: "Admin", apellido: "User", role: "ADMIN" },
  { username: "user1", email: "user1@test.com", password: "user123", nombre: "User", apellido: "One", role: "USER" },
  { username: "testuser", email: "test@test.com", password: "test123", nombre: "Test", apellido: "User", role: "USER" },
]

const PRODUCTOS = [
  {
    name: "Camiseta Titular Boca Juniors 2026",
    description: "Camiseta titular oficial temporada 2026, tela liviana con tecnología de secado rápido",
    price: 45000, club: "Boca Juniors", liga: "Liga Profesional Argentina", temporada: "2026",
    tipo: "CAMISETA", categoriaId: 1, ownerUserId: 2,
    imagen: "https://images.unsplash.com/photo-1517466787929-bc90951d0974?w=800&h=600&fit=crop&crop=center",
    talles: ["S", "M", "L", "XL"], stocks: [6, 10, 8, 4], skuBase: "BOCA-2026-TIT",
  },
  {
    name: "Camiseta Titular River Plate 2026",
    description: "Camiseta titular oficial temporada 2026 con la banda roja clásica",
    price: 45000, club: "River Plate", liga: "Liga Profesional Argentina", temporada: "2026",
    tipo: "CAMISETA", categoriaId: 1, ownerUserId: 2,
    imagen: "https://images.unsplash.com/photo-1580087433295-ab2600c1030e?w=800&h=600&fit=crop&crop=center",
    talles: ["S", "M", "L", "XL"], stocks: [5, 12, 9, 3], skuBase: "RIVER-2026-TIT",
  },
  {
    name: "Short Titular Boca Juniors 2026",
    description: "Short titular oficial temporada 2026, cintura elástica con cordón ajustable",
    price: 22000, club: "Boca Juniors", liga: "Liga Profesional Argentina", temporada: "2026",
    tipo: "SHORT", categoriaId: 1, ownerUserId: 3,
    imagen: "https://images.unsplash.com/photo-1562183241-b937e95585b6?w=800&h=600&fit=crop&crop=center",
    talles: ["S", "M", "L"], stocks: [7, 11, 6], skuBase: "BOCA-2026-SHORT",
  },
  {
    name: "Camiseta Titular Real Madrid 2026",
    description: "Camiseta titular blanca temporada 2026, corte atlético",
    price: 78000, club: "Real Madrid", liga: "LaLiga", temporada: "2026",
    tipo: "CAMISETA", categoriaId: 2, ownerUserId: 2,
    imagen: "https://images.unsplash.com/photo-1577212017184-80cc0da11082?w=800&h=600&fit=crop&crop=center",
    talles: ["S", "M", "L", "XL", "XXL"], stocks: [4, 8, 7, 5, 2], skuBase: "RMA-2026-TIT",
  },
  {
    name: "Camiseta Selección Argentina 2026",
    description: "Camiseta titular de la selección argentina, edición con las tres estrellas",
    price: 89000, club: "Selección Argentina", liga: "Selecciones", temporada: "2026",
    tipo: "CAMISETA", categoriaId: 3, ownerUserId: 3,
    imagen: "https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=800&h=600&fit=crop&crop=center",
    talles: ["S", "M", "L", "XL"], stocks: [10, 15, 12, 6], skuBase: "ARG-2026-TIT",
  },
  {
    name: "Camiseta Retro Argentina 1986",
    description: "Reedición de la camiseta campeona del mundo en México 1986",
    price: 65000, club: "Selección Argentina", liga: "Selecciones", temporada: "1986",
    tipo: "CAMISETA", categoriaId: 4, ownerUserId: 3,
    imagen: "https://images.unsplash.com/photo-1571945153237-4929e783af4a?w=800&h=600&fit=crop&crop=center",
    talles: ["M", "L", "XL"], stocks: [3, 5, 2], skuBase: "ARG-1986-RETRO",
  },
]

// Estado inicial de la demo. Además de lo que siembra DataInitializer trae tres pedidos,
// para que Mis pedidos, Mis ventas y los reportes no arranquen vacíos y haya un pedido
// PENDIENTE para probar la cancelación (el pago simulado confirma al instante los nuevos).
// Las fechas son relativas a `ahora` para que caigan dentro del reporte de 30 días.
export const crearSeed = (ahora = new Date()) => {
  const hoy = fechaLocal(ahora)

  const categorias = CATEGORIAS.map(([nombre, descripcion], i) => ({
    id: i + 1, nombre, descripcion, createdAt: hoy, updatedAt: null,
  }))

  const usuarios = USUARIOS.map((u, i) => ({ id: i + 1, ...u, createdAt: hoy, updatedAt: null }))

  const productos = []
  const variantes = []
  PRODUCTOS.forEach((p, i) => {
    const productoId = i + 1
    productos.push({
      id: productoId, name: p.name, description: p.description, price: p.price, club: p.club,
      liga: p.liga, temporada: p.temporada, tipo: p.tipo, images: [p.imagen],
      categoriaId: p.categoriaId, ownerUserId: p.ownerUserId, createdAt: hoy, updatedAt: null,
    })
    p.talles.forEach((talle, j) => {
      variantes.push({ id: variantes.length + 1, productoId, talle, stock: p.stocks[j], sku: `${p.skuBase}-${talle}` })
    })
  })

  const detalle = (id, pedidoId, varianteId, cantidad, estadoItem) => {
    const variante = variantes.find((v) => v.id === varianteId)
    const producto = productos.find((p) => p.id === variante.productoId)
    return {
      id, pedidoId, productoId: producto.id, productoVarianteId: variante.id, talle: variante.talle,
      productoNombre: producto.name, productoImagen: producto.images[0], cantidad,
      precioUnitario: producto.price, vendedorId: producto.ownerUserId, estadoItem,
    }
  }

  const detalles = [
    detalle(1, 1, 2, 1, "ENTREGADO"),   // Boca M, vende user1
    detalle(2, 2, 14, 1, "CONFIRMADO"), // Real Madrid L, vende user1
    detalle(3, 3, 18, 2, "PENDIENTE"),  // Argentina M, vende testuser
  ]

  const totalDe = (pedidoId) =>
    detalles.filter((d) => d.pedidoId === pedidoId).reduce((suma, d) => suma + d.precioUnitario * d.cantidad, 0)

  const pedido = (id, usuarioId, estado, fecha, direccionEnvio) => ({
    id, usuarioId, estado, direccionEnvio, notas: "", total: totalDe(id), createdAt: fechaLocal(fecha), updatedAt: null,
  })

  const pedidos = [
    pedido(1, 3, "ENTREGADO", restarDias(ahora, 12), "Av. Corrientes 1234, CABA"),
    pedido(2, 3, "CONFIRMADO", restarDias(ahora, 3), "Av. Corrientes 1234, CABA"),
    pedido(3, 2, "PENDIENTE", new Date(ahora.getTime() - 60 * 60 * 1000), "Bv. Oroño 850, Rosario"),
  ]

  const pagos = [
    { id: 1, pedidoId: 1, preferenceId: "demo-pref-1", estado: "APPROVED", createdAt: pedidos[0].createdAt },
    { id: 2, pedidoId: 2, preferenceId: "demo-pref-2", estado: "APPROVED", createdAt: pedidos[1].createdAt },
  ]

  return {
    seq: {
      categorias: categorias.length + 1,
      usuarios: usuarios.length + 1,
      productos: productos.length + 1,
      variantes: variantes.length + 1,
      pedidos: pedidos.length + 1,
      detalles: detalles.length + 1,
      pagos: pagos.length + 1,
    },
    categorias, usuarios, productos, variantes, pedidos, detalles, pagos,
  }
}
