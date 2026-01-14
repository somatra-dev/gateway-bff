# E-Commerce BFF (Backend for Frontend)

## Architecture

```
┌─────────┐     ┌─────────────┐     ┌─────────────┐     ┌──────────────────┐
│ Browser │───▶ │   Gateway   │───▶ │  Next.js    │───▶ │  Microservices   │
│         │     │   (8888)    │     │  BFF (3000) │     │  (via Gateway)   │
└─────────┘     └─────────────┘     └─────────────┘     └──────────────────┘
                      │
                      ▼
               ┌─────────────┐
               │ Auth Server │
               │   (9000)    │
               └─────────────┘
```

### How It Works

1. **Browser** sends requests to **Gateway** (port 8888)
2. **Gateway** handles OAuth2/PKCE authentication
3. **Gateway** forwards requests to **BFF** with `Authorization` header (via `TokenRelay`)
4. **BFF** decodes JWT (no verification needed - Gateway already validated)
5. **BFF** calls microservices through Gateway with forwarded token

## Project Structure

```
e-com2/
├── app/
│   ├── api/                    # BFF API Routes
│   │   ├── auth/me/route.ts    # Get current user from JWT
│   │   ├── products/           # Product CRUD endpoints
│   │   └── orders/             # Order CRUD endpoints
│   ├── products/page.tsx       # Products page
│   ├── orders/page.tsx         # Orders page
│   ├── layout.tsx              # Root layout
│   └── page.tsx                # Home page
├── components/
│   ├── ui/                     # Reusable UI components
│   ├── Navbar.tsx              # Navigation with auth
│   └── AuthStatus.tsx          # Auth status display
├── lib/
│   ├── api/
│   │   ├── client.ts           # Service client for API calls
│   │   └── config.ts           # API configuration
│   ├── auth/
│   │   └── index.ts            # Auth utilities (JWT decode, token extract)
│   ├── store/                  # Redux store
│   │   ├── slices/             # Redux slices (products, orders)
│   │   └── hooks.ts            # Typed Redux hooks
│   └── types/                  # TypeScript types
└── .env.local                  # Environment variables
```

### Environment Variables

Create `.env.local`:

```env
# Gateway URL - Browser calls Gateway directly for all API requests
# Gateway handles OAuth2 authentication (session-based) and routes to microservices
NEXT_PUBLIC_GATEWAY_URL=http://localhost:8888

```

Access via Gateway: `http://localhost:8888/bff/`

## API Routes

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/auth/me` | Get current user from JWT |
| GET | `/api/products` | List all products |
| POST | `/api/products` | Create a product |
| GET | `/api/products/[id]` | Get product by ID |
| PUT | `/api/products/[id]` | Update product |
| DELETE | `/api/products/[id]` | Delete product |
| GET | `/api/orders` | List all orders |
| POST | `/api/orders` | Create an order |
| GET | `/api/orders/[id]` | Get order by ID |
| DELETE | `/api/orders/[id]` | Delete order |
