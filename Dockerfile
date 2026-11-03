# syntax=docker/dockerfile:1
# Production image for the web app + API (deployed on Coolify).

# ── build: full deps + vite build (adapter-node → ./build) ──────────────────
# Pinned to match CI (bun-version in .github/workflows/ci.yml).
FROM oven/bun:1.3.14 AS build
WORKDIR /app
COPY web/package.json web/bun.lock ./
RUN bun install --frozen-lockfile
COPY web/ .
# SvelteKit loads server modules while analysing routes. Nothing connects, but the
# db and auth modules refuse to load unconfigured. These placeholders exist only in
# this build stage; Coolify injects the real values into the runtime image.
ENV DATABASE_URL=mysql://build:build@localhost:3306/build \
    BETTER_AUTH_SECRET=build-time-placeholder-not-used-at-runtime
RUN bun run build

# ── deps: production-only node_modules for the externalised runtime imports ──
FROM oven/bun:1.3.14 AS deps
WORKDIR /app
COPY web/package.json web/bun.lock ./
RUN bun install --frozen-lockfile --production

# ── runtime: slim node, migrate-then-serve ──────────────────────────────────
FROM node:22.22-slim AS runtime
WORKDIR /app
ENV NODE_ENV=production
ENV PORT=3000
# adapter-node reads these to trust Coolify's reverse proxy.
ENV PROTOCOL_HEADER=x-forwarded-proto
ENV HOST_HEADER=x-forwarded-host
ENV ADDRESS_HEADER=x-forwarded-for
ENV BODY_SIZE_LIMIT=10M

# Run as the image's unprivileged node user, not root.
COPY --chown=node:node --from=deps  /app/node_modules ./node_modules
COPY --chown=node:node --from=build /app/build        ./build
COPY --chown=node:node web/package.json ./
COPY --chown=node:node web/drizzle ./drizzle
# Only the migration runner: the other scripts are Bun / TypeScript dev tools.
COPY --chown=node:node web/scripts/migrate.js ./scripts/migrate.js
USER node

EXPOSE 3000
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s CMD node -e "fetch('http://localhost:3000/api/v1/health').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"
CMD ["sh", "-c", "node scripts/migrate.js && node build"]
