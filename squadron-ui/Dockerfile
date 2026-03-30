# Squadron UI - Dockerfile
# When the Angular build is done locally (or in CI), this image simply serves
# the pre-built static assets via Nginx.
#
# To rebuild inside Docker, use Dockerfile.multistage instead.
FROM nginx:alpine
COPY dist/squadron-ui/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
