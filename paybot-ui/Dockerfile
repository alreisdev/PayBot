# Stage 1: Build
FROM node:18-alpine AS build

WORKDIR /app

# Copy package files first for dependency caching
COPY paybot-ui/package*.json ./

# Install dependencies
RUN npm ci

# Copy source code
COPY paybot-ui/ .

# Build for production
RUN npm run build -- --configuration=production

# Stage 2: Run
FROM nginx:alpine

# Copy custom nginx config
COPY paybot-ui/nginx.conf /etc/nginx/conf.d/default.conf

# Copy built app from build stage
COPY --from=build /app/dist/paybot-ui/browser /usr/share/nginx/html

# Expose port
EXPOSE 80

# Start nginx
CMD ["nginx", "-g", "daemon off;"]