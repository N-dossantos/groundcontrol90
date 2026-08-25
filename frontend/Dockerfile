FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Corre como usuario sin privilegios. La imagen oficial baja de privilegios sólo los
# workers: el proceso maestro sigue siendo root para poder bindear el 80. Acá el
# proceso entero corre como `nginx`, que ya existe en la imagen (uid 101), a cambio de
# escuchar en 8080 (ver nginx.conf) y de mover el pid a una ruta que ese usuario pueda
# escribir. El puerto no importa hacia afuera: Caddy expone el 80/443 y hace proxy.
RUN sed -i '/^user  *nginx;/d' /etc/nginx/nginx.conf \
 && sed -i 's|^pid  *.*;|pid /tmp/nginx.pid;|' /etc/nginx/nginx.conf \
 && chown -R nginx:nginx /var/cache/nginx /usr/share/nginx/html /etc/nginx/conf.d
USER nginx

EXPOSE 8080
