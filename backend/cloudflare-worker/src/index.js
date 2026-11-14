/**
 * Yomi Cloud Conversion Middleman Proxy (Cloudflare Worker)
 *
 * Keeps iLovePDF and CloudConvert API keys strictly inside Cloudflare encrypted secrets
 * so zero API keys need to be compiled into the Android APK.
 *
 * Endpoints:
 *   GET  /health  -> Health status & configured provider slot count (never exposes keys)
 *   POST /convert -> Accepts raw document bytes + X-File-Name header, enforces 5/day limit,
 *                    and runs 4-stage failover (iLovePDF Primary -> iLovePDF Backup ->
 *                    CloudConvert Primary -> CloudConvert Backup), returning application/pdf.
 */

const memoryQuotaMap = new Map();
const memoryCooldownMap = new Map();
const COOLDOWN_MS = 12 * 60 * 60 * 1000; // 12 hours

function currentUtcDay() {
  return new Date().toISOString().slice(0, 10);
}

function isQuotaError(status, text) {
  if (status === 402 || status === 429) return true;
  if (status === 400 || status === 401 || status === 403) {
    const lower = (text || "").toLowerCase();
    return [
      "credit",
      "credits",
      "quota",
      "limit",
      "exceeded",
      "exhausted",
      "insufficient",
      "remaining",
      "balance"
    ].some((kw) => lower.includes(kw));
  }
  return false;
}

function isSlotAvailable(slotId) {
  const until = memoryCooldownMap.get(slotId) || 0;
  return Date.now() >= until;
}

function markSlotExhausted(slotId) {
  memoryCooldownMap.set(slotId, Date.now() + COOLDOWN_MS);
}

async function getDailyUsage(env, quotaKey) {
  if (env.YOMI_QUOTA_KV) {
    const raw = await env.YOMI_QUOTA_KV.get(quotaKey);
    return raw ? parseInt(raw, 10) || 0 : 0;
  }
  return memoryQuotaMap.get(quotaKey) || 0;
}

async function incrementDailyUsage(env, quotaKey) {
  const next = (await getDailyUsage(env, quotaKey)) + 1;
  if (env.YOMI_QUOTA_KV) {
    await env.YOMI_QUOTA_KV.put(quotaKey, String(next), { expirationTtl: 86400 });
  } else {
    memoryQuotaMap.set(quotaKey, next);
  }
  return next;
}

function splitKeys(...sources) {
  const out = [];
  const seen = new Set();
  for (const src of sources) {
    if (!src) continue;
    for (const part of String(src).split(/[,;\n]/)) {
      const clean = part.trim();
      if (clean && !seen.has(clean)) {
        seen.add(clean);
        out.push(clean);
      }
    }
  }
  return out;
}

async function convertViaIlovePdf(publicKey, fileBytes, fileName) {
  // 1. Authenticate
  const authRes = await fetch("https://api.ilovepdf.com/v1/auth", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ public_key: publicKey })
  });
  if (!authRes.ok) {
    const errText = await authRes.text();
    const err = new Error(`iLovePDF auth failed (${authRes.status}): ${errText}`);
    err.quotaExhausted = isQuotaError(authRes.status, errText);
    throw err;
  }
  const { token } = await authRes.json();

  // 2. Start officepdf task
  const startRes = await fetch("https://api.ilovepdf.com/v1/start/officepdf", {
    method: "GET",
    headers: { Authorization: `Bearer ${token}` }
  });
  if (!startRes.ok) {
    const errText = await startRes.text();
    const err = new Error(`iLovePDF start failed (${startRes.status}): ${errText}`);
    err.quotaExhausted = isQuotaError(startRes.status, errText);
    throw err;
  }
  const { server, task } = await startRes.json();

  // 3. Upload file
  const form = new FormData();
  form.append("task", task);
  form.append(
    "file",
    new Blob([fileBytes], { type: "application/octet-stream" }),
    fileName
  );
  const uploadRes = await fetch(`https://${server}/v1/upload`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}` },
    body: form
  });
  if (!uploadRes.ok) {
    const errText = await uploadRes.text();
    const err = new Error(`iLovePDF upload failed (${uploadRes.status}): ${errText}`);
    err.quotaExhausted = isQuotaError(uploadRes.status, errText);
    throw err;
  }
  const { server_filename } = await uploadRes.json();

  // 4. Process task
  const processRes = await fetch(`https://${server}/v1/process`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json"
    },
    body: JSON.stringify({
      task,
      tool: "officepdf",
      files: [{ server_filename, filename: fileName }]
    })
  });
  if (!processRes.ok) {
    const errText = await processRes.text();
    const err = new Error(`iLovePDF process failed (${processRes.status}): ${errText}`);
    err.quotaExhausted = isQuotaError(processRes.status, errText);
    throw err;
  }

  // 5. Download PDF
  const dlRes = await fetch(`https://${server}/v1/download/${task}`, {
    method: "GET",
    headers: { Authorization: `Bearer ${token}` }
  });
  if (!dlRes.ok) {
    const errText = await dlRes.text();
    throw new Error(`iLovePDF download failed (${dlRes.status}): ${errText}`);
  }
  return await dlRes.arrayBuffer();
}

async function convertViaCloudConvert(apiKey, fileBytes, fileName) {
  const ext = (fileName.split(".").pop() || "docx").toLowerCase();
  // 1. Create Job
  const jobRes = await fetch("https://api.cloudconvert.com/v2/jobs", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${apiKey}`,
      "Content-Type": "application/json"
    },
    body: JSON.stringify({
      tasks: {
        "import-file": { operation: "import/upload" },
        "convert-file": {
          operation: "convert",
          input: ["import-file"],
          input_format: ext,
          output_format: "pdf"
        },
        "export-file": {
          operation: "export/url",
          input: ["convert-file"],
          inline: false,
          archive_multiple_files: false
        }
      }
    })
  });
  if (!jobRes.ok) {
    const errText = await jobRes.text();
    const err = new Error(`CloudConvert job creation failed (${jobRes.status}): ${errText}`);
    err.quotaExhausted = isQuotaError(jobRes.status, errText);
    throw err;
  }
  const jobJson = await jobRes.json();
  const jobId = jobJson.data.id;
  const importTask = jobJson.data.tasks.find((t) => t.name === "import-file");
  const uploadUrl = importTask.result.form.url;
  const uploadParams = importTask.result.form.parameters;

  // 2. Upload file to CloudConvert storage
  const form = new FormData();
  for (const [k, v] of Object.entries(uploadParams)) {
    form.append(k, String(v));
  }
  form.append(
    "file",
    new Blob([fileBytes], { type: "application/octet-stream" }),
    fileName
  );
  const upRes = await fetch(uploadUrl, { method: "POST", body: form });
  if (!upRes.ok) {
    throw new Error(`CloudConvert upload failed (${upRes.status})`);
  }

  // 3. Wait for job completion
  const waitRes = await fetch(`https://sync.api.cloudconvert.com/v2/jobs/${jobId}`, {
    method: "GET",
    headers: { Authorization: `Bearer ${apiKey}` }
  });
  if (!waitRes.ok) {
    const errText = await waitRes.text();
    const err = new Error(`CloudConvert job wait failed (${waitRes.status}): ${errText}`);
    err.quotaExhausted = isQuotaError(waitRes.status, errText);
    throw err;
  }
  const waitJson = await waitRes.json();
  const exportTask = waitJson.data.tasks.find((t) => t.name === "export-file");
  const fileUrl = exportTask?.result?.files?.[0]?.url;
  if (!fileUrl) {
    throw new Error("CloudConvert did not return an exported PDF URL.");
  }

  // 4. Download PDF
  const dlRes = await fetch(fileUrl);
  if (!dlRes.ok) {
    throw new Error(`CloudConvert download failed (${dlRes.status})`);
  }
  return await dlRes.arrayBuffer();
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (request.method === "GET" && (url.pathname === "/" || url.pathname === "/health")) {
      const iloveCount = splitKeys(env.ILOVEPDF_PUBLIC_KEY, env.ILOVEPDF_BACKUP_PUBLIC_KEY).length;
      const ccCount = splitKeys(env.CLOUDCONVERT_API_KEY, env.CLOUDCONVERT_BACKUP_API_KEY).length;
      return Response.json({
        status: "ok",
        service: "yomi-conversion-proxy",
        configuredSlots: {
          ilovepdf: iloveCount,
          cloudconvert: ccCount
        }
      });
    }

    if (request.method !== "POST" || url.pathname !== "/convert") {
      return Response.json({ error: "Not found. Use POST /convert" }, { status: 404 });
    }

    // Optional shared client token verification
    if (env.YOMI_CLIENT_TOKEN) {
      const clientToken = request.headers.get("X-Yomi-Client-Token") || "";
      if (clientToken !== env.YOMI_CLIENT_TOKEN) {
        return Response.json({ error: "Unauthorized client token" }, { status: 401 });
      }
    }

    // Daily rate limit (5 conversions/day per user/IP)
    const dailyLimit = parseInt(env.DAILY_CONVERSION_LIMIT || "5", 10) || 5;
    const ip = request.headers.get("CF-Connecting-IP") || "anon";
    const userScope = request.headers.get("X-User-Scope") || "guest";
    const quotaKey = `quota:${currentUtcDay()}:${ip}:${userScope}`;
    const usedToday = await getDailyUsage(env, quotaKey);

    if (usedToday >= dailyLimit) {
      return Response.json(
        { error: `Daily cloud conversion limit (${dailyLimit}/day) reached.` },
        { status: 429 }
      );
    }

    const rawFileName = request.headers.get("X-File-Name") || "document.docx";
    const fileName = decodeURIComponent(rawFileName);
    const fileBytes = await request.arrayBuffer();
    if (!fileBytes || fileBytes.byteLength === 0) {
      return Response.json({ error: "Uploaded file body is empty." }, { status: 400 });
    }

    const iloveKeys = splitKeys(env.ILOVEPDF_PUBLIC_KEY, env.ILOVEPDF_BACKUP_PUBLIC_KEY);
    const ccKeys = splitKeys(env.CLOUDCONVERT_API_KEY, env.CLOUDCONVERT_BACKUP_API_KEY);

    if (iloveKeys.length === 0 && ccKeys.length === 0) {
      return Response.json(
        { error: "No cloud conversion API keys configured on proxy server." },
        { status: 503 }
      );
    }

    let lastError = null;

    // Stage 1 & 2: iLovePDF Primary + Backup
    for (let i = 0; i < iloveKeys.length; i++) {
      const slotId = `ilove_${i}`;
      if (!isSlotAvailable(slotId)) continue;
      try {
        const pdfBytes = await convertViaIlovePdf(iloveKeys[i], fileBytes, fileName);
        await incrementDailyUsage(env, quotaKey);
        const label = i === 0 ? "Cloud Proxy (iLovePDF Primary)" : "Cloud Proxy (iLovePDF Backup)";
        return new Response(pdfBytes, {
          status: 200,
          headers: {
            "Content-Type": "application/pdf",
            "X-Conversion-Provider": label
          }
        });
      } catch (err) {
        lastError = err;
        if (err.quotaExhausted) markSlotExhausted(slotId);
      }
    }

    // Stage 3 & 4: CloudConvert Primary + Backup
    for (let i = 0; i < ccKeys.length; i++) {
      const slotId = `cc_${i}`;
      if (!isSlotAvailable(slotId)) continue;
      try {
        const pdfBytes = await convertViaCloudConvert(ccKeys[i], fileBytes, fileName);
        await incrementDailyUsage(env, quotaKey);
        const label = i === 0 ? "Cloud Proxy (CloudConvert Primary)" : "Cloud Proxy (CloudConvert Backup)";
        return new Response(pdfBytes, {
          status: 200,
          headers: {
            "Content-Type": "application/pdf",
            "X-Conversion-Provider": label
          }
        });
      } catch (err) {
        lastError = err;
        if (err.quotaExhausted) markSlotExhausted(slotId);
      }
    }

    return Response.json(
      { error: lastError?.message || "All cloud conversion providers failed." },
      { status: 502 }
    );
  }
};

