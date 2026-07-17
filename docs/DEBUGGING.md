# Debugging Tips

**Backend:** Enable SQL logging in `application.properties`:
```
logging.level.org.hibernate.SQL=DEBUG
```

**Frontend:** Check Vite proxy in DevTools Network tab — requests to `/api/...` should show as proxied to `localhost:8080`.

**SSE:** Use browser DevTools (Application > EventSource) to watch incoming messages in real-time.
