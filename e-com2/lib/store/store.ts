import { configureStore } from '@reduxjs/toolkit';
import productsReducer from './slices/productsSlice';
import ordersReducer from './slices/ordersSlice';

export const makeStore = () => {
  return configureStore({
    reducer: {
      products: productsReducer,
      orders: ordersReducer,
    },
    devTools: process.env.NODE_ENV !== 'production',
  });
};

export type AppStore = ReturnType<typeof makeStore>;
export type RootState = ReturnType<AppStore['getState']>;
export type AppDispatch = AppStore['dispatch'];
