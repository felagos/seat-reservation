.PHONY: help backend backend-dev frontend db-up db-down all dev clean build-backend

help:
	@echo "Seat Reservation System - Available commands:"
	@echo ""
	@echo "  make dev               - Alias for: backend-dev (local with hot-reload)"
	@echo ""


dev: 
	@echo "Starting development environment..."
	docker-compose -f docker/docker-compose.dev.yml down
	docker-compose -f docker/docker-compose.dev.yml up -d --build
	@echo "Containers up and running."

clean:
	@echo "Cleaning build artifacts..."
	cd backend && ./gradlew.bat clean
	cd frontend && bun run clean 2>/dev/null || true
	@echo "Cleanup complete"
