FROM python:3.11-slim
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PYTHONPATH=/app
WORKDIR /app
COPY ragagent/requirements.txt /tmp/requirements.txt
RUN pip install --no-cache-dir -r /tmp/requirements.txt
COPY ragagent /app/ragagent
RUN useradd --create-home --uid 10001 agent && chown -R agent:agent /app
USER agent
EXPOSE 8001
CMD ["python", "-m", "uvicorn", "ragagent.main:app", "--host", "0.0.0.0", "--port", "8001"]
