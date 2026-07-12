.PHONY: help backend backend-dev frontend db-up db-down all dev clean build-backend test test-backend test-frontend test-watch frontend-install

help:
	@echo "Seat Reservation System - Available commands:"
	@echo ""
	@echo "  make backend-dev        - Start the backend development environment"
	@echo "  make frontend-dev       - Start the frontend development environment"
	@echo "  make frontend-install   - Install frontend dependencies (Bun)"
	@echo ""
	@echo "  make test               - Run all tests (backend + frontend)"
	@echo "  make test-backend       - Run backend tests"
	@echo "  make test-frontend      - Run frontend tests"
	@echo "  make test-watch         - Run frontend tests in watch mode"
	@echo ""

frontend-install:
	@echo "Installing frontend dependencies..."
	cd frontend && bun install

backend-dev : 
	@echo "Starting development environment..."
	docker-compose -f docker/docker-compose.dev.yml down
	docker-compose -f docker/docker-compose.dev.yml up -d --build
	@echo "Containers up and running."

frontend-dev: 
	@echo "Starting frontend development environment..."
	cd frontend && bun run dev

test-backend:
	@echo "Running backend tests..."
	cd backend && ./gradlew.bat test

test-frontend:
	@echo "Running frontend tests..."
	cd frontend && bun test --run

test-watch:
	@echo "Running frontend tests in watch mode..."
	cd frontend && bun test --watch

test: test-backend test-frontend
	@echo "All tests completed"

clean:
	@echo "Cleaning build artifacts..."
	cd backend && ./gradlew.bat clean
	cd frontend && bun run clean 2>/dev/null || true
	@echo "Cleanup complete"
