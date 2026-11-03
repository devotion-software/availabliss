/**
 * Loads web/.env for local dev, builds, tests and scripts. In production there is
 * no .env file (the host injects the environment, and .dockerignore keeps .env out
 * of the image), so this is a no-op there; dotenv never overrides a variable that
 * is already set. dotenv is a devDependency on purpose: Vite bundles it into the
 * server build, so the runtime image doesn't need it in node_modules.
 */
import { config } from 'dotenv';

config({ quiet: true });
