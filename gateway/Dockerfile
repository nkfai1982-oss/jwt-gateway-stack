FROM openresty/openresty:1.27.1.2-alpine-fat@sha256:73cc954163a45c44584e57d59ab46a4f0e5359b7d08689edabd18f6eb9f1cb8c

RUN opm get ledgetech/lua-resty-http \
    && opm get fffonion/lua-resty-openssl \
    && opm get anvouk/lua-resty-jwt-verification \
    && apk add --no-cache curl

COPY nginx.conf /usr/local/openresty/nginx/conf/nginx.conf
COPY lua/ /usr/local/openresty/nginx/lua/

EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=5 \
  CMD curl --fail --silent http://127.0.0.1:8080/healthz || exit 1
