import { noAutorizado } from "./respuestas"

// El token de la demo es "demo-token-<id>". No se firma nada porque la demo no tiene
// ningún dato que proteger: sólo hace falta saber quién es el usuario.
export const tokenPara = (usuario) => `demo-token-${usuario.id}`

export const usuarioActual = (state, token) => {
  const id = Number((token || "").replace(/^demo-token-/, ""))
  const usuario = state.usuarios.find((u) => u.id === id)
  if (!token || !usuario) {
    throw noAutorizado("Token expirado o inválido")
  }
  return usuario
}
