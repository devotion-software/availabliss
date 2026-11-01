# Availabliss — common tasks. Run `make help` for the list.
SHELL := /bin/bash
WEB := web
IMAGE ?= availabliss-web

.DEFAULT_GOAL := help
.PHONY: help setup env db-up db-down db-reset db-generate db-migrate db-studio dev build preview check test smoke \
	school-holidays \
	docker-build docker-run ios-project ios-open android-build clean

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-16s\033[0m %s\n", $$1, $$2}'

# ── Setup ────────────────────────────────────────────────────────────────────
setup: env ## Install deps, start services, apply migrations
	cd $(WEB) && bun install
	$(MAKE) db-up
	$(MAKE) db-migrate

env: ## Create web/.env from the example (won't overwrite)
	@test -f $(WEB)/.env || (cp $(WEB)/.env.example $(WEB)/.env && echo "Created $(WEB)/.env")

# ── Database & services ──────────────────────────────────────────────────────
db-up: ## Start MariaDB (:3312) and Mailpit (UI :8027)
	docker compose up -d --wait

db-down: ## Stop local services
	docker compose down

db-reset: ## Drop the local database volume and re-migrate
	docker compose down -v
	$(MAKE) db-up
	$(MAKE) db-migrate

db-generate: ## Generate a migration from schema changes
	cd $(WEB) && bun run db:generate

db-migrate: ## Apply migrations
	cd $(WEB) && bun run db:migrate

db-studio: ## Open Drizzle Studio
	cd $(WEB) && bun run db:studio

# ── Web app ──────────────────────────────────────────────────────────────────
dev: ## Run the web app + API on http://localhost:5190
	cd $(WEB) && bun run dev

build: ## Production build of the web app
	cd $(WEB) && bun run build

preview: build ## Serve the production build locally
	cd $(WEB) && bun run preview

check: ## Type-check the web app
	cd $(WEB) && bun run check

test: ## Run unit tests
	cd $(WEB) && bun run test

smoke: ## End-to-end API smoke test against the running dev server (needs `make dev`)
	cd $(WEB) && bun scripts/smoke.ts

school-holidays: ## Import school holidays from a CSV: make school-holidays FILE=path.csv
	cd $(WEB) && bun scripts/import-school-holidays.ts $(abspath $(FILE))

# ── Docker (same image Coolify builds) ───────────────────────────────────────
docker-build: ## Build the production image
	docker build -t $(IMAGE) .

docker-run: ## Run the production image against web/.env
	docker run --rm -p 3000:3000 --env-file $(WEB)/.env --add-host=host.docker.internal:host-gateway $(IMAGE)

# ── Native apps ──────────────────────────────────────────────────────────────
ios-project: ## Generate ios/Availabliss.xcodeproj with XcodeGen (brew install xcodegen)
	cd ios && xcodegen generate

ios-open: ios-project ## Generate and open the Xcode project
	open ios/Availabliss.xcodeproj

android-build: ## Build the Android debug APK (needs JDK 17 + Android SDK)
	cd android && ./gradlew assembleDebug

clean: ## Remove build output
	rm -rf $(WEB)/build $(WEB)/.svelte-kit ios/build android/build android/app/build
