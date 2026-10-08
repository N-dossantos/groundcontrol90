import { reiniciar } from "../../services/demo/store"

// Un ecommerce que acepta "pagos" sin avisar que es una demo sería engañoso: el banner
// aparece en todas las pantallas, login incluido, mientras el modo demo esté activo.
const DemoBanner = () => {
  // Vuelve todo al estado inicial: datos de la demo, sesión y carrito (el carrito puede
  // apuntar a talles que el reinicio hace desaparecer).
  const reiniciarDemo = () => {
    reiniciar()
    for (const storage of [localStorage, sessionStorage]) {
      storage.removeItem("auth_token")
      storage.removeItem("auth_user")
    }
    localStorage.removeItem("cart_items")
    window.location.assign("/login")
  }

  return (
    <div className="bg-amber-400 text-amber-950 text-sm px-4 py-2 flex flex-wrap items-center justify-center gap-x-4 gap-y-1 text-center">
      <span>
        <strong>Modo demo</strong> — datos de ejemplo, sin backend real. Ningún pago es real.
      </span>
      <button type="button" onClick={reiniciarDemo} className="underline font-medium">
        Reiniciar demo
      </button>
    </div>
  )
}

export default DemoBanner
