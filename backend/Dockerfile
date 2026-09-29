# CloudPocket VM - Containerized Cloud Computer Environment
# Lightweight Alpine Linux base with X11, Openbox, Chromium, and Node.js
FROM alpine:3.19

# Install lightweight X11, window manager, Chromium browser, input tools, and Node.js
RUN apk add --no-cache \
    xvfb \
    xdotool \
    openbox \
    chromium \
    font-noto \
    nodejs \
    npm \
    bash \
    procps \
    imagemagick

WORKDIR /app

# Install backend dependencies
COPY package*.json ./
RUN npm install --production

# Copy backend source
COPY . .

# Environment configuration
ENV DISPLAY=:99
ENV SCREEN_WIDTH=854
ENV SCREEN_HEIGHT=480
ENV SCREEN_DEPTH=16
ENV PORT=8080
ENV CPVM_AUTH_TOKEN=cpvm_demo_token_sec

EXPOSE 8080

# Start script running Xvfb virtual framebuffer, Openbox, Chromium, and CloudPocket Server
CMD ["bash", "-c", "Xvfb :99 -screen 0 ${SCREEN_WIDTH}x${SCREEN_HEIGHT}x${SCREEN_DEPTH} & sleep 1 && openbox & sleep 1 && chromium-browser --no-sandbox --disable-gpu --window-size=${SCREEN_WIDTH},${SCREEN_HEIGHT} --kiosk 'https://google.com' & node server.js"]
