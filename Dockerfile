FROM --platform=arm64 python:3.10-slim

WORKDIR /app

# setup build environment
RUN pip install --no-cache-dir poetry && \
    poetry config virtualenvs.create false

# install dependencies
COPY poetry.lock pyproject.toml ./
RUN poetry install --only main --no-root --no-interaction --no-ansi --no-cache

# install app
COPY . .
RUN poetry install --only-root --no-interaction --no-ansi --no-cache

CMD [ "uvicorn", "bridge_back.main:app", "--host", "0.0.0.0", "--port", "8080" ]
