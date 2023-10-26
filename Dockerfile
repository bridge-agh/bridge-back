FROM sbtscala/scala-sbt:eclipse-temurin-jammy-17.0.8.1_1_1.9.7_3.3.1

RUN apt-get update && apt-get install -y \
    python3 \
    python3-pip \
    python-is-python3 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY . .

RUN pip3 install --no-cache-dir -r requirements.txt

EXPOSE 8000

CMD [ "sbt", "run" ]
