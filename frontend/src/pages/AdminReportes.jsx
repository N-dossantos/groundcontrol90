import { useState } from "react"
import { Link } from "react-router-dom"
import { useAuth } from "../context/AuthContext"
import { useFetch } from "../hooks/useFetch"
import { api } from "../services/api"
import { formatPrice } from "../utils/formatters"
import LoadingSpinner from "../components/LoadingSpinner"
import { ArrowLeft, TrendingUp, ShoppingBag, DollarSign, AlertCircle } from "lucide-react"

const AdminReportes = () => {
  const { user } = useAuth()
  const [desde, setDesde] = useState("")
  const [hasta, setHasta] = useState("")

  // ProtectedRoute solo chequea que haya sesión iniciada, no el rol, así que el gate de
  // admin va acá (mismo criterio que AdminPanel). El backend igual exige ADMIN en
  // /api/pedidos/admin/reportes; esto evita que un usuario común se coma un 403 crudo.
  const isAdmin = user?.role === 'admin'

  const { data: reporte, loading, error } = useFetch(
    () => isAdmin
      ? api.getReporteVentas({ desde: desde || undefined, hasta: hasta || undefined })
      : Promise.resolve(null),
    [desde, hasta, isAdmin]
  )

  if (!isAdmin) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <div className="text-center">
          <AlertCircle className="h-16 w-16 text-red-500 mx-auto mb-4" />
          <h2 className="text-2xl font-bold text-gray-900 dark:text-white mb-2">
            Acceso Restringido
          </h2>
          <p className="text-gray-600 dark:text-gray-300">
            No tienes permisos para acceder a esta sección
          </p>
        </div>
      </div>
    )
  }

  return (
    <div className="max-w-5xl mx-auto">
      <Link to="/admin" className="inline-flex items-center text-blue-600 dark:text-blue-400 mb-6">
        <ArrowLeft size={16} className="mr-1" /> Volver al panel
      </Link>
      <h1 className="text-3xl font-bold text-gray-900 dark:text-white mb-6">Reportes de Ventas</h1>

      <div className="flex gap-4 mb-6">
        <input type="date" value={desde} onChange={(e) => setDesde(e.target.value)} className="input" />
        <input type="date" value={hasta} onChange={(e) => setHasta(e.target.value)} className="input" />
      </div>

      {loading && <LoadingSpinner size="lg" />}
      {error && <p className="text-red-600">Error al cargar el reporte: {error}</p>}

      {reporte && (
        <>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-6 mb-8">
            <div className="card p-6 flex items-center gap-4">
              <DollarSign className="text-green-600" size={32} />
              <div>
                <p className="text-sm text-gray-500 dark:text-gray-400">Ventas totales</p>
                <p className="text-2xl font-bold text-gray-900 dark:text-white">{formatPrice(reporte.ventasTotales)}</p>
              </div>
            </div>
            <div className="card p-6 flex items-center gap-4">
              <TrendingUp className="text-blue-600" size={32} />
              <div>
                <p className="text-sm text-gray-500 dark:text-gray-400">Ticket promedio</p>
                <p className="text-2xl font-bold text-gray-900 dark:text-white">{formatPrice(reporte.ticketPromedio)}</p>
              </div>
            </div>
            <div className="card p-6 flex items-center gap-4">
              <ShoppingBag className="text-purple-600" size={32} />
              <div>
                <p className="text-sm text-gray-500 dark:text-gray-400">Pedidos pagados</p>
                <p className="text-2xl font-bold text-gray-900 dark:text-white">{reporte.cantidadPedidos}</p>
              </div>
            </div>
          </div>

          <div className="card p-6">
            <h2 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">Productos más vendidos</h2>
            {reporte.productosMasVendidos.length === 0 ? (
              <p className="text-sm text-gray-500 dark:text-gray-400">No hay ventas registradas en este período.</p>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="text-left text-gray-500 dark:text-gray-400 border-b dark:border-gray-700">
                      <th className="py-2">Producto</th>
                      <th className="py-2 text-right">Unidades vendidas</th>
                    </tr>
                  </thead>
                  <tbody>
                    {reporte.productosMasVendidos.map((p) => (
                      <tr key={p.nombre} className="border-b dark:border-gray-700">
                        <td className="py-2 text-gray-900 dark:text-white">{p.nombre}</td>
                        <td className="py-2 text-right text-gray-900 dark:text-white">{p.cantidadVendida}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </>
      )}
    </div>
  )
}

export default AdminReportes
