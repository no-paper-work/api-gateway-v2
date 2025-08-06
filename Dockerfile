services:
  # --- Application Services ---
  discovery-server:
    build: ./discovery-server # Assumes a discovery-server module with a Dockerfile
    container_name: discovery-server
    ports:
      - "8761:8761"
    networks:
      - app-network

  gateway:
    build: ./gateway
    container_name: gateway
    ports:
      - "8080:8080"
    depends_on:
      - discovery-server
      - otel-collector
    environment:
      # --- Agent Configuration ---
      - JAVA_TOOL_OPTIONS=-javaagent:pyroscope.jar -javaagent:opentelemetry-javaagent.jar
      - OTEL_SERVICE_NAME=gateway
      - OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317 # Send to collector
      - OTEL_METRICS_EXPORTER=otlp
      - PYROSCOPE_APPLICATION_NAME=gateway
      - PYROSCOPE_SERVER_ADDRESS=http://pyroscope:4040
      # --- Application Configuration ---
      - EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://discovery-server:8761/eureka/
    networks:
      - app-network

  # --- Observability Stack ---
  otel-collector:
    image: otel/opentelemetry-collector-contrib:latest
    container_name: otel-collector
    command: ["--config=/etc/otel-collector-config.yml"]
    volumes:
      - ./otel-collector-config.yml:/etc/otel-collector-config.yml
    ports:
      - "4317:4317" # OTLP gRPC
      - "4318:4318" # OTLP HTTP
    depends_on:
      - tempo
      - prometheus
    networks:
      - app-network

  prometheus:
    image: prom/prometheus:latest
    container_name: prometheus
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
    ports:
      - "9090:9090"
    networks:
      - app-network

  loki:
    image: grafana/loki:latest
    container_name: loki
    ports:
      - "3100:3100"
    networks:
      - app-network

  tempo:
    image: grafana/tempo:latest
    container_name: tempo
    ports:
      - "3101:3101" # Tempo uses different ports internally
    networks:
      - app-network

  pyroscope:
    image: grafana/pyroscope:latest
    container_name: pyroscope
    ports:
      - "4040:4040"
    networks:
      - app-network

  grafana:
    image: grafana/grafana:latest
    container_name: grafana
    ports:
      - "3000:3000"
    volumes:
      - ./grafana-datasources.yml:/etc/grafana/provisioning/datasources/datasources.yml
    depends_on:
      - prometheus
      - loki
      - tempo
      - pyroscope
    networks:
      - app-network

networks:
  app-network:
    driver: bridge