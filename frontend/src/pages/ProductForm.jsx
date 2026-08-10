import { useState, useEffect } from "react"
import { useParams, useNavigate, Link } from "react-router-dom"
import { useAuth } from "../context/AuthContext"
import { useToast } from "../context/ToastContext"
import { useFetch } from "../hooks/useFetch"
import { api } from "../services/api"
import { validateRequired, validatePrice, validateStock } from "../utils/validators"
import LoadingSpinner from "../components/LoadingSpinner"
import ImageUploader from "../components/ImageUploader"
import { ArrowLeft, Save, Package } from "lucide-react"
const ProductForm = () => {
  const { id } = useParams()
  const navigate = useNavigate()
  const { user } = useAuth()
  const { success, error } = useToast()
  const isEditing = Boolean(id)
  const [formData, setFormData] = useState({
    name: "",
    description: "",
    price: "",
    club: "",
    liga: "",
    temporada: "",
    tipo: "CAMISETA",
    variantes: [{ talle: "", stock: "", sku: "" }],
    categoryId: "",
    images: [],
  })
  const [errors, setErrors] = useState({})
  const [isSubmitting, setIsSubmitting] = useState(false)
  // Fetch categories
  const { data: categories } = useFetch(() => api.getCategories(), [])
  // Fetch product data if editing
  const { data: product, loading: productLoading } = useFetch(
    () => (isEditing ? api.getProduct(id) : Promise.resolve(null)),
    [id, isEditing],
  )
  // Populate form when editing
  useEffect(() => {
    if (isEditing && product) {
      setFormData({
        name: product.name || "",
        description: product.description || "",
        price: product.price?.toString() || "",
        club: product.club || "",
        liga: product.liga || "",
        temporada: product.temporada || "",
        tipo: product.tipo || "CAMISETA",
        variantes: product.variantes?.length
          ? product.variantes.map((v) => ({ talle: v.talle, stock: v.stock?.toString() ?? "", sku: v.sku }))
          : [{ talle: "", stock: "", sku: "" }],
        categoryId: product.categoryId?.toString() || "",
        images: product.images || [],
      })
    }
  }, [isEditing, product])
  const handleChange = (e) => {
    const { name, value } = e.target
    setFormData((prev) => ({
      ...prev,
      [name]: value,
    }))
    // Clear error when user starts typing
    if (errors[name]) {
      setErrors((prev) => ({
        ...prev,
        [name]: "",
      }))
    }
  }
  const handleVarianteChange = (index, field, value) => {
    setFormData((prev) => {
      const variantes = [...prev.variantes]
      variantes[index] = { ...variantes[index], [field]: value }
      return { ...prev, variantes }
    })
    if (errors.variantes) {
      setErrors((prev) => ({ ...prev, variantes: "" }))
    }
  }
  const addVariante = () => {
    setFormData((prev) => ({ ...prev, variantes: [...prev.variantes, { talle: "", stock: "", sku: "" }] }))
  }
  const removeVariante = (index) => {
    setFormData((prev) => ({ ...prev, variantes: prev.variantes.filter((_, i) => i !== index) }))
  }
  const handleImagesChange = (images) => {
    setFormData((prev) => ({
      ...prev,
      images,
    }))
    if (errors.images) {
      setErrors((prev) => ({
        ...prev,
        images: "",
      }))
    }
  }
  const validateForm = () => {
    const newErrors = {}
    if (!validateRequired(formData.name)) {
      newErrors.name = "El nombre es requerido"
    }
    if (!validateRequired(formData.description)) {
      newErrors.description = "La descripción es requerida"
    }
    if (!validateRequired(formData.price)) {
      newErrors.price = "El precio es requerido"
    } else if (!validatePrice(formData.price)) {
      newErrors.price = "El precio debe ser un número mayor a 0"
    }
    if (!validateRequired(formData.club)) {
      newErrors.club = "El club es requerido"
    }
    if (!validateRequired(formData.liga)) {
      newErrors.liga = "La liga es requerida"
    }
    if (!validateRequired(formData.temporada)) {
      newErrors.temporada = "La temporada es requerida"
    }
    if (formData.variantes.some((v) => !v.talle || v.stock === "" || !v.sku)) {
      newErrors.variantes = "Completá talle, stock y SKU para cada variante"
    } else if (formData.variantes.some((v) => !validateStock(v.stock))) {
      newErrors.variantes = "El stock de cada talle debe ser un número mayor o igual a 0"
    } else if (new Set(formData.variantes.map((v) => v.talle)).size !== formData.variantes.length) {
      newErrors.variantes = "No repitas el mismo talle en dos variantes"
    }
    if (!validateRequired(formData.categoryId)) {
      newErrors.categoryId = "La categoría es requerida"
    }
    if (formData.images.length === 0) {
      newErrors.images = "Debe agregar al menos una imagen"
    }
    setErrors(newErrors)
    return Object.keys(newErrors).length === 0
  }
  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!validateForm()) {
      return
    }
    setIsSubmitting(true)
    try {
      const productData = {
        name: formData.name.trim(),
        description: formData.description.trim(),
        price: Number.parseFloat(formData.price),
        club: formData.club.trim(),
        liga: formData.liga.trim(),
        temporada: formData.temporada.trim(),
        tipo: formData.tipo,
        variantes: formData.variantes.map((v) => ({
          talle: v.talle,
          stock: Number.parseInt(v.stock),
          sku: v.sku.trim(),
        })),
        categoryId: Number.parseInt(formData.categoryId),
        images: formData.images,
        ownerUserId: user.id, // Keep original type (string or number)
      }
      if (isEditing) {
        await api.updateProduct(id, productData)
        success("Producto actualizado exitosamente")
      } else {
        await api.createProduct(productData)
        success("Producto creado exitosamente")
      }
      navigate("/dashboard/products")
    } catch (err) {
      error("Error al guardar el producto: " + err.message)
    } finally {
      setIsSubmitting(false)
    }
  }
  if (isEditing && productLoading) {
    return (
      <div className="flex justify-center items-center min-h-64">
        <LoadingSpinner size="lg" />
      </div>
    )
  }
  if (isEditing && !product) {
    return (
      <div className="text-center py-12">
        <Package className="mx-auto h-12 w-12 text-gray-400 mb-4" />
        <h3 className="text-lg font-medium text-gray-900 mb-2">Producto no encontrado</h3>
        <p className="text-gray-600 mb-4">El producto que intentas editar no existe.</p>
        <Link to="/dashboard/products" className="btn btn-primary">
          Volver a mis productos
        </Link>
      </div>
    )
  }
  return (
    <div className="max-w-2xl mx-auto">
      {/* Header */}
      <div className="mb-8">
        <Link
          to="/dashboard/products"
          className="inline-flex items-center text-blue-600 hover:text-blue-800 transition-colors mb-4"
        >
          <ArrowLeft size={16} className="mr-1" />
          Volver a mis productos
        </Link>
        <h1 className="text-3xl font-bold text-gray-900">{isEditing ? "Editar Producto" : "Nuevo Producto"}</h1>
        <p className="text-gray-600 mt-1">
          {isEditing ? "Modifica los datos de tu producto" : "Completa la información de tu nuevo producto"}
        </p>
      </div>
      {/* Form */}
      <form onSubmit={handleSubmit} className="space-y-6">
        <div className="card p-6">
          {/* Product Images */}
          <div className="mb-6">
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Imágenes del producto <span className="text-red-500">*</span>
            </label>
            <ImageUploader images={formData.images} onChange={handleImagesChange} />
            {errors.images && <p className="mt-1 text-sm text-red-600">{errors.images}</p>}
          </div>
          {/* Product Name */}
          <div className="mb-6">
            <label htmlFor="name" className="block text-sm font-medium text-gray-700 mb-2">
              Nombre del producto <span className="text-red-500">*</span>
            </label>
            <input
              type="text"
              id="name"
              name="name"
              value={formData.name}
              onChange={handleChange}
              className={`input ${errors.name ? "border-red-500 focus:ring-red-500" : ""}`}
              placeholder="Ej: Camiseta Titular Boca Juniors 2026"
            />
            {errors.name && <p className="mt-1 text-sm text-red-600">{errors.name}</p>}
          </div>
          {/* Description */}
          <div className="mb-6">
            <label htmlFor="description" className="block text-sm font-medium text-gray-700 mb-2">
              Descripción <span className="text-red-500">*</span>
            </label>
            <textarea
              id="description"
              name="description"
              rows={4}
              value={formData.description}
              onChange={handleChange}
              className={`input resize-none ${errors.description ? "border-red-500 focus:ring-red-500" : ""}`}
              placeholder="Describe las características principales de tu producto..."
            />
            {errors.description && <p className="mt-1 text-sm text-red-600">{errors.description}</p>}
          </div>
          {/* Price */}
          <div className="mb-6">
            <label htmlFor="price" className="block text-sm font-medium text-gray-700 mb-2">
              Precio (€) <span className="text-red-500">*</span>
            </label>
            <input
              type="number"
              id="price"
              name="price"
              step="0.01"
              min="0"
              value={formData.price}
              onChange={handleChange}
              className={`input ${errors.price ? "border-red-500 focus:ring-red-500" : ""}`}
              placeholder="0.00"
            />
            {errors.price && <p className="mt-1 text-sm text-red-600">{errors.price}</p>}
          </div>
          {/* Club, liga, temporada y tipo */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-6 mb-6">
            <div>
              <label htmlFor="club" className="block text-sm font-medium text-gray-700 mb-2">
                Club <span className="text-red-500">*</span>
              </label>
              <input
                type="text"
                id="club"
                name="club"
                value={formData.club}
                onChange={handleChange}
                className={`input ${errors.club ? "border-red-500 focus:ring-red-500" : ""}`}
                placeholder="Ej: Boca Juniors"
              />
              {errors.club && <p className="mt-1 text-sm text-red-600">{errors.club}</p>}
            </div>
            <div>
              <label htmlFor="liga" className="block text-sm font-medium text-gray-700 mb-2">
                Liga <span className="text-red-500">*</span>
              </label>
              <input
                type="text"
                id="liga"
                name="liga"
                value={formData.liga}
                onChange={handleChange}
                className={`input ${errors.liga ? "border-red-500 focus:ring-red-500" : ""}`}
                placeholder="Ej: Liga Profesional Argentina"
              />
              {errors.liga && <p className="mt-1 text-sm text-red-600">{errors.liga}</p>}
            </div>
            <div>
              <label htmlFor="temporada" className="block text-sm font-medium text-gray-700 mb-2">
                Temporada <span className="text-red-500">*</span>
              </label>
              <input
                type="text"
                id="temporada"
                name="temporada"
                value={formData.temporada}
                onChange={handleChange}
                className={`input ${errors.temporada ? "border-red-500 focus:ring-red-500" : ""}`}
                placeholder="Ej: 2026"
              />
              {errors.temporada && <p className="mt-1 text-sm text-red-600">{errors.temporada}</p>}
            </div>
            <div>
              <label htmlFor="tipo" className="block text-sm font-medium text-gray-700 mb-2">
                Tipo <span className="text-red-500">*</span>
              </label>
              <select id="tipo" name="tipo" value={formData.tipo} onChange={handleChange} className="input">
                <option value="CAMISETA">Camiseta</option>
                <option value="SHORT">Short</option>
              </select>
            </div>
          </div>
          {/* Talles y stock */}
          <div className="mb-6">
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Talles y stock <span className="text-red-500">*</span>
            </label>
            {formData.variantes.map((variante, index) => (
              <div key={index} className="flex gap-3 mb-2">
                <select
                  value={variante.talle}
                  onChange={(e) => handleVarianteChange(index, "talle", e.target.value)}
                  className="input"
                  aria-label={`Talle de la variante ${index + 1}`}
                >
                  <option value="">Talle</option>
                  {["XS", "S", "M", "L", "XL", "XXL"].map((t) => (
                    <option key={t} value={t}>
                      {t}
                    </option>
                  ))}
                </select>
                <input
                  type="number"
                  min="0"
                  placeholder="Stock"
                  value={variante.stock}
                  onChange={(e) => handleVarianteChange(index, "stock", e.target.value)}
                  className="input"
                  aria-label={`Stock de la variante ${index + 1}`}
                />
                <input
                  type="text"
                  placeholder="SKU"
                  value={variante.sku}
                  onChange={(e) => handleVarianteChange(index, "sku", e.target.value)}
                  className="input"
                  aria-label={`SKU de la variante ${index + 1}`}
                />
                {formData.variantes.length > 1 && (
                  <button type="button" onClick={() => removeVariante(index)} className="btn btn-secondary">
                    Quitar
                  </button>
                )}
              </div>
            ))}
            <button type="button" onClick={addVariante} className="btn btn-secondary mt-2">
              Agregar talle
            </button>
            {errors.variantes && <p className="mt-1 text-sm text-red-600">{errors.variantes}</p>}
          </div>
          {/* Category */}
          <div className="mb-6">
            <label htmlFor="categoryId" className="block text-sm font-medium text-gray-700 mb-2">
              Categoría <span className="text-red-500">*</span>
            </label>
            <select
              id="categoryId"
              name="categoryId"
              value={formData.categoryId}
              onChange={handleChange}
              className={`input ${errors.categoryId ? "border-red-500 focus:ring-red-500" : ""}`}
            >
              <option value="">Selecciona una categoría</option>
              {categories?.map((category) => (
                <option key={category.id} value={category.id}>
                  {category.name}
                </option>
              ))}
            </select>
            {errors.categoryId && <p className="mt-1 text-sm text-red-600">{errors.categoryId}</p>}
          </div>
        </div>
        {/* Submit Button */}
        <div className="flex justify-end space-x-4">
          <Link to="/dashboard/products" className="btn btn-secondary">
            Cancelar
          </Link>
          <button
            type="submit"
            disabled={isSubmitting}
            className="btn btn-primary flex items-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {isSubmitting ? (
              <>
                <LoadingSpinner size="sm" className="border-white border-t-transparent" />
                {isEditing ? "Actualizando..." : "Creando..."}
              </>
            ) : (
              <>
                <Save size={18} />
                {isEditing ? "Actualizar Producto" : "Crear Producto"}
              </>
            )}
          </button>
        </div>
      </form>
    </div>
  )
}
export default ProductForm
