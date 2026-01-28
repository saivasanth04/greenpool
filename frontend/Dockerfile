FROM node:20-alpine as build
WORKDIR /app
COPY . .
RUN npm install
ENV REACT_APP_API_URL=http://backend:8080  

FROM nginx:alpine
COPY dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]