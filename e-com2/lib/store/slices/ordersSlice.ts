import { createSlice, createAsyncThunk } from '@reduxjs/toolkit';
import { orderService } from '@/lib/api/client';
import type { Order, CreateOrderPayload } from '@/lib/types';

interface OrdersState {
  items: Order[];
  loading: boolean;
  error: string | null;
}

const initialState: OrdersState = {
  items: [],
  loading: false,
  error: null,
};

/**
 * BFF Pattern - Browser calls Gateway directly
 * - Gateway handles session-based OAuth2 authentication
 * - Gateway uses TokenRelay to forward access token to microservices
 * - CSRF token is automatically included for state-changing requests
 */

export const fetchOrders = createAsyncThunk(
  'orders/fetchOrders',
  async (_, { rejectWithValue }) => {
    const response = await orderService.getAll();

    if (response.error) {
      return rejectWithValue(response.error);
    }

    return response.data as Order[];
  }
);

export const createOrder = createAsyncThunk(
  'orders/createOrder',
  async (order: CreateOrderPayload, { rejectWithValue, dispatch }) => {
    const response = await orderService.create(order);

    if (response.error) {
      return rejectWithValue(response.error);
    }

    dispatch(fetchOrders());
    return null;
  }
);

export const deleteOrder = createAsyncThunk(
  'orders/deleteOrder',
  async (uuid: string, { rejectWithValue }) => {
    const response = await orderService.delete(uuid);

    if (response.error) {
      return rejectWithValue(response.error);
    }

    return uuid;
  }
);

const ordersSlice = createSlice({
  name: 'orders',
  initialState,
  reducers: {
    clearError: (state) => {
      state.error = null;
    },
  },
  extraReducers: (builder) => {
    builder
      // Fetch
      .addCase(fetchOrders.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(fetchOrders.fulfilled, (state, action) => {
        state.loading = false;
        state.items = action.payload;
      })
      .addCase(fetchOrders.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload as string;
      })
      // Create
      .addCase(createOrder.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(createOrder.fulfilled, (state) => {
        state.loading = false;
      })
      .addCase(createOrder.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload as string;
      })
      // Delete
      .addCase(deleteOrder.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(deleteOrder.fulfilled, (state, action) => {
        state.loading = false;
        state.items = state.items.filter((o) => o.uuid !== action.payload);
      })
      .addCase(deleteOrder.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload as string;
      });
  },
});

export const { clearError } = ordersSlice.actions;
export default ordersSlice.reducer;
