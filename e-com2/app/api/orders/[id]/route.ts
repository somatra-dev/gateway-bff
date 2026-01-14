import {NextRequest, NextResponse} from 'next/server';
import {orderService} from '@/lib/api/client';
import type {Order} from '@/lib/types';

interface RouteParams {
  params: Promise<{ id: string }>;
}

// GET /api/orders/:id - Get order by ID
export async function GET(request: NextRequest, { params }: RouteParams) {
  const { id } = await params;
  // const token = extractToken(request.headers.get('Authorization'));

  const response = await orderService.getById(id);

  if (response.error) {
    return NextResponse.json(
      { error: response.error },
      { status: response.status }
    );
  }

  return NextResponse.json(response.data as Order);
}

// DELETE /api/orders/:id - Delete order
export async function DELETE(request: NextRequest, { params }: RouteParams) {
  const { id } = await params;
  // const token = extractToken(request.headers.get('Authorization'));

  const response = await orderService.delete(id);

  if (response.error) {
    return NextResponse.json(
      { error: response.error },
      { status: response.status }
    );
  }

  return NextResponse.json({ message: 'Order deleted successfully' });
}
