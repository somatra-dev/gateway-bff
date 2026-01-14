'use client';

import { useAuth } from '@/lib/auth/AuthContext';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { CheckCircle, XCircle, Loader2 } from 'lucide-react';

export function AuthStatus() {
  const { user, isAuthenticated, isLoading, login } = useAuth();

  if (isLoading) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Loader2 className="h-5 w-5 animate-spin" />
            Checking Authentication
          </CardTitle>
        </CardHeader>
      </Card>
    );
  }

  if (!isAuthenticated) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <XCircle className="h-5 w-5 text-muted-foreground" />
            Not Authenticated
          </CardTitle>
          <CardDescription>Login to access protected resources</CardDescription>
        </CardHeader>
        <CardContent>
          <Button onClick={login} className="w-full">
            Login via Gateway
          </Button>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <CheckCircle className="h-5 w-5 text-green-500" />
          Authenticated
        </CardTitle>
        <CardDescription>You are logged in via OAuth2/PKCE</CardDescription>
      </CardHeader>
      <CardContent className="space-y-2">
        <div className="grid grid-cols-2 gap-2 text-sm">
          {user?.name && (
            <>
              <span className="text-muted-foreground">Name:</span>
              <span>{user.name}</span>
            </>
          )}
          {user?.email && (
            <>
              <span className="text-muted-foreground">Email:</span>
              <span>{user.email}</span>
            </>
          )}
          {user?.roles && user.roles.length > 0 && (
            <>
              <span className="text-muted-foreground">Roles:</span>
              <span>{user.roles.join(', ')}</span>
            </>
          )}
        </div>
      </CardContent>
    </Card>
  );
}
