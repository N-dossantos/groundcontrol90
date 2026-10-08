import { describe, it, expect } from "vitest"
import { crearSeed } from "./seed"

const AHORA = new Date(2026, 9, 8, 15, 30, 0) // 8/10/2026 15:30 hora local

describe("crearSeed", () => {
  const estado = crearSeed(AHORA)

  it("porta los datos de DataInitializer", () => {
    expect(estado.categorias.map((c) => c.nombre)).toEqual([
      "Clubes Argentinos",
      "Clubes Europeos",
      "Selecciones",
      "Retro",
    ])
    expect(estado.usuarios.map((u) => [u.id, u.email, u.role])).toEqual([
      [1, "admin@test.com", "ADMIN"],
      [2, "user1@test.com", "USER"],
      [3, "test@test.com", "USER"],
    ])
    expect(estado.productos).toHaveLength(6)
    expect(estado.variantes).toHaveLength(23)
  })

  it("respeta talles, stock y sku por variante", () => {
    const porId = (id) => estado.variantes.find((v) => v.id === id)
    expect(porId(1)).toEqual({ id: 1, productoId: 1, talle: "S", stock: 6, sku: "BOCA-2026-TIT-S" })
    expect(porId(2)).toMatchObject({ productoId: 1, talle: "M", stock: 10 })
    expect(porId(14)).toMatchObject({ productoId: 4, talle: "L", stock: 7, sku: "RMA-2026-TIT-L" })
    expect(porId(18)).toMatchObject({ productoId: 5, talle: "M", stock: 15 })
    expect(porId(23)).toMatchObject({ productoId: 6, talle: "XL", stock: 2, sku: "ARG-1986-RETRO-XL" })
  })

  it("siembra tres pedidos con snapshot del producto y fechas relativas a ahora", () => {
    expect(estado.pedidos.map((p) => [p.id, p.usuarioId, p.estado, p.total])).toEqual([
      [1, 3, "ENTREGADO", 45000],
      [2, 3, "CONFIRMADO", 78000],
      [3, 2, "PENDIENTE", 178000],
    ])
    expect(estado.pedidos[0].createdAt).toBe("2026-09-26T15:30:00")
    expect(estado.pedidos[2].createdAt).toBe("2026-10-08T14:30:00")
    expect(estado.detalles[2]).toMatchObject({
      pedidoId: 3,
      productoVarianteId: 18,
      talle: "M",
      productoNombre: "Camiseta Selección Argentina 2026",
      cantidad: 2,
      precioUnitario: 89000,
      vendedorId: 3,
      estadoItem: "PENDIENTE",
    })
  })

  it("deja los contadores de ids en el siguiente libre", () => {
    expect(estado.seq).toEqual({
      categorias: 5,
      usuarios: 4,
      productos: 7,
      variantes: 24,
      pedidos: 4,
      detalles: 4,
      pagos: 3,
    })
  })
})
