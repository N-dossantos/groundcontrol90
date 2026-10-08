// Cuentas sembradas de la demo (seed.js): sin esto, quien entra no tiene cómo loguearse.
const CUENTAS = [
  { rol: "Compra y vende", email: "user1@test.com", password: "user123" },
  { rol: "Vende", email: "test@test.com", password: "test123" },
  { rol: "Administra", email: "admin@test.com", password: "admin123" },
]

const DemoCuentas = ({ onElegir }) => (
  <div className="rounded-md border border-amber-300 bg-amber-50 dark:bg-gray-800 dark:border-amber-700 p-4 text-sm">
    <p className="font-medium text-gray-900 dark:text-white mb-2">Cuentas de prueba</p>
    <ul className="space-y-1">
      {CUENTAS.map((cuenta) => (
        <li key={cuenta.email}>
          <button
            type="button"
            onClick={() => onElegir(cuenta.email, cuenta.password)}
            className="text-left text-blue-700 dark:text-blue-400 hover:underline"
          >
            {cuenta.email} / {cuenta.password}
          </button>
          <span className="text-gray-600 dark:text-gray-400"> — {cuenta.rol}</span>
        </li>
      ))}
    </ul>
  </div>
)

export default DemoCuentas
