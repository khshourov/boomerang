#!/bin/bash

# Exit on error
set -e

# Define color codes for pretty output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Set the master key for Boomerang Core
export BOOMERANG_MASTER_KEY="9ftbMvgQQx5/fv0yKzaqQllAe14ilgoRdqlf7D+KHqg="

echo -e "${BLUE}=== Starting Boomerang System ===${NC}"

# Check for Docker and Docker Compose
if ! command -v docker &> /dev/null; then
    echo -e "${YELLOW}Error: docker is not installed.${NC}"
    exit 1
fi

if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
    echo -e "${YELLOW}Error: docker-compose is not installed.${NC}"
    exit 1
fi

# Determine docker compose command
DOCKER_COMPOSE="docker-compose"
if docker compose version &> /dev/null; then
    DOCKER_COMPOSE="docker compose"
fi

echo -e "${BLUE}Step 1: Building Java Projects...${NC}"
./gradlew build -x test --parallel

echo -e "${BLUE}Step 2: Building and Starting Docker Containers...${NC}"
# Use --build to ensure any local changes are picked up
$DOCKER_COMPOSE up --build -d

echo -e ""
echo -e "${GREEN}=== System Successfully Started ===${NC}"
echo -e "${BLUE}Web UI:      ${NC}http://localhost"
echo -e "${BLUE}Web Backend: ${NC}http://localhost:8080"
echo -e "${BLUE}Core Server: ${NC}localhost:9973"
echo -e ""
echo -e "${YELLOW}To view logs, run: $DOCKER_COMPOSE logs -f${NC}"
echo -e "${YELLOW}To stop the system, run: $DOCKER_COMPOSE down${NC}"
