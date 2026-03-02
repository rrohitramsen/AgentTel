FROM python:3.12-slim

WORKDIR /app

# Copy agenttel-cli (dependency) and agenttel-instrument
COPY agenttel-cli/ /app/agenttel-cli/
COPY agenttel-instrument/ /app/agenttel-instrument/

# Install both packages
RUN pip install --no-cache-dir /app/agenttel-cli /app/agenttel-instrument

COPY agenttel-instrument/instrument.yml /app/instrument.yml

EXPOSE 8082

CMD ["agenttel-instrument", "--config", "/app/instrument.yml"]
