# Smart Email Writer UI

React 18 + Vite 6 + Material UI workspace for the Smart Email Writer API.

## Run

```powershell
npm install
Copy-Item .env.example .env   # optional
npm run dev                   # http://localhost:5173
```

`npm run build` creates a production bundle; `npm run lint` checks the source.

## Configuration

```
VITE_API_BASE_URL=http://localhost:8080
```

Start the backend first with `..\backend\.mvnw.cmd spring-boot:run`. See the root `README.md` for the complete walkthrough.
