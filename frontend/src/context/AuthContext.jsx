import { createContext, useContext, useReducer, useEffect } from "react"
import { authReducer, authInitialState } from "../reducers/authReducer"
import { api } from "../services/api"
import { leerSesionGuardada } from "./sesionGuardada"
const AuthContext = createContext()
export const useAuth = () => {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider")
  }
  return context
}
export const AuthProvider = ({ children }) => {
  // Si hay una sesión guardada, la app arranca "restaurando": ProtectedRoute espera a que
  // se valide el token en vez de mandar a /login en el primer render. Sin esto, cualquier
  // carga completa de una ruta protegida (un link directo, F5, o la vuelta del pago a
  // /checkout/resultado) expulsaba al usuario aunque su sesión fuera válida.
  const [state, dispatch] = useReducer(authReducer, authInitialState, (inicial) => ({
    ...inicial,
    restoring: leerSesionGuardada() !== null,
  }))
  
  // Restore session on app load
  useEffect(() => {
    const sesion = leerSesionGuardada()
    if (!sesion) return

    api.validateToken(sesion.token)
      .then((validatedUser) => {
        dispatch({
          type: "AUTH_RESTORE",
          payload: {
            token: sesion.token,
            user: {
              id: validatedUser.id,
              username: validatedUser.username,
              email: validatedUser.email,
              firstName: validatedUser.firstName,
              lastName: validatedUser.lastName,
              role: validatedUser.role || 'user'
            },
            storageType: sesion.storageType,
          },
        })
      })
      .catch(() => {
        // Token inválido o expirado
        localStorage.removeItem("auth_token")
        localStorage.removeItem("auth_user")
        sessionStorage.removeItem("auth_token")
        sessionStorage.removeItem("auth_user")
        dispatch({ type: "AUTH_RESTORE_FAILED" })
      })
  }, [])
  const login = async (email, password, rememberMe = false) => {
    dispatch({ type: "AUTH_START" })
    try {
      const response = await api.login(email, password)
      
      // Choose storage based on rememberMe option
      const storage = rememberMe ? localStorage : sessionStorage
      
      // Save to chosen storage
      storage.setItem("auth_token", response.token)
      storage.setItem("auth_user", JSON.stringify(response.user))
      
      // Clear the other storage to avoid conflicts
      if (rememberMe) {
        sessionStorage.removeItem("auth_token")
        sessionStorage.removeItem("auth_user")
      } else {
        localStorage.removeItem("auth_token")
        localStorage.removeItem("auth_user")
      }
      
      dispatch({
        type: "AUTH_SUCCESS",
        payload: response,
      })
      return response
    } catch (error) {
      dispatch({
        type: "AUTH_ERROR",
        payload: error.message,
      })
      throw error
    }
  }
  const register = async (userData, rememberMe = false) => {
    dispatch({ type: "AUTH_START" })
    try {
      const response = await api.register(userData)
      
      // Choose storage based on rememberMe option
      const storage = rememberMe ? localStorage : sessionStorage
      
      // Save to chosen storage
      storage.setItem("auth_token", response.token)
      storage.setItem("auth_user", JSON.stringify(response.user))
      
      // Clear the other storage to avoid conflicts
      if (rememberMe) {
        sessionStorage.removeItem("auth_token")
        sessionStorage.removeItem("auth_user")
      } else {
        localStorage.removeItem("auth_token")
        localStorage.removeItem("auth_user")
      }
      
      dispatch({
        type: "AUTH_SUCCESS",
        payload: response,
      })
      return response
    } catch (error) {
      dispatch({
        type: "AUTH_ERROR",
        payload: error.message,
      })
      throw error
    }
  }
  const logout = () => {
    // Clear both storages to ensure complete logout
    localStorage.removeItem("auth_token")
    localStorage.removeItem("auth_user")
    sessionStorage.removeItem("auth_token")
    sessionStorage.removeItem("auth_user")
    dispatch({ type: "AUTH_LOGOUT" })
  }
  const value = {
    ...state,
    login,
    register,
    logout,
  }
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
