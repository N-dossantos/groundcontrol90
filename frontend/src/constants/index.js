// La base URL de la API vive en services/api.js como ruta relativa. No se duplica
// acá para no tener dos fuentes de verdad que puedan divergir.

// Error Messages
export const ERROR_MESSAGES = {
  INVALID_CREDENTIALS: "Credenciales inválidas",
  EMAIL_EXISTS: "El email ya está registrado",
  USERNAME_EXISTS: "El nombre de usuario ya está en uso",
  PRODUCT_NOT_FOUND: "Producto no encontrado",
  USER_NOT_FOUND: "Usuario no encontrado",
};

// Token Format
export const TOKEN_PREFIX = "token_";
