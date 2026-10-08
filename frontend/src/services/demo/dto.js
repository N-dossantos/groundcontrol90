// Convierte las entidades del store en los DTOs que devuelve el backend real
// (backend/src/main/java/com/ecommerce/dto/). api.js los mapea igual que en producción.

export const usuarioDTO = (u) => ({
  id: u.id,
  username: u.username,
  nombre: u.nombre,
  apellido: u.apellido,
  email: u.email,
  role: u.role,
  createdAt: u.createdAt,
  updatedAt: u.updatedAt,
})
