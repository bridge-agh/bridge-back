FROM python:3.10

WORKDIR /app
COPY . .
RUN pip3 install .

CMD [ "uvicorn", "bridge_back.main:app", "--host", "0.0.0.0", "--port", "${PORT}" ]
