FROM sbtscala/scala-sbt:eclipse-temurin-jammy-17.0.8.1_1_1.9.7_3.3.1

RUN apt-get update && apt-get install -y \
    python3 \
    python3-pip \
    python-is-python3 \
    build-essential \
    git \
    && rm -rf /var/lib/apt/lists/*

RUN git clone https://github.com/bridge-agh/dds-ai.git /dds
RUN cp /dds/src/Makefiles/Makefile_linux_static /dds/src/Makefile
RUN cp /dds/examples/Makefiles/Makefile_linux /dds/examples/Makefile
RUN sed -i 's/THREADING\s*=\s*\$(THR_BOOST)\s*\$(THR_OPENMP)\s*\$(THR_STL)/THREADING\t= \$(THR_OPENMP) \$(THR_STL)/' /dds/src/Makefile
RUN cd /dds/src && make
RUN cp /dds/src/libdds.a /dds/examples/libdds.a
RUN cd /dds/examples && make SolveBoardPBN
RUN cp /dds/examples/SolveBoardPBN /usr/bin/SolveBoardPBN

RUN SolveBoardPBN N E 'N:QJ6.K652.J85.T98 873.J97.AT764.Q4 K5.T83.KQ9.A7652 AT942.AQ4.32.KJ3'

WORKDIR /app

COPY . .

RUN pip3 install --no-cache-dir 'jax[cpu]'
RUN pip3 install --no-cache-dir -r requirements.txt

EXPOSE 8000

CMD [ "sbt", "run" ]
