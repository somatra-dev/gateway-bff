import { NextRequest, NextResponse } from 'next/server';
import { productService } from '@/lib/api/client';
import type { Product } from '@/lib/types';

interface RouteParams {
  params: Promise<{ id: string }>;
}

// GET /api/products/:id - Get product by ID
export async function GET(request: NextRequest, { params }: RouteParams) {
  const { id } = await params;
  // const token = extractToken(request.headers.get('Authorization'));

  const response = await productService.getById(id);

  if (response.error) {
    return NextResponse.json(
      { error: response.error },
      { status: response.status }
    );
  }

  return NextResponse.json(response.data as Product);
}

// PUT /api/products/:id - Update product
export async function PUT(request: NextRequest, { params }: RouteParams) {
  const { id } = await params;
  // const token = extractToken(request.headers.get('Authorization'));

  try {
    const body = await request.json();

    const response = await productService.update(id, body);

    if (response.error) {
      return NextResponse.json(
        { error: response.error },
        { status: response.status }
      );
    }

    return NextResponse.json({ message: 'Product updated successfully' });
  } catch {
    return NextResponse.json(
      { error: 'Invalid request body' },
      { status: 400 }
    );
  }
}

// DELETE /api/products/:id - Delete product
export async function DELETE(request: NextRequest, { params }: RouteParams) {
  const { id } = await params;
  // const token = extractToken(request.headers.get('Authorization'));

  const response = await productService.delete(id);

  if (response.error) {
    return NextResponse.json(
      { error: response.error },
      { status: response.status }
    );
  }

  return NextResponse.json({ message: 'Product deleted successfully' });
}
