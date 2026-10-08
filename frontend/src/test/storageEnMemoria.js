// localStorage en memoria para los tests, que corren en Node sin DOM.
// `llena` simula la cuota excedida: setItem lanza como lo haría el navegador.
export const crearStorageEnMemoria = () => {
  const datos = new Map()
  const storage = {
    llena: false,
    getItem: (k) => (datos.has(k) ? datos.get(k) : null),
    setItem: (k, v) => {
      if (storage.llena) throw new Error("QuotaExceededError")
      datos.set(k, String(v))
    },
    removeItem: (k) => {
      datos.delete(k)
    },
  }
  return storage
}
