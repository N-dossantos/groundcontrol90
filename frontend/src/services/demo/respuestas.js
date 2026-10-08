// Errores con el mismo shape que GlobalExceptionHandler, para que request() los procese
// por el mismo camino que en producción.
export class DemoError extends Error {
  constructor(status, error, message) {
    super(message)
    this.status = status
    this.body = { status, error, message }
  }
}

export const demoError = (status, error, message) => new DemoError(status, error, message)
export const noAutorizado = (message) => demoError(401, "Unauthorized", message)
export const prohibido = (message) => demoError(403, "Forbidden", message)
export const noEncontrado = (message) => demoError(404, "Not Found", message)
export const pedidoInvalido = (message) => demoError(400, "Bad Request", message)
export const conflicto = (message) => demoError(409, "Conflict", message)

// Lo que devuelve un handler cuando termina bien: el router lo convierte en un Response.
export const ok = (body) => ({ status: 200, body })
export const creado = (body) => ({ status: 201, body })
export const sinContenido = () => ({ status: 204, body: null })
