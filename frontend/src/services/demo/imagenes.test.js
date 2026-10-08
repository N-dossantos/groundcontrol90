import { describe, it, expect } from "vitest"
import { leerImagenDemo, TAMANIO_MAXIMO_DEMO } from "./imagenes"

describe("subida de imágenes en la demo", () => {
  it("devuelve la imagen como data: URL", async () => {
    const file = new File([new Uint8Array([137, 80, 78, 71])], "foto.png", { type: "image/png" })

    expect(await leerImagenDemo(file)).toBe("data:image/png;base64,iVBORw==")
  })

  it("rechaza con un mensaje claro una imagen que no entraría en localStorage", async () => {
    const file = new File([new Uint8Array(TAMANIO_MAXIMO_DEMO + 1)], "grande.jpg", { type: "image/jpeg" })

    await expect(leerImagenDemo(file)).rejects.toThrow('En el modo demo las imágenes no pueden superar 1 MB ("grande.jpg" pesa 1.0 MB).')
  })
})
