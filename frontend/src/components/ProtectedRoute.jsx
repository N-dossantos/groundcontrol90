import { Navigate } from "react-router-dom"
import { useAuth } from "../context/AuthContext"
import LoadingSpinner from "./LoadingSpinner"
const ProtectedRoute = ({ children }) => {
  const { isAuthenticated, restoring } = useAuth()
  // Mientras se valida una sesión guardada todavía no se sabe si el usuario está logueado:
  // redirigir ya a /login lo expulsaría en cada recarga.
  if (restoring) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <LoadingSpinner size="lg" />
      </div>
    )
  }
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />
  }
  return children
}
export default ProtectedRoute
