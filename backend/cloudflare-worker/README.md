# Yomi Cloud Conversion Middleman Proxy (Cloudflare Worker)

This Cloudflare Worker sits between the Yomi Android app and iLovePDF / CloudConvert so that **zero API keys** are compiled into your distributed Android APK.

## How It Works
1. **Offline-First on Phone**: Yomi always runs its local Apache POI engine first (0 credits).
2. **Middleman Proxy on Failover**: If an offline conversion fails while online, Yomi sends the document bytes to `POST /convert` on this Worker.
3. **Server-Side Secrets & Failover**:
   - Enforces the **5 conversions/day** limit per user/IP.
   - Tries **iLovePDF Primary → iLovePDF Backup → CloudConvert Primary → CloudConvert Backup** using encrypted Cloudflare Secrets.
   - Streams the converted `application/pdf` back to the phone.
4. **Automatic APK Key Stripping**: In `app/build.gradle.kts`, as soon as `conversion.proxy.url` is set in `local.properties`, Gradle automatically blanks out all 5 API key fields in `BuildConfig` (`""`) so decompiling the APK reveals zero secret keys.

---

## Quick 3-Step Setup (Free Cloudflare Account)

### Step 1: Log in to Cloudflare (One-Time)
```powershell
cd d:\yomi\backend\cloudflare-worker
npx wrangler login
```

### Step 2: Deploy & Upload Secrets Automatically
Run the included PowerShell script—it reads your existing keys from `d:\yomi\local.properties` and uploads them into Cloudflare's encrypted secret store:
```powershell
.\deploy-proxy.ps1
```

### Step 3: Add Your Worker URL to `d:\yomi\local.properties`
Copy the `.workers.dev` URL printed by Wrangler and add one line to `d:\yomi\local.properties`:
```properties
conversion.proxy.url=https://yomi-conversion-proxy.<your-subdomain>.workers.dev
```

Rebuild the APK (`.\gradlew.bat assembleDebug`), and your APK will use the Cloudflare Worker proxy with **zero API keys embedded in the APK**.

