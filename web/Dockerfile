# syntax=docker/dockerfile:1

# Estágio 1: build de produção do Angular (mesma versão do Node do mise.toml).
FROM node:24.22.1-alpine AS build
WORKDIR /build
# dependências primeiro: enquanto o package-lock.json não muda, o npm ci vem do cache
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY . .
RUN npx ng build

# Estágio 2: só os arquivos estáticos num nginx que já roda como não-root (porta 8080).
FROM nginxinc/nginx-unprivileged:1.31.2-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist/web/browser /usr/share/nginx/html
EXPOSE 8080
