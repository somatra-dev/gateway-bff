// Store
export { makeStore } from './store';
export type { AppStore, RootState, AppDispatch } from './store';

// Hooks
export { useAppDispatch, useAppSelector } from './hooks';

// Provider
export { StoreProvider } from './provider';

// Products slice
export {
  default as productsReducer,
  fetchProducts,
  createProduct,
  updateProduct,
  deleteProduct,
  clearError as clearProductsError,
} from './slices/productsSlice';

// Orders slice
export {
  default as ordersReducer,
  fetchOrders,
  createOrder,
  deleteOrder,
  clearError as clearOrdersError,
} from './slices/ordersSlice';
