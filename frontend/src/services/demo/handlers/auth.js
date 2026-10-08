import { nextId } from "../store"
import { fechaLocal } from "../fechas"
import { usuarioDTO } from "../dto"
import { tokenPara, usuarioActual } from "../sesion"
import { ok, demoError, noAutorizado, conflicto } from "../respuestas"

// Mismo orden que UsuarioService.findByEmailOrUsername: primero por email, después por username.
export const login = ({ state, body }) => {
  const identificador = body?.emailOrUsername
  const usuario =
    state.usuarios.find((u) => u.email === identificador) ||
    state.usuarios.find((u) => u.username === identificador)
  if (!usuario || usuario.password !== body?.password) {
    throw noAutorizado("Credenciales inválidas")
  }
  return ok({ token: tokenPara(usuario), user: usuarioDTO(usuario) })
}

const validacion = (campo, mensaje) => demoError(400, "Validation Failed", `Errores de validación: ${campo}: ${mensaje}`)

export const registrar = ({ state, body }) => {
  if (body?.aceptaTerminos !== true) {
    throw validacion("aceptaTerminos", "Debés aceptar los Términos y Condiciones y la Política de Privacidad")
  }
  if (!body.password || body.password.length < 8) {
    throw validacion("password", "La contraseña debe tener entre 8 y 100 caracteres")
  }
  if (state.usuarios.some((u) => u.email === body.email)) {
    throw conflicto(`El email '${body.email}' ya existe`)
  }
  if (state.usuarios.some((u) => u.username === body.username)) {
    throw conflicto(`El username '${body.username}' ya existe`)
  }

  const usuario = {
    id: nextId(state, "usuarios"),
    username: body.username,
    email: body.email,
    password: body.password,
    nombre: body.nombre,
    apellido: body.apellido,
    role: "USER",
    createdAt: fechaLocal(),
    updatedAt: null,
  }
  state.usuarios.push(usuario)
  return ok({ token: tokenPara(usuario), user: usuarioDTO(usuario) })
}

export const validar = ({ state, token }) => ok(usuarioDTO(usuarioActual(state, token)))
