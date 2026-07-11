.PHONY: help backend backend-dev frontend db-up db-down all dev clean build-backend

help:
	@echo "Seat Reservation System - Available commands:"
	@echo ""
	@echo "  make backend-dev        - Start the backend development environment"
	@echo "  make frontend-dev       - Start the frontend development environment"
	@echo ""


backend-dev : 
	@echo "Starting development environment..."
	docker-compose -f docker/docker-compose.dev.yml down
	docker-compose -f docker/docker-compose.dev.yml up -d --build
	@echo "Containers up and running."

frontend-dev: 
	@echo "Starting frontend development environment..."
	cd frontend && bun run dev

clean:
	@echo "Cleaning build artifacts..."
	cd backend && ./gradlew.bat clean
	cd frontend && bun run clean 2>/dev/null || true
	@echo "Cleanup complete"
