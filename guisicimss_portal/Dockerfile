# Stage 1: Compile and Build angular codebase

# Use official node image as the base image
#FROM image-registry.openshift-image-registry.svc:5000/openshift/nodejs:16-ubi9 as builder
FROM node:16 as builder

# Set the working directory
WORKDIR /app

USER root

# Add the source code to app
COPY package.json ./

# Install all the dependencies
RUN npm install

COPY . .

# Generate the build of the application
RUN npm run build -- --configuration development --output-path=/dist

# Stage 2: Serve app with nginx server

# Use official nginx image as the base image
#FROM image-registry.openshift-image-registry.svc:5000/openshift/nginx-118:1-90
FROM nginx:1.18

# TEST
USER root

COPY nginx.conf /etc/nginx/nginx.conf

# RUN rm -rf /usr/share/nginx/html/*
# COPY --from=builder /dist /usr/share/nginx/html
RUN rm -rf /opt/app-root/src/*
COPY --from=builder /dist /opt/app-root/src

# Expose port 8080
EXPOSE 8080

ENTRYPOINT ["nginx", "-g", "daemon off;"]
