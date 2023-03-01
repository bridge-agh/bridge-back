# Bridge Backend

API URL: <https://bridge-back-afhyegyfua-lm.a.run.app>

## Setup

Requirements:

- Python 3.10

Steps:

1. Install [Poetry](https://python-poetry.org)

    - Option 1: Global install (if you like Poetry)

      Follow [instructions](https://python-poetry.org/docs/).

    - Option 2: Just for this project

        1. Create a Python venv

        2. Install Poetry

           ```bash
           pip install poetry
           ```

2. Enter project directory

3. Install project dependencies

   ```bash
   poetry install
   ```

## Run server

From project directory:

```bash
poetry run uvicorn bridge_back.main:app --reload
```
