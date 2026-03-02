FROM node:20-alpine AS builder
WORKDIR /app

# Copy the agenttel-web SDK and example
COPY agenttel-web/ ./agenttel-web/

# Install SDK deps and build
WORKDIR /app/agenttel-web
RUN npm install && npm run build

# Install example deps and build
WORKDIR /app/agenttel-web/examples/react-checkout
RUN npm install && npm run build

# ------- Production -------
FROM nginx:1.27-alpine
COPY --from=builder /app/agenttel-web/examples/react-checkout/dist /usr/share/nginx/html
COPY agenttel-web/examples/react-checkout/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 3000
CMD ["nginx", "-g", "daemon off;"]
