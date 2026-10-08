import { categoriaDTO } from "../dto"
import { ok } from "../respuestas"

export const listar = ({ state }) => ok(state.categorias.map(categoriaDTO))
