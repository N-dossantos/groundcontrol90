export const cartInitialState = {
  items: [],
  total: 0,
}
// Cada línea del carrito es una variante concreta (producto + talle), no un
// producto: dos talles de la misma camiseta son dos líneas independientes.
export const cartReducer = (state, action) => {
  switch (action.type) {
    case "ADD_TO_CART": {
      const { product, variante } = action.payload
      const cartItemId = `${product.id}:${variante.id}`
      const existingItem = state.items.find((item) => item.cartItemId === cartItemId)

      if (existingItem) {
        const updatedItems = state.items.map((item) =>
          item.cartItemId === cartItemId ? { ...item, quantity: item.quantity + 1 } : item,
        )
        return { ...state, items: updatedItems, total: calculateTotal(updatedItems) }
      }

      const newItem = {
        cartItemId,
        id: product.id,
        varianteId: variante.id,
        talle: variante.talle,
        name: product.name,
        price: product.price,
        images: product.images,
        quantity: 1,
      }
      const newItems = [...state.items, newItem]
      return { ...state, items: newItems, total: calculateTotal(newItems) }
    }
    case "REMOVE_FROM_CART": {
      const updatedItems = state.items.filter((item) => item.cartItemId !== action.payload)
      return { ...state, items: updatedItems, total: calculateTotal(updatedItems) }
    }
    case "UPDATE_QUANTITY": {
      const updatedItems = state.items
        .map((item) =>
          item.cartItemId === action.payload.cartItemId ? { ...item, quantity: action.payload.quantity } : item,
        )
        .filter((item) => item.quantity > 0)
      return { ...state, items: updatedItems, total: calculateTotal(updatedItems) }
    }
    case "CLEAR_CART":
      return cartInitialState
    case "RESTORE_CART":
      return { items: action.payload, total: calculateTotal(action.payload) }
    default:
      return state
  }
}
const calculateTotal = (items) => {
  return items.reduce((total, item) => total + item.price * item.quantity, 0)
}
