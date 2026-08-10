import { Link } from "react-router-dom"
import { formatPrice } from "../utils/formatters"
import { Eye } from "lucide-react"
// El talle es obligatorio para comprar, así que la card no agrega al carrito:
// lleva a la ficha del producto, que es donde está el selector de talle.
const ProductCard = ({ product }) => {
  const isOutOfStock = (product.stockTotal ?? 0) === 0
  const tallesDisponibles = product.variantes?.filter((v) => v.stock > 0).length ?? 0
  return (
    <div className="card overflow-hidden hover:shadow-lg transition-shadow duration-200 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg">
      <Link to={`/product/${product.id}`} className="block">
        {/* Product Image */}
        <div className="relative aspect-square bg-gray-100 dark:bg-gray-700">
          <img
            src={product.images?.[0] || "/placeholder.svg?height=300&width=300"}
            alt={product.name}
            className="w-full h-full object-cover"
            loading="lazy"
          />
          {isOutOfStock && (
            <div className="absolute inset-0 bg-black bg-opacity-50 flex items-center justify-center">
              <span className="bg-red-600 text-white px-3 py-1 rounded-full text-sm font-medium">Sin Stock</span>
            </div>
          )}
          {/* Quick view button */}
          <div className="absolute top-2 right-2 opacity-0 group-hover:opacity-100 transition-opacity">
            <button className="p-2 bg-white dark:bg-gray-600 rounded-full shadow-md hover:bg-gray-50 dark:hover:bg-gray-500">
              <Eye size={16} className="text-gray-600 dark:text-gray-300" />
            </button>
          </div>
        </div>
        {/* Product Info */}
        <div className="p-4">
          <h3 className="font-semibold text-gray-900 dark:text-white mb-1 line-clamp-2">{product.name}</h3>
          <p className="text-sm text-gray-600 dark:text-gray-300 mb-1">
            {product.club} • {product.temporada}
          </p>
          <div className="flex items-center justify-between mb-1">
            <span className="text-lg font-bold text-blue-600 dark:text-blue-400">{formatPrice(product.price)}</span>
            <span className={`text-sm ${isOutOfStock ? "text-red-600 dark:text-red-400" : "text-green-600 dark:text-green-400"}`}>
              {isOutOfStock ? "Sin stock" : `${product.stockTotal} disponibles`}
            </span>
          </div>
          <p className="text-sm text-gray-500 dark:text-gray-400 mt-2">
            {isOutOfStock ? "Sin stock" : `${tallesDisponibles} talles disponibles`}
          </p>
        </div>
      </Link>
    </div>
  )
}
export default ProductCard
