import { NextRequest, NextResponse } from 'next/server';
import { extractToken, getUserFromToken } from '@/lib/auth';

/**
 * GET /api/auth/me - Get current user info from JWT token
 *
 * In BFF Behind Gateway pattern:
 * - Gateway handles OAuth2 authentication
 * - Gateway forwards request with Authorization header (via tokenRelay)
 * - BFF decodes the JWT to get user info (no verification needed, Gateway verified it)
 */
export async function GET(request: NextRequest) {
  const token = extractToken(request.headers.get('Authorization'));

  if (!token) {
    return NextResponse.json(
      { authenticated: false, user: null },
      { status: 200 }
    );
  }

  const user = getUserFromToken(token);

  if (!user) {
    return NextResponse.json(
      { authenticated: false, user: null },
      { status: 200 }
    );
  }

  return NextResponse.json({
    authenticated: true,
    user: {
      sub: user.sub,
      uuid: user.uuid,
      email: user.email,
      name: user.name,
      given_name: user.given_name,
      family_name: user.family_name,
      roles: user.roles || [],
      permissions: user.permissions || [],
    },
  });
}
