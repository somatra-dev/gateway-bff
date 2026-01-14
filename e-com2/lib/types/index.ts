/**
 * Product type matching backend ResponseProduct DTO
 */
export interface Product {
  uuid: string;
  productName: string;
  price: number;
}

/**
 * Order type matching backend ResponseOrder DTO
 */
export interface Order {
  uuid: string;
  product: Product;
  quantity: number;
  totalPrice: number;
  orderDate: string;
  status: OrderStatus;
}

/**
 * Order status enum matching backend OrderStatus
 */
export type OrderStatus = 'PENDING' | 'CONFIRMED' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED';

/**
 * Create product payload matching backend CreateProduct DTO
 */
export interface CreateProductPayload {
  productName: string;
  price: number;
}

/**
 * Update product payload matching backend UpdateProduct DTO
 */
export interface UpdateProductPayload {
  productName?: string;
  price?: number;
}

/**
 * Create order payload matching backend CreateOrder DTO
 */
export interface CreateOrderPayload {
  productUuid: string;
  quantity: number;
}
