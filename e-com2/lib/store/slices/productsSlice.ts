import { createSlice, createAsyncThunk } from '@reduxjs/toolkit';
import { productService } from '@/lib/api/client';
import type { Product, CreateProductPayload, UpdateProductPayload } from '@/lib/types';

interface ProductsState {
  items: Product[];
  loading: boolean;
  error: string | null;
}

const initialState: ProductsState = {
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

export const fetchProducts = createAsyncThunk(
  'products/fetchProducts',
  async (_, { rejectWithValue }) => {
    const response = await productService.getAll();

    if (response.error) {
      return rejectWithValue(response.error);
    }

    return response.data as Product[];
  }
);

export const createProduct = createAsyncThunk(
  'products/createProduct',
  async (product: CreateProductPayload, { rejectWithValue, dispatch }) => {
    const response = await productService.create(product);

    if (response.error) {
      return rejectWithValue(response.error);
    }

    // Refetch to get updated list
    dispatch(fetchProducts());
    return null;
  }
);

export const updateProduct = createAsyncThunk(
  'products/updateProduct',
  async ({ uuid, product }: { uuid: string; product: UpdateProductPayload }, { rejectWithValue, dispatch }) => {
    const response = await productService.update(uuid, product);

    if (response.error) {
      return rejectWithValue(response.error);
    }

    dispatch(fetchProducts());
    return null;
  }
);

export const deleteProduct = createAsyncThunk(
  'products/deleteProduct',
  async (uuid: string, { rejectWithValue }) => {
    const response = await productService.delete(uuid);

    if (response.error) {
      return rejectWithValue(response.error);
    }

    return uuid;
  }
);

const productsSlice = createSlice({
  name: 'products',
  initialState,
  reducers: {
    clearError: (state) => {
      state.error = null;
    },
  },
  extraReducers: (builder) => {
    builder
      // Fetch
      .addCase(fetchProducts.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(fetchProducts.fulfilled, (state, action) => {
        state.loading = false;
        state.items = action.payload;
      })
      .addCase(fetchProducts.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload as string;
      })
      // Create
      .addCase(createProduct.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(createProduct.fulfilled, (state) => {
        state.loading = false;
      })
      .addCase(createProduct.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload as string;
      })
      // Update
      .addCase(updateProduct.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(updateProduct.fulfilled, (state) => {
        state.loading = false;
      })
      .addCase(updateProduct.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload as string;
      })
      // Delete
      .addCase(deleteProduct.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(deleteProduct.fulfilled, (state, action) => {
        state.loading = false;
        state.items = state.items.filter((p) => p.uuid !== action.payload);
      })
      .addCase(deleteProduct.rejected, (state, action) => {
        state.loading = false;
        state.error = action.payload as string;
      });
  },
});

export const { clearError } = productsSlice.actions;
export default productsSlice.reducer;
