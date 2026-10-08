// El backend serializa LocalDateTime sin zona ("2026-08-25T14:30:00"). Se arma con los
// componentes locales y no con toISOString(), que daría la hora UTC: el frontend la
// interpretaría como local y mostraría todo corrido tres horas.
const dosDigitos = (n) => String(n).padStart(2, "0")

export const fechaLocal = (fecha = new Date()) =>
  `${fecha.getFullYear()}-${dosDigitos(fecha.getMonth() + 1)}-${dosDigitos(fecha.getDate())}` +
  `T${dosDigitos(fecha.getHours())}:${dosDigitos(fecha.getMinutes())}:${dosDigitos(fecha.getSeconds())}`

// Días de calendario, no 24 h fijas: si en el medio hay un cambio de horario (en la zona
// del navegador del visitante), restar milisegundos corre la hora.
export const restarDias = (fecha, dias) => {
  const resultado = new Date(fecha)
  resultado.setDate(resultado.getDate() - dias)
  return resultado
}
