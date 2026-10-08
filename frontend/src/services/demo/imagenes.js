// En la demo no hay servidor de archivos: la imagen se guarda como data: URL dentro del
// estado en localStorage. El límite es más bajo que los 5 MB del backend porque todo
// localStorage ronda los 5 MB y el base64 agrega un tercio.
export const TAMANIO_MAXIMO_DEMO = 1024 * 1024

export const leerImagenDemo = async (file) => {
  if (file.size > TAMANIO_MAXIMO_DEMO) {
    const megas = (file.size / 1024 / 1024).toFixed(1)
    throw new Error(`En el modo demo las imágenes no pueden superar 1 MB ("${file.name}" pesa ${megas} MB).`)
  }
  const bytes = new Uint8Array(await file.arrayBuffer())
  let binario = ""
  for (let i = 0; i < bytes.length; i++) {
    binario += String.fromCharCode(bytes[i])
  }
  return `data:${file.type};base64,${btoa(binario)}`
}
