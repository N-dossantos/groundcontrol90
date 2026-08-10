import { Link } from "react-router-dom"
import { formatPrice } from "../utils/formatters"
import { Star } from "lucide-react"

// Igual que ProductCard: el talle se elige en la ficha, no desde el listado.
const ProductListItem = ({ product }) => {
  const isOutOfStock = (product.stockTotal ?? 0) === 0
  const tallesDisponibles = product.variantes?.filter((v) => v.stock > 0) ?? []
  const images = product.images || ["/placeholder.svg?height=200&width=200"]

  return (
    <div className="card p-6 hover:shadow-lg transition-shadow rounded-lg bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700">
      <div className="flex items-center space-x-6">
        {/* Product Image */}
        <div className="flex-shrink-0">
          <Link to={`/product/${product.id}`}>
            <div className="relative w-32 h-32 bg-gray-100 rounded-lg overflow-hidden group">
              <img
                src={images[0] || "/placeholder.svg"}
                alt={product.name}
                className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-200"
              />
              {isOutOfStock && (
                <div className="absolute inset-0 bg-black bg-opacity-50 flex items-center justify-center">
                  <span className="bg-red-600 text-white px-2 py-1 rounded-lg text-xs font-medium">
                    Sin Stock
                  </span>
                </div>
              )}
            </div>
          </Link>
        </div>

        {/* Product Info */}
        <div className="flex-1 min-w-0">
          <div className="flex items-start justify-between">
            <div className="flex-1">
              <Link to={`/product/${product.id}`} className="block group">
                <h3 className="text-xl font-semibold text-gray-900 dark:text-white group-hover:text-blue-600 dark:group-hover:text-blue-400 transition-colors mb-2">
                  {product.name}
                </h3>
              </Link>
              
              <p className="text-sm text-gray-500 dark:text-gray-400 mb-2">
                {product.club} • {product.liga} • {product.temporada}
              </p>

              <p className="text-gray-600 dark:text-gray-300 mb-3 line-clamp-2">
                {product.description}
              </p>

              {/* Rating */}
              <div className="flex items-center mb-3">
                <div className="flex items-center">
                  {[...Array(5)].map((_, i) => (
                    <Star
                      key={i}
                      size={16}
                      className={`${i < 4 ? "text-yellow-400 fill-current" : "text-gray-300"}`}
                    />
                  ))}
                </div>
                <span className="ml-2 text-sm text-gray-600 dark:text-gray-400">(4.0) • 24 reseñas</span>
              </div>

              {/* Price and Stock */}
              <div className="flex items-center justify-between">
                <div className="flex items-center space-x-4">
                  <span className="text-2xl font-bold text-blue-600 dark:text-blue-400">
                    {formatPrice(product.price)}
                  </span>
                  <span className={`text-sm font-medium ${isOutOfStock ? "text-red-600 dark:text-red-400" : "text-green-600 dark:text-green-400"}`}>
                    {isOutOfStock ? "Sin stock" : `${product.stockTotal} disponibles`}
                  </span>
                </div>

                <Link
                  to={`/product/${product.id}`}
                  className="px-4 py-2 rounded-lg font-medium transition-colors bg-blue-600 text-white hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 dark:focus:ring-offset-gray-800"
                >
                  Ver talles
                </Link>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Talles disponibles */}
      {!isOutOfStock && (
        <div className="mt-4 pt-4 border-t border-gray-200 dark:border-gray-600">
          <div className="flex items-center justify-between text-sm text-gray-600 dark:text-gray-400">
            <span>Talles disponibles:</span>
            <div className="flex flex-wrap items-center gap-2">
              {tallesDisponibles.map((variante) => (
                <span
                  key={variante.id}
                  className="px-2 py-1 rounded border border-gray-300 dark:border-gray-600 text-xs"
                >
                  {variante.talle}
                </span>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

export default ProductListItem
