// La sesión puede estar en localStorage ("recordarme") o en sessionStorage. Sólo cuenta
// si están el token y el usuario juntos: AuthContext guarda y borra siempre los dos.
export const leerSesionGuardada = () => {
  const storages = [
    [localStorage, "localStorage"],
    [sessionStorage, "sessionStorage"],
  ]
  for (const [storage, storageType] of storages) {
    const token = storage.getItem("auth_token")
    const user = storage.getItem("auth_user")
    if (token && user) {
      return { token, user, storageType }
    }
  }
  return null
}
