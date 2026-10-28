FROM node:22-alpine

WORKDIR /app
COPY server/ ./server/
COPY dist/ /usr/share/nginx/html/
ENV FRONTEND_DIST_DIR=/usr/share/nginx/html

HEALTHCHECK --interval=30s --timeout=3s \
  CMD wget --quiet --spider http://127.0.0.1/ || exit 1

CMD ["node", "/app/server/index.mjs"]
