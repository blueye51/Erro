# syntax=docker/dockerfile:1
FROM node:24-alpine@sha256:ebfe2f90462722a7a4de65e91990e97fe0d401c70e0e762c5b53302f905ec1c1 AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY index.html vite.config.ts tsconfig*.json ./
COPY src ./src
COPY public ./public
RUN npm run build

FROM nginxinc/nginx-unprivileged:stable-alpine@sha256:ed04ec1ff34502c339ee5c3ae3f855442398edc1d05591e2b98981dcbbd20b1e AS production
ENV PORT=8080 \
    API_UPSTREAM=http://backend:8080 \
    NGINX_ENVSUBST_OUTPUT_DIR=/tmp/nginx-conf \
    NGINX_ENVSUBST_FILTER="^(PORT|API_UPSTREAM|API_DNS_RESOLVERS)$"
COPY nginx/nginx.conf /etc/nginx/nginx.conf
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --chmod=755 nginx/15-api-resolvers.envsh /docker-entrypoint.d/15-api-resolvers.envsh
COPY --from=build /app/dist /usr/share/nginx/html
USER 101
RUN mkdir -p /tmp/nginx-conf
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=5s \
    CMD wget -q -O /dev/null "http://127.0.0.1:$PORT/healthz" || exit 1
