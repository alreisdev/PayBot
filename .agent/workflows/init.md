---
description: Initialize the PayBot project for Gemini
---

This workflow ensures the development environment is ready for Gemini.

1. Check for required files
   - `README.md`
   - `docker-compose.yml`
   - `.env`
   - `credentials/paybot-credentials.json`

2. Verify Docker status
// turbo
3. `docker ps`

4. Validate current setup
// turbo
5. `docker-compose ps`

6. Perform a clean start if requested
// turbo
7. `docker-compose down -v`
// turbo
8. `docker-compose up --build -d`

Wait for all services to become healthy before reporting success.
