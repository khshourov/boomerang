# Stage 1: Build
FROM node:23.11-alpine AS build
WORKDIR /app

# Copy package management files
COPY package.json package-lock.json ./

# Install dependencies
RUN npm install

# Copy source code
COPY . .

# Build the application
RUN npm run build

# Stage 2: Production Server
FROM nginx:stable-alpine
WORKDIR /usr/share/nginx/html

# Copy the built application from the build stage
COPY --from=build /app/dist .

# Copy Nginx configuration to support routing and proxying
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Expose the frontend port
EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
