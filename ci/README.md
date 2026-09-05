# Local Jenkins and SonarQube

This setup runs Jenkins and SonarQube in Docker Desktop. Jenkins can build the
Maven artifacts, build all service images, scan the workspace, and deploy the
images to the Docker Desktop Kubernetes context `docker-desktop`.

## Start

1. Copy `ci/.env.ci.example` to `ci/.env.ci`.
2. Set a local Jenkins administrator password and a SonarQube user token.
3. Run from `selfcare-platform`:

```powershell
.\ci\start-local-ci.ps1
```

The script retries image pulls, waits for SonarQube health, and starts Jenkins.
The first SonarQube login is normally `admin` / `admin`; create a token under
the user account and put it in `ci/.env.ci` before starting Jenkins.

## Jenkins job

Create a Pipeline job pointing at the GitHub repository and select **Pipeline
script from SCM** with script path `Jenkinsfile`. The Jenkinsfile configures a
GitHub push trigger and a five-minute SCM polling fallback. For a private
repository, add the GitHub token to Jenkins as an HTTPS username/password
credential and select it in the job SCM configuration.

GitHub cannot deliver a webhook to `localhost`; use the SCM polling fallback
for local Docker Desktop, or configure a public reverse proxy and set the
Jenkins webhook URL in GitHub.

The pipeline intentionally blocks image build and Kubernetes deployment when
unit tests, frontend/mobile QA, Sonar quality gates, or security scans fail.
