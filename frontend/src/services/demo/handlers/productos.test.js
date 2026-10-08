import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { llamar, usarStorageEnMemoria, TOKENS } from "../../../test/llamarDemo"

beforeEach(usarStorageEnMemoria)
afterEach(() => {
  vi.unstubAllGlobals()
})

const payload = (cambios = {}) => ({
  producto: {
    name: "Camiseta Suplente Boca 2026",
    description: "Suplente amarilla",
    price: 47000,
    club: "Boca Juniors",
    liga: "Liga Profesional Argentina",
    temporada: "2026",
    tipo: "CAMISETA",
    images: ["https://example.com/a.jpg"],
    categoriaId: 1,
    ...cambios,
  },
  variantes: [
    { talle: "M", stock: 5, sku: "BOCA-SUP-M" },
    { talle: "L", stock: 3, sku: "BOCA-SUP-L" },
  ],
})

describe("catálogo de la demo", () => {
  it("lista los productos con la forma de ProductoDTO", async () => {
    const res = await llamar("GET", "/productos")

    expect(res.status).toBe(200)
    expect(res.body).toHaveLength(6)
    expect(res.body[0]).toMatchObject({
      id: 1,
      name: "Camiseta Titular Boca Juniors 2026",
      price: 45000,
      tipo: "CAMISETA",
      stockTotal: 28,
      categoriaId: 1,
      categoriaNombre: "Clubes Argentinos",
      ownerUserId: 2,
      ownerUserNombre: "User",
    })
    expect(res.body[0].variantes[0]).toEqual({ id: 1, talle: "S", stock: 6, sku: "BOCA-2026-TIT-S" })
  })

  it("busca por nombre sin distinguir mayúsculas", async () => {
    const res = await llamar("GET", "/productos/buscar?nombre=BOCA")

    expect(res.body.map((p) => p.id)).toEqual([1, 3])
  })

  it("una búsqueda vacía devuelve todo", async () => {
    const res = await llamar("GET", "/productos/buscar?nombre=")

    expect(res.body).toHaveLength(6)
  })

  it("filtra por categoría", async () => {
    const res = await llamar("GET", "/productos/categoria/4")

    expect(res.body.map((p) => p.name)).toEqual(["Camiseta Retro Argentina 1986"])
  })

  it("devuelve 404 para un producto inexistente", async () => {
    const res = await llamar("GET", "/productos/999")

    expect(res.status).toBe(404)
    expect(res.body.message).toBe("Producto con ID 999 no encontrado")
  })

  it("lista las categorías con la forma de CategoriaDTO", async () => {
    const res = await llamar("GET", "/categorias")

    expect(res.body[0]).toMatchObject({ id: 1, nombre: "Clubes Argentinos" })
  })
})

describe("ABM de productos en la demo", () => {
  it("crea un producto a nombre del usuario logueado", async () => {
    const res = await llamar("POST", "/productos", { body: payload(), token: TOKENS.user1 })

    expect(res.status).toBe(201)
    expect(res.body).toMatchObject({ id: 7, ownerUserId: 2, categoriaNombre: "Clubes Argentinos", stockTotal: 8 })
    expect(res.body.variantes.map((v) => [v.id, v.talle])).toEqual([[24, "M"], [25, "L"]])
  })

  it("exige estar logueado para crear", async () => {
    const res = await llamar("POST", "/productos", { body: payload() })

    expect(res.status).toBe(401)
  })

  it("no deja editar el producto de otro vendedor", async () => {
    const res = await llamar("PUT", "/productos/3", { body: payload(), token: TOKENS.user1 })

    expect(res.status).toBe(403)
    expect(res.body.message).toBe("No tenés permiso sobre este producto")
  })

  it("el admin puede editar cualquier producto", async () => {
    const res = await llamar("PUT", "/productos/3", { body: payload({ name: "Editado por admin" }), token: TOKENS.admin })

    expect(res.status).toBe(200)
    expect(res.body.name).toBe("Editado por admin")
    expect(res.body.ownerUserId).toBe(3)
  })

  it("al editar conserva el id de los talles que siguen y borra los que se quitan sin ventas", async () => {
    // Producto 2 (River, de user1): talles S(5) M(6) L(7) XL(8), sin ventas
    const body = {
      ...payload(),
      variantes: [
        { talle: "M", stock: 1, sku: "RIVER-2026-TIT-M" },
        { talle: "XXL", stock: 2, sku: "RIVER-2026-TIT-XXL" },
      ],
    }
    const res = await llamar("PUT", "/productos/2", { body, token: TOKENS.user1 })

    expect(res.status).toBe(200)
    expect(res.body.variantes).toEqual([
      { id: 6, talle: "M", stock: 1, sku: "RIVER-2026-TIT-M" },
      { id: 24, talle: "XXL", stock: 2, sku: "RIVER-2026-TIT-XXL" },
    ])
  })

  it("no deja quitar un talle que ya tiene ventas", async () => {
    // Producto 1 (Boca): el talle M (variante 2) está en el pedido sembrado 1
    const body = { ...payload(), variantes: [{ talle: "S", stock: 6, sku: "BOCA-2026-TIT-S" }] }
    const res = await llamar("PUT", "/productos/1", { body, token: TOKENS.user1 })

    expect(res.status).toBe(400)
    expect(res.body.message).toContain("No se puede eliminar el talle M")
    const producto = await llamar("GET", "/productos/1")
    expect(producto.body.variantes).toHaveLength(4)
  })

  it("borra un producto propio junto con sus talles", async () => {
    const res = await llamar("DELETE", "/productos/2", { token: TOKENS.user1 })

    expect(res.status).toBe(204)
    expect((await llamar("GET", "/productos/2")).status).toBe(404)
    expect((await llamar("GET", "/productos")).body).toHaveLength(5)
  })

  it("no deja borrar el producto de otro vendedor", async () => {
    const res = await llamar("DELETE", "/productos/5", { token: TOKENS.user1 })

    expect(res.status).toBe(403)
  })
})
