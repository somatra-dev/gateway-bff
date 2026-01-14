import {NextRequest, NextResponse} from 'next/server';
import {productService} from '@/lib/api/client';
import type {Product} from '@/lib/types';

/**
 * BFF API Route for Products
 *
 * In BFF Behind Gateway pattern:
 * - Gateway handles OAuth2 authentication
 * - Gateway forwards request to BFF with Authorization header
 * - BFF extracts token and forwards to Product Service
 */

// GET /api/products - List all products
export async function GET(request: NextRequest) {
  // const token = extractToken(request.headers.get('Authorization'));

  const response = await productService.getAll();

  if (response.error) {
    return NextResponse.json(
      { error: response.error },
      { status: response.status }
    );
  }

  return NextResponse.json(response.data as Product[]);
}

// POST /api/products - Create a new product
export async function POST(request: NextRequest) {
  // const token = extractToken(request.headers.get('Authorization'));

  try {
    const body = await request.json();

    // Validate required fields
    if (!body.productName || body.price === undefined) {
      return NextResponse.json(
        { error: 'productName and price are required' },
        { status: 400 }
      );
    }

    const response = await productService.create(
      { productName: body.productName, price: body.price },
    );

    if (response.error) {
      return NextResponse.json(
        { error: response.error },
        { status: response.status }
      );
    }

    return NextResponse.json(
      { message: 'Product created successfully' },
      { status: 201 }
    );
  } catch {
    return NextResponse.json(
      { error: 'Invalid request body' },
      { status: 400 }
    );
  }
}
