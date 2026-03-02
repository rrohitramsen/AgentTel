FROM python:3.11-slim

WORKDIR /app

# Copy project files
COPY pyproject.toml .
COPY src/ src/
COPY monitor.yml .

# Install the package
RUN pip install --no-cache-dir .

# Run the monitor
CMD ["python", "-m", "agenttel_monitor"]
