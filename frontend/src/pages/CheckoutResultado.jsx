import { useEffect, useState } from "react"
import { useSearchParams, Link, useNavigate } from "react-router-dom"
import { api } from "../services/api"
import LoadingSpinner from "../components/LoadingSpinner"
import { CheckCircle, XCircle, Clock } from "lucide-react"

const POLL_INTERVAL_MS = 3000
const POLL_MAX_INTENTOS = 20

// El pago sigue en curso mientras Mercado Pago no nos mandó el webhook final.
const esEstadoFinal = (estadoPago) => estadoPago !== "PENDING" && estadoPago !== "IN_PROCESS"

const CheckoutResultado = () => {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const pedidoId = searchParams.get("pedidoId")
  const [estado, setEstado] = useState(null)
  const [agotado, setAgotado] = useState(false)

  useEffect(() => {
    if (!pedidoId) return

    // Mercado Pago redirige al comprador de vuelta apenas termina el checkout,
    // pero el webhook que confirma el pago puede tardar unos segundos más, así
    // que consultamos el estado hasta que sea final o se agoten los intentos.
    let cancelado = false
    let intentos = 0
    let timeoutId

    const consultar = async () => {
      try {
        const resultado = await api.getEstadoPago(pedidoId)
        if (cancelado) return
        setEstado(resultado)
        if (esEstadoFinal(resultado.estadoPago)) return
      } catch {
        // Un error de red puntual no significa que el pago haya fallado:
        // se reintenta mientras queden intentos.
        if (cancelado) return
      }

      intentos += 1
      if (intentos >= POLL_MAX_INTENTOS) {
        setAgotado(true)
        return
      }
      timeoutId = setTimeout(consultar, POLL_INTERVAL_MS)
    }

    consultar()

    return () => {
      cancelado = true
      clearTimeout(timeoutId)
    }
  }, [pedidoId])

  if (!pedidoId) {
    return <p className="text-center py-12 text-gray-600 dark:text-gray-400">Falta el número de pedido.</p>
  }

  if (estado?.estadoPago === "APPROVED") {
    return (
      <div className="max-w-md mx-auto text-center py-16">
        <CheckCircle className="mx-auto h-16 w-16 text-green-600 mb-4" />
        <h1 className="text-2xl font-bold text-gray-900 dark:text-white mb-2">¡Pago aprobado!</h1>
        <p className="text-gray-600 dark:text-gray-400 mb-6">Tu pedido #{pedidoId} fue confirmado.</p>
        <button onClick={() => navigate(`/orders/${pedidoId}`)} className="btn btn-primary">
          Ver mi pedido
        </button>
      </div>
    )
  }

  if (estado && esEstadoFinal(estado.estadoPago)) {
    return (
      <div className="max-w-md mx-auto text-center py-16">
        <XCircle className="mx-auto h-16 w-16 text-red-600 mb-4" />
        <h1 className="text-2xl font-bold text-gray-900 dark:text-white mb-2">El pago no se completó</h1>
        <p className="text-gray-600 dark:text-gray-400 mb-6">
          Tu pedido #{pedidoId} fue cancelado y el stock quedó liberado. Podés volver a intentarlo.
        </p>
        <Link to="/" className="btn btn-primary">Volver al catálogo</Link>
      </div>
    )
  }

  // Se agotaron los intentos y el pago sigue sin resolverse: no es un rechazo,
  // así que se deriva al detalle del pedido en vez de dar una respuesta falsa.
  if (agotado) {
    return (
      <div className="max-w-md mx-auto text-center py-16">
        <Clock className="mx-auto h-16 w-16 text-yellow-600 mb-4" />
        <h1 className="text-2xl font-bold text-gray-900 dark:text-white mb-2">Tu pago sigue procesándose</h1>
        <p className="text-gray-600 dark:text-gray-400 mb-6">
          Mercado Pago todavía no confirmó el pago del pedido #{pedidoId}. Podés seguir su estado desde tus pedidos.
        </p>
        <button onClick={() => navigate(`/orders/${pedidoId}`)} className="btn btn-primary">
          Ver mi pedido
        </button>
      </div>
    )
  }

  return (
    <div className="max-w-md mx-auto text-center py-16">
      <div className="flex justify-center mb-4">
        <LoadingSpinner size="lg" />
      </div>
      <p className="text-gray-600 dark:text-gray-400">Confirmando tu pago con Mercado Pago...</p>
    </div>
  )
}

export default CheckoutResultado
