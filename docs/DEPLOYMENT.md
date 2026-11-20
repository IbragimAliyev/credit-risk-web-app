# Deploying RiskLens to Render (free tier)

The repo includes a [`render.yaml`](../render.yaml) Blueprint that creates three things:

| Resource | Name | What it is |
|---|---|---|
| PostgreSQL | `risklens-db` | Free managed database |
| Web service | `risklens-api` | Spring Boot backend, built from `backend/Dockerfile` |
| Static site | `risklens-web` | React frontend, built with Vite |

## Before you start

- The code must be pushed to GitHub.
- Create a free account at <https://render.com> and sign in with GitHub.

## Steps

1. In the Render dashboard, click **New → Blueprint**.
2. Connect your GitHub account if asked, and select the **credit-risk-web-app** repository.
3. Render reads `render.yaml` and lists the three resources. It then asks for the two values marked `sync: false`:

   | Variable | Service | Value to enter |
   |---|---|---|
   | `CORS_ALLOWED_ORIGINS` | risklens-api | `https://risklens-web.onrender.com` |
   | `VITE_API_BASE_URL` | risklens-web | `https://risklens-api.onrender.com` |

   These are the URLs Render normally assigns from the service names. If a name is already taken, Render adds a suffix (e.g. `risklens-api-x7k2.onrender.com`). See *Fixing the URLs* below.
4. Click **Apply**. The first deploy takes about 5–10 minutes, because the backend image builds with Maven.
5. When all three show **Live**, open the `risklens-web` URL, register an account and submit an application.

## Fixing the URLs (if Render assigned different ones)

Each service's real URL is shown at the top of its page in the dashboard. If it differs from what you entered:

1. **risklens-api → Environment**: set `CORS_ALLOWED_ORIGINS` to the real frontend URL, then save. It redeploys automatically.
2. **risklens-web → Environment**: set `VITE_API_BASE_URL` to the real API URL, then save and use **Manual Deploy → Clear build cache & deploy**. Vite bakes this value in at build time, so a rebuild is required.

## Checks if something doesn't work

| Symptom | Likely cause / fix |
|---|---|
| Frontend says "Cannot reach the server" | `VITE_API_BASE_URL` is wrong, or the API is still waking up (see free-tier notes). |
| Browser console shows a CORS error | `CORS_ALLOWED_ORIGINS` doesn't exactly match the frontend URL (must be `https://`, no trailing `/`). |
| Refreshing `/login` gives "Not Found" | The rewrite rule wasn't applied. In **risklens-web → Redirects/Rewrites**, add: Source `/*`, Destination `/index.html`, Action **Rewrite**. |
| API deploy fails health check | Check **risklens-api → Logs**. `https://<api-url>/actuator/health` should return `{"status":"UP"}`. |

## Free-tier limitations

- **The API sleeps after 15 minutes without traffic.** The first request after that takes about a minute while it wakes up. It's worth mentioning this next to the demo link in the README.
- **Free PostgreSQL expires 30 days after creation**, with a 14-day grace period before deletion. To keep the demo running, recreate the database or upgrade it before then.
- The free tier includes 750 instance hours per month, which is enough for one always-on service.

## Updating the demo

With auto-deploy enabled (the default), every push to `main` redeploys the changed services automatically.
