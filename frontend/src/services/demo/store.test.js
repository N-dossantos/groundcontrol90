import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { cargar, guardar, reiniciar, nextId, CLAVE_ESTADO } from "./store"
import { DemoError } from "./respuestas"
import { crearStorageEnMemoria } from "../../test/storageEnMemoria"

let storage

beforeEach(() => {
  storage = crearStorageEnMemoria()
  vi.stubGlobal("localStorage", storage)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe("store de la demo", () => {
  it("siembra y persiste en la primera carga", () => {
    const estado = cargar()

    expect(estado.productos).toHaveLength(6)
    expect(JSON.parse(storage.getItem(CLAVE_ESTADO)).productos).toHaveLength(6)
  })

  it("devuelve lo guardado en las cargas siguientes", () => {
    const estado = cargar()
    estado.variantes[0].stock = 1
    guardar(estado)

    expect(cargar().variantes[0].stock).toBe(1)
  })

  it("vuelve a sembrar si el estado guardado está corrupto", () => {
    storage.setItem(CLAVE_ESTADO, "{esto no es json")

    expect(cargar().productos).toHaveLength(6)
  })

  it("reiniciar descarta los cambios", () => {
    const estado = cargar()
    estado.productos = []
    guardar(estado)

    reiniciar()

    expect(cargar().productos).toHaveLength(6)
  })

  it("con el storage lleno lanza un 507 claro y no pisa lo guardado", () => {
    const estado = cargar()
    estado.productos = []
    storage.llena = true

    expect(() => guardar(estado)).toThrow(DemoError)
    try {
      guardar(estado)
    } catch (e) {
      expect(e.status).toBe(507)
      expect(e.message).toContain("Reiniciar demo")
    }
    storage.llena = false
    expect(cargar().productos).toHaveLength(6)
  })

  it("nextId entrega el siguiente id y avanza el contador", () => {
    const estado = cargar()

    expect(nextId(estado, "productos")).toBe(7)
    expect(nextId(estado, "productos")).toBe(8)
    expect(estado.seq.productos).toBe(9)
  })
})
