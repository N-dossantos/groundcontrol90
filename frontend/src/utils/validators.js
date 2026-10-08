export const validateEmail = (email) => {
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  return emailRegex.test(email)
}
export const validatePassword = (password) => {
  return password && password.length >= 6
}
// Para contraseñas nuevas (registro): el mismo mínimo que RegisterRequest en el backend.
// validatePassword sigue en 6 porque la usa el login, y hay cuentas con claves más cortas.
export const validateNewPassword = (password) => {
  return Boolean(password) && password.length >= 8
}
export const validateRequired = (value) => {
  return value && value.toString().trim().length > 0
}
export const validatePrice = (price) => {
  const numPrice = Number.parseFloat(price)
  return !isNaN(numPrice) && numPrice > 0
}
export const validateStock = (stock) => {
  const numStock = Number.parseInt(stock)
  return !isNaN(numStock) && numStock >= 0
}
export const getValidationErrors = (formData, rules) => {
  const errors = {}
  Object.keys(rules).forEach((field) => {
    const value = formData[field]
    const fieldRules = rules[field]
    fieldRules.forEach((rule) => {
      if (typeof rule === "function") {
        if (!rule(value)) {
          errors[field] = errors[field] || []
          errors[field].push(`${field} es inválido`)
        }
      } else if (rule.validator && !rule.validator(value)) {
        errors[field] = errors[field] || []
        errors[field].push(rule.message)
      }
    })
  })
  return errors
}
