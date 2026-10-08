import { crearSeed } from "./seed"
import { demoError } from "./respuestas"

// Estado de la demo en el localStorage del visitante. La key lleva versión: si un deploy
// cambia la forma del estado, se sube la versión y los visitantes arrancan de un seed
// nuevo en vez de cargar datos con otra forma.
export const CLAVE_ESTADO = "demo-state-v1"

export const guardar = (estado) => {
  try {
    localStorage.setItem(CLAVE_ESTADO, JSON.stringify(estado))
  } catch {
    // Casi siempre es la cuota de localStorage (~5 MB), que se llena con imágenes subidas.
    throw demoError(
      507,
      "Insufficient Storage",
      'El almacenamiento de la demo está lleno. Usá "Reiniciar demo" para empezar de nuevo.',
    )
  }
}

// Cada llamada devuelve una copia nueva parseada del storage: el router muta esa copia y
// sólo la guarda si el handler terminó bien.
export const cargar = () => {
  try {
    const guardado = localStorage.getItem(CLAVE_ESTADO)
    if (guardado) return JSON.parse(guardado)
  } catch {
    // JSON corrupto (lo editaron a mano, o quedó a medio escribir): se vuelve a sembrar.
  }
  const inicial = crearSeed()
  guardar(inicial)
  return inicial
}

export const reiniciar = () => {
  localStorage.removeItem(CLAVE_ESTADO)
}

export const nextId = (estado, coleccion) => {
  const id = estado.seq[coleccion]
  estado.seq[coleccion] = id + 1
  return id
}
