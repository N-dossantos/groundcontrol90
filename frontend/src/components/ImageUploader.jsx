import { useState } from "react"
import { Upload, X, ImageIcon, Loader2 } from "lucide-react"
import { api } from "../services/api"

// Los mismos formatos que acepta AlmacenamientoService en el backend. Se validan acá
// también para dar el error al instante, pero la validación que cuenta es la del
// servidor, que mira los magic bytes y no el tipo que declara el navegador.
const TIPOS_ACEPTADOS = ["image/jpeg", "image/png", "image/gif", "image/webp"]
const TAMANIO_MAXIMO = 5 * 1024 * 1024

const ImageUploader = ({ images = [], onChange, maxImages = 5 }) => {
  const [dragActive, setDragActive] = useState(false)
  const [subiendo, setSubiendo] = useState(false)
  const [error, setError] = useState("")

  // Sube los archivos al backend y guarda las URLs que devuelve.
  // Antes esto hacía URL.createObjectURL(file), que produce una URL blob: válida sólo
  // en la pestaña que la creó: se persistía en la base y quedaba rota apenas el
  // usuario recargaba o abría el producto desde otro dispositivo.
  const handleFiles = async (files) => {
    setError("")

    const seleccionados = Array.from(files)
    const cupo = maxImages - images.length
    if (cupo <= 0) return

    const validos = []
    for (const file of seleccionados.slice(0, cupo)) {
      if (!TIPOS_ACEPTADOS.includes(file.type)) {
        setError(`"${file.name}" no es un formato soportado (JPG, PNG, GIF o WEBP).`)
        continue
      }
      if (file.size > TAMANIO_MAXIMO) {
        setError(`"${file.name}" supera los 5 MB.`)
        continue
      }
      validos.push(file)
    }
    if (validos.length === 0) return

    setSubiendo(true)
    try {
      const urls = await Promise.all(validos.map((file) => api.uploadImage(file)))
      onChange([...images, ...urls].slice(0, maxImages))
    } catch (e) {
      setError(e.message || "No se pudo subir la imagen. Intentá de nuevo.")
    } finally {
      setSubiendo(false)
    }
  }

  const handleDrag = (e) => {
    e.preventDefault()
    e.stopPropagation()
    if (e.type === "dragenter" || e.type === "dragover") {
      setDragActive(true)
    } else if (e.type === "dragleave") {
      setDragActive(false)
    }
  }

  const handleDrop = (e) => {
    e.preventDefault()
    e.stopPropagation()
    setDragActive(false)
    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      handleFiles(e.dataTransfer.files)
    }
  }

  const handleFileInput = (e) => {
    if (e.target.files && e.target.files[0]) {
      handleFiles(e.target.files)
    }
    // Permite volver a elegir el mismo archivo después de un error
    e.target.value = ""
  }

  const removeImage = (index) => {
    const newImages = images.filter((_, i) => i !== index)
    onChange(newImages)
  }

  const canAddMore = images.length < maxImages

  return (
    <div className="space-y-4">
      {/* Image Previews */}
      {images.length > 0 && (
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-4">
          {images.map((image, index) => (
            <div key={index} className="relative group">
              <img
                src={image || "/placeholder.svg"}
                alt={`Preview ${index + 1}`}
                className="w-full h-24 object-cover rounded-lg border border-gray-200"
              />
              <button
                type="button"
                onClick={() => removeImage(index)}
                className="absolute -top-2 -right-2 bg-red-500 text-white rounded-full p-1 opacity-0 group-hover:opacity-100 transition-opacity"
              >
                <X size={14} />
              </button>
              {index === 0 && (
                <div className="absolute bottom-1 left-1 bg-blue-500 text-white text-xs px-2 py-1 rounded-lg">
                  Principal
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {/* Upload Area */}
      {canAddMore && (
        <div
          className={`relative border-2 border-dashed rounded-lg p-6 transition-colors ${
            dragActive ? "border-blue-400 bg-blue-50" : "border-gray-300 hover:border-gray-400"
          } ${subiendo ? "opacity-60 pointer-events-none" : ""}`}
          onDragEnter={handleDrag}
          onDragLeave={handleDrag}
          onDragOver={handleDrag}
          onDrop={handleDrop}
        >
          <input
            type="file"
            multiple
            accept={TIPOS_ACEPTADOS.join(",")}
            onChange={handleFileInput}
            disabled={subiendo}
            className="absolute inset-0 w-full h-full opacity-0 cursor-pointer"
          />
          <div className="text-center">
            <div className="mx-auto h-12 w-12 text-gray-400 mb-4">
              {subiendo ? (
                <Loader2 size={48} className="animate-spin" />
              ) : images.length === 0 ? (
                <ImageIcon size={48} />
              ) : (
                <Upload size={48} />
              )}
            </div>
            <div className="text-sm text-gray-600">
              <p className="font-medium">
                {subiendo
                  ? "Subiendo..."
                  : images.length === 0
                    ? "Arrastra imágenes aquí o haz clic para seleccionar"
                    : "Agregar más imágenes"}
              </p>
              <p className="mt-1">
                PNG, JPG, GIF o WEBP hasta 5MB ({images.length}/{maxImages} imágenes)
              </p>
            </div>
          </div>
        </div>
      )}

      {error && (
        <p role="alert" className="text-sm text-red-600">
          {error}
        </p>
      )}

      {/* Instructions */}
      <div className="text-xs text-gray-500">
        <p>• La primera imagen será la imagen principal del producto</p>
        <p>• Puedes subir hasta {maxImages} imágenes</p>
        <p>• Formatos soportados: PNG, JPG, GIF, WEBP</p>
      </div>
    </div>
  )
}

export default ImageUploader
