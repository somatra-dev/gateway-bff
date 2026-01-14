import { NextRequest, NextResponse } from 'next/server';
import { orderService } from '@/lib/api/client';
import type { Order } from '@/lib/types';
import {extractToken} from "@/lib/auth";

/**
 * BFF API Route for Orders
 *
 * In BFF Behind Gateway pattern:
 * - Gateway handles OAuth2 authentication
 * - Gateway forwards request to BFF with Authorization header
 * - BFF extracts token and forwards to Order Service
 */

// GET /api/orders - List all orders
export async function GET(request: NextRequest) {
  // const token = extractToken(request.headers.get('Authorization'));

  const response = await orderService.getAll();

  if (response.error) {
    return NextResponse.json(
      { error: response.error },
      { status: response.status }
    );
  }

  return NextResponse.json(response.data as Order[]);
}

// POST /api/orders - Create a new order
export async function POST(request: NextRequest) {
  // const token = extractToken(request.headers.get('Authorization'));

  try {
    const body = await request.json();

    // Validate required fields
    if (!body.productUuid || body.quantity === undefined) {
      return NextResponse.json(
        { error: 'productUuid and quantity are required' },
        { status: 400 }
      );
    }

    const response = await orderService.create(
      { productUuid: body.productUuid, quantity: body.quantity },
    );

    if (response.error) {
      return NextResponse.json(
        { error: response.error },
        { status: response.status }
      );
    }

    return NextResponse.json(
      { message: 'Order created successfully' },
      { status: 201 }
    );
  } catch {
    return NextResponse.json(
      { error: 'Invalid request body' },
      { status: 400 }
    );
  }
}
