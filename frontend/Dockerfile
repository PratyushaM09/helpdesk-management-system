# Containerizes the static frontend (Phase 5, Milestone 1) behind nginx.
# No build step exists for this frontend (plain HTML/CSS/JS, no bundler) —
# the "build" is simply copying the static tree into the nginx image.
FROM nginx:1.27-alpine

# Custom server block replaces the image's default.
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Runs before nginx starts; substitutes the real API base URL into
# js/core/config.js from the API_BASE_URL env var (see docker-compose.yml).
COPY docker/40-inject-api-base-url.sh /docker-entrypoint.d/40-inject-api-base-url.sh
RUN chmod +x /docker-entrypoint.d/40-inject-api-base-url.sh

# Static assets last, so the layers above (which change far less often)
# stay cached across rebuilds triggered by ordinary frontend edits. Copied
# explicitly (not `COPY . .`) so Dockerfile/nginx.conf/docker/ never end up
# served from the web root alongside the real app files.
COPY *.html /usr/share/nginx/html/
COPY css /usr/share/nginx/html/css
COPY js /usr/share/nginx/html/js
COPY assets /usr/share/nginx/html/assets

EXPOSE 80

HEALTHCHECK --interval=10s --timeout=5s --start-period=10s --retries=5 \
  CMD wget -q --spider http://localhost/login.html || exit 1
