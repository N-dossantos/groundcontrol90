import { createContext, useContext, useReducer, useEffect } from "react"
import { cartReducer, cartInitialState } from "../reducers/cartReducer"
import { api } from "../services/api"
const CartContext = createContext()
export const useCart = () => {
  const context = useContext(CartContext)
  if (!context) {
    throw new Error("useCart must be used within a CartProvider")
  }
  return context
}
export const CartProvider = ({ children }) => {
  const [state, dispatch] = useReducer(cartReducer, cartInitialState)
  // Restore cart from localStorage on app load
  useEffect(() => {
    const savedCart = localStorage.getItem("cart_items")
    if (savedCart) {
      // Se descartan los items guardados antes de que el carrito identificara
      // por variante: no tienen talle y el backend los rechazaría al checkout.
      const items = JSON.parse(savedCart).filter((item) => item.cartItemId && item.varianteId)
      dispatch({
        type: "RESTORE_CART",
        payload: items,
      })
    }
  }, [])
  // Save cart to localStorage whenever it changes
  useEffect(() => {
    localStorage.setItem("cart_items", JSON.stringify(state.items))
  }, [state.items])
  const addToCart = (product, variante) => {
    dispatch({
      type: "ADD_TO_CART",
      payload: { product, variante },
    })
  }
  const removeFromCart = (cartItemId) => {
    dispatch({
      type: "REMOVE_FROM_CART",
      payload: cartItemId,
    })
  }
  const updateQuantity = (cartItemId, quantity) => {
    dispatch({
      type: "UPDATE_QUANTITY",
      payload: { cartItemId, quantity },
    })
  }
  const clearCart = () => {
    dispatch({ type: "CLEAR_CART" })
  }
  const getCartItemsCount = () => {
    return state.items.reduce((total, item) => total + item.quantity, 0)
  }

  /**
   * Finalizar compra - Crea un pedido con los items del carrito
   */
  const checkout = async (shippingData) => {
    try {
      // Preparar datos del pedido
      const orderData = {
        items: state.items.map(item => ({
          productoVarianteId: item.varianteId,
          cantidad: item.quantity
        })),
        shippingAddress: shippingData?.address || '',
        notes: shippingData?.notes || ''
      }

      // Crear el pedido en el backend
      const order = await api.createOrder(orderData)

      // Limpiar el carrito después de crear el pedido exitosamente
      clearCart()

      return order
    } catch (error) {
      throw error
    }
  }

  const value = {
    ...state,
    addToCart,
    removeFromCart,
    updateQuantity,
    clearCart,
    getCartItemsCount,
    checkout,
  }
  return <CartContext.Provider value={value}>{children}</CartContext.Provider>
}
