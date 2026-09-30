FROM node:22-alpine

WORKDIR /app

COPY package.json package-lock.json* ./
RUN npm install --omit=dev

COPY src ./src

ENV PORT=8081
ENV RECEPTIONS_FILE=/app/data/receptions.json
RUN mkdir -p /app/data
VOLUME ["/app/data"]
EXPOSE 8081

CMD ["npm", "start"]
