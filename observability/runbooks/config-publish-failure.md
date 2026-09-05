# Runbook: Config Publish Failure

## Symptoms
- Admin sees "publish failed" in Selfcare Studio
- Config Service logs show compilation error
- Mobile apps stuck on previous configVersion

## Quick checks

1. **What's the error message?**
   - Check config-tenant-service logs:
     ```bash
     kubectl -n omobio-prod logs -l app=config-tenant-service --since=10m | grep -i compile
     ```

2. **Was a new component introduced?**
   - "Component X is not registered" → submit app update OR revert config
   - Per ADR-009, new components must be registered in ComponentRegistry

3. **Did the JSON Schema fail?**
   - "JSON schema validation failed at /sections/3" → fix the section in the layout

## Common causes

### A. Unregistered component
**Symptom**: "Component 'NewWidget' is not registered"
**Fix**:
- Option 1: Use only registered components in the layout
- Option 2: Add the component to ComponentRegistry AND submit a new app version
- Option 3: Revert the layout to a known-good version

### B. Action type not in allowlist
**Symptom**: "Action type 'OPEN_RANDOM_URL' is not in allowlist"
**Fix**: Use a registered action (NAVIGATE, CALL_API, etc.) or extend ADR-009

### C. URL not in tenant allowlist
**Symptom**: "URL 'https://evil.com' is not in tenant allowlist"
**Fix**: Add the domain to the tenant's allowedDomains in config

### D. Broken theme reference
**Symptom**: "Theme 'theme-99' not found"
**Fix**: Either publish the theme first, or change the layout to reference an existing theme

## Rollback

If a publish succeeds but breaks the app, rollback in admin UI:
1. Admin → Configurations → Layouts
2. Select tenant + page
3. Click "Versions" tab
4. Click "Rollback" on a known-good version

The ConfigCompiler will re-publish the previous version and increment
configVersion. Apps will pick it up on their next manifest fetch.

## Escalation
If rollback fails, page the platform on-call.
