import { useState } from "react"
import { useParams, Link } from "react-router-dom"
import { useFetch } from "../hooks/useFetch"
import { useCart } from "../context/CartContext"
import { useToast } from "../context/ToastContext"
import { api } from "../services/api"
import { formatPrice } from "../utils/formatters"
import LoadingSpinner from "../components/LoadingSpinner"
import ProductCard from "../components/ProductCard"
import { ArrowLeft, ShoppingCart, Package, Star } from "lucide-react"
const ProductDetail = () => {
  const { id } = useParams()
  const [selectedImageIndex, setSelectedImageIndex] = useState(0)
  const [selectedTalle, setSelectedTalle] = useState(null)
  const { addToCart } = useCart()
  const { success, error } = useToast()
  const { data: product, loading, error: fetchError } = useFetch(() => api.getProduct(id), [id])
  
  // Fetch related products (same category, excluding current product)
  const { data: relatedProducts } = useFetch(
    () => product ? api.getProducts({ categoryId: product.categoryId }) : Promise.resolve([]),
    [product?.categoryId]
  )
  const handleAddToCart = () => {
    if (!product) return
    if (!selectedTalle) {
      error("Elegí un talle antes de agregar al carrito")
      return
    }
    addToCart(product, selectedTalle)
    success(`${product.name} (talle ${selectedTalle.talle}) agregado al carrito`)
  }
  if (loading) {
    return (
      <div className="flex justify-center items-center min-h-64">
        <LoadingSpinner size="lg" />
      </div>
    )
  }
  if (fetchError || !product) {
    return (
      <div className="text-center py-12">
        <Package className="mx-auto h-12 w-12 text-gray-400 mb-4" />
        <h3 className="text-lg font-medium text-gray-900 mb-2">Producto no encontrado</h3>
        <p className="text-gray-600 mb-4">El producto que buscas no existe o ha sido eliminado.</p>
        <Link to="/" className="btn btn-primary">
          Volver al catálogo
        </Link>
      </div>
    )
  }
  const isOutOfStock = (product.stockTotal ?? 0) === 0
  const images = product.images || ["/placeholder.svg?height=500&width=500"]
  
  // Filter related products (exclude current product and limit to 4)
  const filteredRelatedProducts = relatedProducts
    ?.filter(p => p.id !== product.id)
    ?.slice(0, 4) || []
  return (
    <div className="max-w-6xl mx-auto">
      {/* Breadcrumb */}
      <nav className="mb-6">
        <Link to="/" className="inline-flex items-center text-blue-600 hover:text-blue-800 transition-colors">
          <ArrowLeft size={16} className="mr-1" />
          Volver al catálogo
        </Link>
      </nav>
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
        {/* Product Images */}
        <div className="space-y-4">
          {/* Main Image */}
          <div className="relative aspect-square bg-gray-100 rounded-lg overflow-hidden">
            <img
              src={images[selectedImageIndex] || "/placeholder.svg"}
              alt={product.name}
              className="w-full h-full object-cover"
            />
            {isOutOfStock && (
              <div className="absolute inset-0 bg-black bg-opacity-50 flex items-center justify-center">
                <span className="bg-red-600 text-white px-4 py-2 rounded-full text-lg font-medium">Sin Stock</span>
              </div>
            )}
          </div>
          {/* Thumbnail Images */}
          {images.length > 1 && (
            <div className="flex space-x-2 overflow-x-auto">
              {images.map((image, index) => (
                <button
                  key={index}
                  onClick={() => setSelectedImageIndex(index)}
                  className={`flex-shrink-0 w-20 h-20 rounded-lg overflow-hidden border-2 transition-colors ${
                    selectedImageIndex === index ? "border-blue-600 dark:border-blue-400" : "border-gray-200 dark:border-gray-600 hover:border-gray-300 dark:hover:border-gray-500"
                  }`}
                >
                  <img
                    src={image || "/placeholder.svg"}
                    alt={`${product.name} ${index + 1}`}
                    className="w-full h-full object-cover"
                  />
                </button>
              ))}
            </div>
          )}
        </div>
        {/* Product Info */}
        <div className="space-y-6">
          <div>
            <h1 className="text-3xl font-bold text-gray-900 dark:text-white mb-2">{product.name}</h1>
            {/* Rating placeholder */}
            <div className="flex items-center mb-4">
              <div className="flex items-center">
                {[...Array(5)].map((_, i) => (
                  <Star key={i} size={16} className={`${i < 4 ? "text-yellow-400 fill-current" : "text-gray-300 dark:text-gray-600"}`} />
                ))}
              </div>
              <span className="ml-2 text-sm text-gray-600 dark:text-gray-400">(4.0) • 24 reseñas</span>
            </div>
          </div>
          {/* Precio y selector de talle */}
          <div className="border-t border-b border-gray-200 dark:border-gray-600 py-6">
            <div className="flex items-center justify-between mb-4">
              <span className="text-3xl font-bold text-blue-600 dark:text-blue-400">{formatPrice(product.price)}</span>
              <span className={`text-lg font-medium ${isOutOfStock ? "text-red-600 dark:text-red-400" : "text-green-600 dark:text-green-400"}`}>
                {isOutOfStock ? "Sin stock" : `${product.stockTotal} disponibles`}
              </span>
            </div>
            <div>
              <p className="text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">Talle</p>
              <div className="flex flex-wrap gap-2">
                {product.variantes?.map((variante) => (
                  <button
                    key={variante.id}
                    type="button"
                    disabled={variante.stock === 0}
                    onClick={() => setSelectedTalle(variante)}
                    className={`px-4 py-2 rounded-lg border text-sm font-medium ${
                      variante.stock === 0
                        ? "border-gray-200 text-gray-300 cursor-not-allowed dark:border-gray-700 dark:text-gray-600"
                        : selectedTalle?.id === variante.id
                          ? "border-blue-600 bg-blue-50 text-blue-700 dark:bg-blue-900 dark:text-blue-200"
                          : "border-gray-300 text-gray-700 hover:border-blue-400 dark:border-gray-600 dark:text-gray-300"
                    }`}
                  >
                    {variante.talle}
                  </button>
                ))}
              </div>
              {selectedTalle && (
                <p className="text-sm text-gray-600 dark:text-gray-400 mt-2">
                  {selectedTalle.stock} disponibles en talle {selectedTalle.talle}
                </p>
              )}
            </div>
          </div>
          {/* Description */}
          <div>
            <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-2">Descripción</h3>
            <p className="text-gray-700 dark:text-gray-300 leading-relaxed">{product.description}</p>
          </div>
          {/* Add to Cart */}
          <div className="space-y-4">
            <button
              onClick={handleAddToCart}
              disabled={!selectedTalle}
              className={`w-full flex items-center justify-center gap-3 py-3 px-6 rounded-lg font-medium text-lg transition-colors ${
                !selectedTalle
                  ? "bg-gray-200 dark:bg-gray-600 text-gray-500 dark:text-gray-400 cursor-not-allowed"
                  : "bg-blue-600 text-white hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 dark:focus:ring-offset-gray-800"
              }`}
            >
              <ShoppingCart size={20} />
              {isOutOfStock ? "Producto Agotado" : selectedTalle ? "Agregar al Carrito" : "Elegí un talle"}
            </button>
            {!isOutOfStock && (
              <p className="text-sm text-gray-600 dark:text-gray-400 text-center">Envío gratis en pedidos superiores a €50</p>
            )}
          </div>
          {/* Product Details */}
          <div className="border-t border-gray-200 dark:border-gray-600 pt-6">
            <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">Detalles del producto</h3>
            <dl className="space-y-2">
              <div className="flex justify-between">
                <dt className="text-gray-600 dark:text-gray-400">Club:</dt>
                <dd className="text-gray-900 dark:text-gray-100">{product.club}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-gray-600 dark:text-gray-400">Liga:</dt>
                <dd className="text-gray-900 dark:text-gray-100">{product.liga}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-gray-600 dark:text-gray-400">Temporada:</dt>
                <dd className="text-gray-900 dark:text-gray-100">{product.temporada}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-gray-600 dark:text-gray-400">Tipo:</dt>
                <dd className="text-gray-900 dark:text-gray-100">
                  {product.tipo === "SHORT" ? "Short" : "Camiseta"}
                </dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-gray-600 dark:text-gray-400">SKU:</dt>
                <dd className="text-gray-900 dark:text-gray-100">
                  {selectedTalle ? selectedTalle.sku : "Elegí un talle"}
                </dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-gray-600 dark:text-gray-400">Disponibilidad:</dt>
                <dd className={isOutOfStock ? "text-red-600 dark:text-red-400" : "text-green-600 dark:text-green-400"}>
                  {isOutOfStock ? "Agotado" : "En stock"}
                </dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-gray-600 dark:text-gray-400">Categoría:</dt>
                <dd className="text-gray-900 dark:text-gray-100">Categoría #{product.categoryId}</dd>
              </div>
            </dl>
          </div>
        </div>
      </div>
      
      {/* Related Products */}
      {filteredRelatedProducts.length > 0 && (
        <div className="mt-16">
          <div className="border-t border-gray-200 dark:border-gray-600 pt-8">
            <h2 className="text-2xl font-bold text-gray-900 dark:text-white mb-6">Productos Relacionados</h2>
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
              {filteredRelatedProducts.map((relatedProduct) => (
                <ProductCard key={relatedProduct.id} product={relatedProduct} />
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
export default ProductDetail
