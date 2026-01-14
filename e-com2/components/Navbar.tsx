'use client';

import Link from 'next/link';
import { useAuth } from '@/lib/auth/AuthContext';
import { Button } from '@/components/ui/button';
import { LogIn, LogOut, User, ShoppingCart, Package } from 'lucide-react';

export function Navbar() {
  const { user, isAuthenticated, isLoading, login, logout } = useAuth();

  return (
    <nav className="border-b bg-background">
      <div className="container mx-auto px-4">
        <div className="flex h-16 items-center justify-between">
          {/* Logo & Nav Links */}
          <div className="flex items-center gap-8">
            <Link href="/" className="text-xl font-bold">
              E-Commerce BFF
            </Link>
            <div className="hidden md:flex items-center gap-6">
              <Link
                href="/products"
                className="flex items-center gap-2 text-muted-foreground hover:text-foreground transition-colors"
              >
                <Package className="h-4 w-4" />
                Products
              </Link>
              <Link
                href="/orders"
                className="flex items-center gap-2 text-muted-foreground hover:text-foreground transition-colors"
              >
                <ShoppingCart className="h-4 w-4" />
                Orders
              </Link>
            </div>
          </div>

          {/* Auth Section */}
          <div className="flex items-center gap-4">
            {isLoading ? (
              <div className="h-9 w-20 bg-muted animate-pulse rounded-md" />
            ) : isAuthenticated ? (
              <>
                <div className="flex items-center gap-2 text-sm">
                  <User className="h-4 w-4" />
                  <span className="hidden sm:inline">
                    {user?.name || user?.email || 'User'}
                  </span>
                </div>
                <Button variant="outline" size="sm" onClick={logout}>
                  <LogOut className="h-4 w-4 mr-2" />
                  Logout
                </Button>
              </>
            ) : (
              <Button onClick={login}>
                <LogIn className="h-4 w-4 mr-2" />
                Login
              </Button>
            )}
          </div>
        </div>
      </div>
    </nav>
  );
}
