# Availabliss — common tasks. Run `make help` for the list.
SHELL := /bin/bash
WEB := web
IMAGE ?= availabliss-web

.DEFAULT_GOAL := help
.PHONY: help setup hooks env db-up db-down db-reset db-generate db-migrate db-studio dev build preview check test test-integration smoke \
	seed-demo clean-demo set-plan school-holidays \
	docker-build docker-run ios-project ios-open ios-build ios-uitest android-build android-test android-tour clean

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-16s\033[0m %s\n", $$1, $$2}'

# ── Setup ────────────────────────────────────────────────────────────────────
setup: env hooks ## Install deps, start services, apply migrations
	cd $(WEB) && bun install
	$(MAKE) db-up
	$(MAKE) db-migrate

hooks: ## Use .githooks (offers a version bump on commits to main)
	git config core.hooksPath .githooks

env: ## Create web/.env from the example (won't overwrite)
	@test -f $(WEB)/.env || (cp $(WEB)/.env.example $(WEB)/.env && echo "Created $(WEB)/.env")

# ── Database & services ──────────────────────────────────────────────────────
db-up: ## Start MariaDB (:3312), Redis (:6382) and Mailpit (UI :8027)
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

test-integration: ## Run DB integration tests against DATABASE_URL (needs `make db-up` and migrations)
	cd $(WEB) && bun run test:integration

smoke: ## End-to-end API smoke test against the running dev server (needs `make dev`)
	cd $(WEB) && bun scripts/smoke.ts

seed-demo: ## Create demo users, groups, busy time and chat (prints the email to sign in with)
	cd $(WEB) && bun scripts/seed-demo.ts

clean-demo: ## Delete the demo data created by seed-demo
	cd $(WEB) && bun scripts/clean-demo.ts

set-plan: ## Give an account Pro or free without a purchase: make set-plan EMAIL=… PLAN=pro|free [DAYS=30|lifetime]
	cd $(WEB) && bun scripts/set-plan.ts $(EMAIL) $(PLAN) $(DAYS)

school-holidays: ## Import school holidays from a CSV: make school-holidays FILE=path.csv
	cd $(WEB) && bun scripts/import-school-holidays.ts $(abspath $(FILE))

# ── Docker (same image Coolify builds) ───────────────────────────────────────
docker-build: ## Build the production image
	docker build -t $(IMAGE) .

docker-run: ## Run the production image against web/.env
	docker run --rm -p 3000:3000 --env-file $(WEB)/.env --add-host=host.docker.internal:host-gateway $(IMAGE)

# ── Native apps ──────────────────────────────────────────────────────────────
# The UI tours sign in as the demo user from `make seed-demo` unless EMAIL is given.
EMAIL ?= $(shell test -f web/.demo.json && python3 -c "import json;print(json.load(open('web/.demo.json'))['users']['sam']['email'])")

# Use Xcode even if xcode-select still points at the Command Line Tools.
XCODE_ENV := $(shell xcode-select -p 2>/dev/null | grep -q CommandLineTools && echo DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer)
IOS_SIM ?= iPhone 18 Pro
IOS_BUILD := $(XCODE_ENV) xcodebuild -project ios/Availabliss.xcodeproj -scheme Availabliss \
	-destination 'platform=iOS Simulator,name=$(IOS_SIM)' -derivedDataPath ios/build CODE_SIGNING_ALLOWED=NO

ios-project: ## Generate ios/Availabliss.xcodeproj with XcodeGen (brew install xcodegen)
	cd ios && xcodegen generate

ios-open: ios-project ## Generate and open the Xcode project
	open ios/Availabliss.xcodeproj

ios-build: ios-project ## Build the iOS app for the simulator (IOS_SIM="iPhone 18 Pro")
	$(IOS_BUILD) build

ios-uitest: ios-project ## Sign in and tour the iOS app in the simulator (after make seed-demo)
	rm -rf ios/build/uitest.xcresult ios/build/uitest-screens
	@# Start signed out: the simulator keeps Keychain items across reinstalls.
	-$(XCODE_ENV) xcrun simctl uninstall booted dev.joeherbert.availabliss 2>/dev/null
	-$(XCODE_ENV) xcrun simctl keychain booted reset 2>/dev/null
	TEST_RUNNER_AV_EMAIL=$(EMAIL) $(IOS_BUILD) -resultBundlePath ios/build/uitest.xcresult test; \
	status=$$?; $(XCODE_ENV) xcrun xcresulttool export attachments --path ios/build/uitest.xcresult \
		--output-path ios/build/uitest-screens >/dev/null 2>&1; python3 scripts/name-ui-test-screens.py ios/build/uitest-screens; \
	echo "Screenshots: ios/build/uitest-screens"; exit $$status

# Android Studio bundles a JDK; use it unless JAVA_HOME is already set.
STUDIO_JDK := /Applications/Android Studio.app/Contents/jbr/Contents/Home
# (`wildcard` can't handle the space in "Android Studio.app", hence the shell test.)
ANDROID_ENV := $(if $(JAVA_HOME),,$(shell test -d "$(STUDIO_JDK)" && echo 'JAVA_HOME="$(STUDIO_JDK)"'))
ADB ?= $(HOME)/Library/Android/sdk/platform-tools/adb

android-build: ## Build the Android debug APK (Android Studio's JDK + SDK 37)
	cd android && $(ANDROID_ENV) ./gradlew assembleDebug

android-test: ## Run Android unit tests
	cd android && $(ANDROID_ENV) ./gradlew testDebugUnitTest

android-tour: android-build ## Install on the running emulator, sign in and tour (after make seed-demo)
	$(ADB) install -r android/app/build/outputs/apk/debug/app-debug.apk
	python3 scripts/android-tour.py $(EMAIL) android/build/tour-screens

clean: ## Remove build output
	rm -rf $(WEB)/build $(WEB)/.svelte-kit ios/build android/build android/app/build
