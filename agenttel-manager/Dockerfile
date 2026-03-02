FROM python:3.11-slim

WORKDIR /app

# Install manager dependencies
COPY agenttel-manager/requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r requirements.txt

# Install the monitor package (so we can spawn it as a subprocess)
COPY agenttel-monitor/pyproject.toml /app/monitor/pyproject.toml
COPY agenttel-monitor/src/ /app/monitor/src/
COPY agenttel-monitor/monitor.yml /app/monitor/monitor.yml
RUN pip install --no-cache-dir /app/monitor

# Copy manager code
COPY agenttel-manager/manager.py /app/manager.py

CMD ["python", "manager.py"]
