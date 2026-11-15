// Variables used by Scriptable.
// These must be at the very top of the file. Do not edit.
// icon-color: deep-blue; icon-glyph: bell;

/**
 * ============================================================================
 * 🔔 NotifAula para iOS (Scriptable) — v1.0.0
 * ============================================================================
 * Recibe notificaciones del Aula Virtual de la UAL (aulavirtual.ual.es) en tu
 * iPhone y ábrelas directamente en Safari con un toque.
 *
 * 100 % Privada y sin servidores intermedios:
 * - Te autenticas directamente en https://aulavirtual.ual.es
 * - El estado se guarda únicamente en tu iPhone (FileManager local + Keychain)
 * - Licencia MIT · Proyecto estudiantil independiente no oficial
 * ============================================================================
 */

const HOST = "aulavirtual.ual.es";
const BASE_URL = `https://${HOST}`;
const ULTRA_URL = `${BASE_URL}/ultra/`;
const STATE_FILE = "notifaula-state.json";
const KEYCHAIN_USER_KEY = "notifaula_user_info";
const MIN_AUTO_INTERVAL_MS = 5 * 60 * 1000; // Intervalo mínimo automático: 5 minutos

const LOWERCASE_WORDS = new Set([
  "de", "del", "la", "las", "el", "los", "y", "e", "o", "u", "a", "en", "con", "por", "para", "al"
]);
const ROMAN_NUMERALS = new Set(["i", "ii", "iii", "iv", "v", "vi"]);

// ───────────────────────── Almacenamiento local ─────────────────────────

const fm = FileManager.local();
const statePath = fm.joinPath(fm.documentsDirectory(), STATE_FILE);

function loadState() {
  if (!fm.fileExists(statePath)) {
    return { seen: {}, history: [], lastPoll: 0, runCounter: 0 };
  }
  try {
    return JSON.parse(fm.readString(statePath));
  } catch (e) {
    return { seen: {}, history: [], lastPoll: 0, runCounter: 0 };
  }
}

function saveState(state) {
  if (state.history && state.history.length > 100) {
    state.history = state.history.slice(0, 100);
  }
  fm.writeString(statePath, JSON.stringify(state));
}

function getUserInfo() {
  if (!Keychain.contains(KEYCHAIN_USER_KEY)) return null;
  try {
    return JSON.parse(Keychain.get(KEYCHAIN_USER_KEY));
  } catch (e) {
    return null;
  }
}

function saveUserInfo(info) {
  Keychain.set(KEYCHAIN_USER_KEY, JSON.stringify(info));
}

function clearAllData() {
  if (fm.fileExists(statePath)) fm.remove(statePath);
  if (Keychain.contains(KEYCHAIN_USER_KEY)) Keychain.remove(KEYCHAIN_USER_KEY);
}

// ───────────────────────── Formato y utilidades ─────────────────────────

function shortCourseName(full) {
  if (!full) return "";
  const parts = full.split(" - ").map(s => s.trim()).filter(Boolean);
  let candidate = full;
  if (parts.length >= 2 && parts[0].startsWith("[")) {
    candidate = parts[1];
  } else if (parts.length > 0) {
    candidate = parts[0];
  }
  const words = candidate.toLowerCase().split(/\s+/).filter(Boolean);
  return words.map((w, idx) => {
    if (ROMAN_NUMERALS.has(w)) return w.toUpperCase();
    if (idx > 0 && LOWERCASE_WORDS.has(w)) return w;
    return w.charAt(0).toUpperCase() + w.slice(1);
  }).join(" ").slice(0, 60);
}

function htmlToText(html, maxChars = 450) {
  if (!html) return "";
  return String(html)
    .replace(/<br\s*\/?>|<\/p>|<\/div>|<\/li>/gi, "\n")
    .replace(/<[^>]+>/g, "")
    .replace(/&nbsp;/g, " ")
    .replace(/&amp;/g, "&")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/[ \t]+/g, " ")
    .replace(/\n\s*\n+/g, "\n")
    .trim()
    .slice(0, maxChars);
}

function fmtNum(n) {
  if (n === null || n === undefined) return "";
  const num = Number(n);
  if (Number.isNaN(num)) return String(n);
  if (num % 1 === 0) return String(Math.trunc(num));
  return num.toFixed(2).replace(/\.?0+$/, "").replace(".", ",");
}

function prettyDate(isoStr) {
  if (!isoStr) return "";
  try {
    const d = new Date(isoStr);
    return d.toLocaleString("es-ES", {
      weekday: "short",
      day: "numeric",
      month: "short",
      hour: "2-digit",
      minute: "2-digit"
    });
  } catch (e) {
    return isoStr;
  }
}

function timeAgo(ts) {
  if (!ts) return "nunca";
  const diff = Math.max(0, Date.now() - ts);
  const mins = Math.floor(diff / 60000);
  const hours = Math.floor(diff / 3600000);
  const days = Math.floor(diff / 86400000);
  if (diff < 60000) return "ahora mismo";
  if (mins < 60) return `hace ${mins} min`;
  if (hours < 24) return `hace ${hours} h`;
  if (days === 1) return "hace 1 día";
  return `hace ${days} días`;
}

// ───────────────────────── Notificaciones nativas iOS ─────────────────────────

async function sendNotification(item) {
  const n = new Notification();
  n.identifier = item.id;
  n.title = item.title;
  if (item.courseName) n.subtitle = item.courseName;
  n.body = item.body || item.courseName || "";
  n.openURL = item.url;
  n.sound = "default";
  n.threadIdentifier = "notifaula";
  await n.schedule();
}

async function sendSessionExpiredNotification() {
  const n = new Notification();
  n.identifier = "notifaula_session_expired";
  n.title = "⚠️ Sesión del Aula Virtual caducada";
  n.body = "Abre Scriptable → NotifAula para volver a iniciar sesión.";
  n.openURL = "scriptable:///run/" + encodeURIComponent(Script.name());
  n.sound = "default";
  await n.schedule();
}

// ───────────────────────── Login y Motor WebKit ─────────────────────────

/**
 * Abre la web oficial de aulavirtual.ual.es para que el usuario inicie sesión
 * y verifica la sesión contra /learn/api/v1/users/me.
 */
async function interactiveLogin() {
  const wv = new WebView();
  await wv.loadURL(ULTRA_URL);
  await wv.present(false);

  // Al cerrar el WebView tras entrar en /ultra, verificamos la sesión dentro de WebKit
  let me = null;
  try {
    me = await evaluateApiInWebView(wv, "/learn/api/v1/users/me");
  } catch (e) {
    me = null;
  }
  if (!me || !me.id) {
    try {
      await wv.loadURL(`${BASE_URL}/learn/api/v1/utilities/timeUntilBbSessionInactive?forceLogout=false`);
      me = await evaluateApiInWebView(wv, "/learn/api/v1/users/me");
    } catch (e) {
      me = null;
    }
  }
  if (!me || !me.id) {
    const a = new Alert();
    a.title = "Sesión no detectada";
    a.message = "Inicia sesión hasta que cargue la página principal de tus cursos en el Aula Virtual (https://aulavirtual.ual.es/ultra) y vuelve a intentarlo.";
    a.addAction("Entendido");
    await a.present();
    return null;
  }

  const given = (me.name && me.name.given) || "";
  const family = (me.name && me.name.family) || "";
  const displayName = `${given} ${family}`.trim() || me.userName || me.id;
  const info = { userId: me.id, displayName };
  saveUserInfo(info);
  return info;
}

/**
 * Ejecuta una petición autenticada a la API interna de Blackboard Ultra
 * desde el contexto del WebView de Scriptable (usando completion() + null final
 * requerido por WKWebView en iOS cuando useCallback = true).
 */
async function evaluateApiInWebView(wv, pathAndQuery) {
  const fullUrl = pathAndQuery.startsWith("http")
    ? pathAndQuery
    : `${BASE_URL}${pathAndQuery.startsWith("/") ? "" : "/"}${pathAndQuery}`;
  const js = `
    (function() {
      const done = (typeof completion === 'function') ? completion : function(v) { return v; };
      (async function() {
        try {
          const headers = { 'Accept': 'application/json, text/plain, */*' };
          const decodedCookie = decodeURIComponent(document.cookie || '');
          const m = decodedCookie.match(/xsrf:([0-9a-fA-F-]{16,64})/);
          if (m && m[1]) {
            headers['X-Blackboard-XSRF'] = m[1];
          }
          const res = await fetch(${JSON.stringify(fullUrl)}, {
            method: 'GET',
            credentials: 'include',
            headers: headers
          });
          if (res.status === 401 || res.status === 403) {
            done(JSON.stringify({ __authError: true, status: res.status }));
            return;
          }
          if (!res.ok) {
            done(JSON.stringify({ __httpError: true, status: res.status }));
            return;
          }
          const text = await res.text();
          if (text.trim().startsWith('<')) {
            done(JSON.stringify({ __authError: true, status: 401 }));
            return;
          }
          done(text);
        } catch (err) {
          done(JSON.stringify({ __networkError: true, message: String(err) }));
        }
      })();
    })();
    null;
  `;
  const raw = await wv.evaluateJavaScript(js, true);
  if (!raw) return null;
  const parsed = typeof raw === "string" ? JSON.parse(raw) : raw;
  if (parsed && parsed.__authError) {
    throw new Error("SESSION_EXPIRED");
  }
  if (parsed && (parsed.__httpError || parsed.__networkError)) {
    return null;
  }
  return parsed;
}

/**
 * Ejecuta un lote de peticiones GET en paralelo dentro de una única llamada
 * a WKWebView.evaluateJavaScript (evitando colisiones de completion en iOS).
 */
async function evaluateBatchInWebView(wv, paths) {
  if (!paths.length) return [];
  const fullUrls = paths.map(p =>
    p.startsWith("http") ? p : `${BASE_URL}${p.startsWith("/") ? "" : "/"}${p}`
  );
  const js = `
    (function() {
      const done = (typeof completion === 'function') ? completion : function(v) { return v; };
      (async function() {
        try {
          const headers = { 'Accept': 'application/json, text/plain, */*' };
          const decodedCookie = decodeURIComponent(document.cookie || '');
          const m = decodedCookie.match(/xsrf:([0-9a-fA-F-]{16,64})/);
          if (m && m[1]) {
            headers['X-Blackboard-XSRF'] = m[1];
          }
          const urls = ${JSON.stringify(fullUrls)};
          const results = await Promise.all(urls.map(async function(u) {
            try {
              const r = await fetch(u, { method: 'GET', credentials: 'include', headers: headers });
              if (!r.ok) return null;
              return await r.json();
            } catch (e) {
              return null;
            }
          }));
          done(JSON.stringify(results));
        } catch (err) {
          done("[]");
        }
      })();
    })();
    null;
  `;
  const raw = await wv.evaluateJavaScript(js, true);
  if (!raw) return [];
  try {
    return typeof raw === "string" ? JSON.parse(raw) : raw;
  } catch (e) {
    return [];
  }
}

// ───────────────────────── Sincronización completa ─────────────────────────

function diff(state, scope, items, keyFn, fpFn, buildNotificationFn) {
  const baselineKey = `init:${scope}`;
  if (!state.seen[baselineKey]) {
    for (const item of items) {
      state.seen[keyFn(item)] = fpFn(item);
    }
    state.seen[baselineKey] = "1";
    return [];
  }

  const out = [];
  for (const item of items) {
    const k = keyFn(item);
    const fp = fpFn(item);
    const oldFp = state.seen[k];
    if (oldFp === undefined || oldFp !== fp) {
      const notif = buildNotificationFn(item, oldFp);
      if (notif) out.push(notif);
      state.seen[k] = fp;
    }
  }
  return out;
}

async function runSync() {
  const state = loadState();
  const wv = new WebView();
  // Cargamos un recurso ligero del mismo origen para inicializar el contexto WebKit de cookies
  await wv.loadURL(`${BASE_URL}/learn/api/v1/utilities/timeUntilBbSessionInactive?forceLogout=false`);

  const me = await evaluateApiInWebView(wv, "/learn/api/v1/users/me");
  if (!me || !me.id) {
    throw new Error("SESSION_EXPIRED");
  }

  const userId = me.id;
  const given = (me.name && me.name.given) || "";
  const family = (me.name && me.name.family) || "";
  saveUserInfo({ userId, displayName: `${given} ${family}`.trim() || me.userName || userId });

  state.runCounter = (state.runCounter || 0) + 1;
  const fullContentScan = (state.runCounter % 4 === 1);

  // 1) Obtener asignaturas activas
  const membershipsResp = await evaluateApiInWebView(
    wv,
    `/learn/api/v1/users/${encodeURIComponent(userId)}/memberships?expand=course.effectiveAvailability,courseRole&includeCount=true&limit=10000`
  );
  const memberships = (membershipsResp && membershipsResp.results) || [];
  const courses = [];
  for (const m of memberships) {
    const c = m.course;
    if (!c) continue;
    const id = c.id || m.courseId;
    const avail = (m.isAvailable !== false) &&
      (c.effectiveAvailability !== false) &&
      (c.isAvailable !== false) &&
      (c.isClosed !== true);
    if (!id || !avail) continue;
    const name = c.displayName || c.name || id;
    courses.push({ id, name, shortName: shortCourseName(name) });
  }

  const newNotifications = [];

  for (const course of courses) {
    const cid = course.id;

    // 2) Anuncios
    const annResp = await evaluateApiInWebView(
      wv,
      `/learn/api/v1/courses/${encodeURIComponent(cid)}/announcements?limit=25&offset=0&sort=${encodeURIComponent("startDateRestriction(desc)")}`
    );
    const announcements = ((annResp && annResp.results) || []).filter(a => !a.isDraft);
    newNotifications.push(...diff(
      state,
      `ann:${cid}`,
      announcements,
      a => `ann:${cid}:${a.id}`,
      a => a.modifiedDate || "1",
      (a, oldFp) => {
        if (oldFp !== undefined) return null;
        if (a.readStatus && a.readStatus.isRead) return null;
        const bodyText = htmlToText(a.body && (a.body.rawText || a.body.displayText), 500);
        const dateStr = prettyDate(a.startDateRestriction || a.createdDate);
        const body = [bodyText, dateStr ? `🕒 Publicado: ${dateStr}` : ""].filter(Boolean).join("\n");
        return {
          id: `ann:${cid}:${a.id}`,
          type: "ANNOUNCEMENT",
          courseName: course.shortName,
          title: `📢 ${a.title || "Nuevo anuncio"}`,
          body: body || `Nuevo anuncio en ${course.shortName}`,
          url: `${ULTRA_URL}courses/${cid}/announcements/announcement-detail?courseId=${cid}&announcementId=${a.id}`,
          timestamp: Date.now()
        };
      }
    ));

    // 3) Calificaciones
    const gradesResp = await evaluateApiInWebView(
      wv,
      `/learn/api/v1/courses/${encodeURIComponent(cid)}/gradebook/grades?userId=${encodeURIComponent(userId)}&limit=100&offset=0&expand=${encodeURIComponent("lastAttempt,column")}&includeNoGradeItems=true`
    );
    const grades = ((gradesResp && gradesResp.results) || []).filter(g => {
      const col = g.column || {};
      return col.visible !== false && col.gradesReleased !== false;
    });

    function gradeScoreText(g) {
      const dg = g.displayGrade;
      if (!dg) return "";
      if (dg.score !== undefined && dg.score !== null) return fmtNum(dg.score);
      return dg.text || dg.letter || "";
    }

    newNotifications.push(...diff(
      state,
      `grade:${cid}`,
      grades,
      g => `grade:${cid}:${g.columnId}`,
      g => gradeScoreText(g),
      (g, oldScore) => {
        const nowScore = gradeScoreText(g);
        if (!nowScore) return null;
        if (oldScore === nowScore) return null;
        if ((g.hasBeenViewedByStudent || g.studentReviewedTimestamp) && !oldScore) return null;

        const col = g.column || {};
        const colName = col.effectiveColumnName ||
          (col.localizedColumnName && col.localizedColumnName.languageKey) ||
          col.columnName ||
          "Calificación";
        const possible = col.possible > 0 ? ` / ${fmtNum(col.possible)}` : "";
        const avg = g.averageScore > 0 ? `  (Media: ${fmtNum(g.averageScore)})` : "";
        const fbRaw = g.lastAttempt && g.lastAttempt.feedbackToUser &&
          (g.lastAttempt.feedbackToUser.rawText || g.lastAttempt.feedbackToUser.displayText);
        const fb = htmlToText(fbRaw, 300);

        const body = [
          `📊 Nota: ${nowScore}${possible}${avg}`,
          fb ? `💬 Comentario: ${fb}` : ""
        ].filter(Boolean).join("\n");

        const url = col.contentId
          ? `${ULTRA_URL}courses/${cid}/assessment/${col.contentId}/overview?courseId=${cid}`
          : `${ULTRA_URL}courses/${cid}/grades`;

        return {
          id: `grade:${cid}:${g.columnId}:${nowScore}`,
          type: "GRADE",
          courseName: course.shortName,
          title: `${oldScore ? "🎓 Nota actualizada: " : "🎓 Nueva nota: "}${colName}`,
          body,
          url,
          timestamp: Date.now()
        };
      }
    ));

    // 4) Contenidos (recorrido de árbol de carpetas)
    const isBaselineContent = !state.seen[`init:content:${cid}`];
    const collectedNodes = [];
    await walkCourseContent(wv, cid, "ROOT", "", 0, fullContentScan || isBaselineContent, state, collectedNodes);

    const contentNews = diff(
      state,
      `content:${cid}`,
      collectedNodes,
      n => `content:${n.item.id}`,
      n => String(n.item.modifiedDate || ""),
      (n, oldFp) => {
        if (oldFp !== undefined) return null;
        const it = n.item;
        const kind = contentTypeLabel(it.contentHandler || "");
        const desc = htmlToText(it.description || (it.body && (it.body.rawText || it.body.displayText)), 300);
        const body = [
          n.breadcrumb ? `📁 En: ${n.breadcrumb}` : "",
          desc
        ].filter(Boolean).join("\n") || `${kind} publicado en ${course.shortName}`;

        return {
          id: `content:${it.id}`,
          type: "CONTENT",
          courseName: course.shortName,
          title: `📄 ${kind}: ${it.title || "Nuevo material"}`,
          body,
          url: contentUrl(cid, it.id, it.contentHandler || ""),
          timestamp: Date.now()
        };
      }
    );

    if (contentNews.length > 3) {
      newNotifications.push({
        id: `content-batch:${cid}:${Date.now()}`,
        type: "CONTENT",
        courseName: course.shortName,
        title: `📄 ${contentNews.length} materiales nuevos en ${course.shortName}`,
        body: contentNews.slice(0, 5).map(n => `• ${n.title.replace("📄 ", "")}`).join("\n"),
        url: `${ULTRA_URL}courses/${cid}/outline`,
        timestamp: Date.now()
      });
    } else {
      newNotifications.push(...contentNews);
    }

    // 5) Mensajes por asignatura
    const convCounts = await evaluateApiInWebView(
      wv,
      `/learn/api/v1/courses/${encodeURIComponent(cid)}/conversations/counts`
    );
    if (convCounts && typeof convCounts.unreadCount === "number") {
      const unread = convCounts.unreadCount;
      const msgKey = `conv:course:${cid}`;
      const baseMsgKey = `init:${msgKey}`;
      if (!state.seen[baseMsgKey]) {
        state.seen[msgKey] = String(unread);
        state.seen[baseMsgKey] = "1";
      } else {
        const oldUnread = parseInt(state.seen[msgKey] || "0", 10) || 0;
        state.seen[msgKey] = String(unread);
        if (unread > oldUnread) {
          const delta = unread - oldUnread;
          const title = delta === 1
            ? `✉️ 1 mensaje nuevo en ${course.shortName}`
            : `✉️ ${delta} mensajes nuevos en ${course.shortName}`;
          const body = unread === 1
            ? `Tienes 1 conversación sin leer en ${course.shortName}`
            : `Tienes ${unread} conversaciones sin leer en ${course.shortName}`;
          newNotifications.push({
            id: `msg:${cid}:${Date.now()}`,
            type: "MESSAGE",
            courseName: course.shortName,
            title,
            body,
            url: `${ULTRA_URL}courses/${cid}/messages`,
            timestamp: Date.now()
          });
        }
      }
    }
  }

  // Enviar notificaciones nativas de iOS
  for (const item of newNotifications) {
    await sendNotification(item);
  }

  state.history = [...newNotifications, ...(state.history || [])].slice(0, 100);
  state.lastPoll = Date.now();
  saveState(state);

  return { coursesChecked: courses.length, newCount: newNotifications.length };
}

async function walkCourseContent(wv, cid, parentId, breadcrumb, depth, full, state, out) {
  if (depth > 2 || out.length > 400) return;
  const resp = await evaluateApiInWebView(
    wv,
    `/learn/api/v1/courses/${encodeURIComponent(cid)}/contents/${encodeURIComponent(parentId)}/children?@view=Summary&limit=200`
  );
  const children = (resp && resp.results) || [];
  const subFolders = [];
  for (const item of children) {
    out.push({ item, breadcrumb });
    if (isFolderItem(item) && item.id) {
      const nextCrumb = breadcrumb ? `${breadcrumb} › ${item.title || ""}` : (item.title || "");
      const oldFp = state.seen[`content:${item.id}`];
      const fp = String(item.modifiedDate || "");
      if (full || oldFp === undefined || oldFp !== fp) {
        subFolders.push({ id: item.id, crumb: nextCrumb });
      }
    }
  }
  if (subFolders.length > 0 && depth + 1 <= 2) {
    const paths = subFolders.map(
      f => `/learn/api/v1/courses/${encodeURIComponent(cid)}/contents/${encodeURIComponent(f.id)}/children?@view=Summary&limit=200`
    );
    const batchResults = await evaluateBatchInWebView(wv, paths);
    for (let i = 0; i < subFolders.length; i++) {
      const folder = subFolders[i];
      const subResp = batchResults[i];
      const subChildren = (subResp && subResp.results) || [];
      for (const subItem of subChildren) {
        out.push({ item: subItem, breadcrumb: folder.crumb });
      }
    }
  }
}

function isFolderItem(item) {
  const h = item.contentHandler || "";
  if (h === "resource/x-bb-folder" || h === "resource/x-bb-lesson") return true;
  if (item.contentDetail) {
    return Object.values(item.contentDetail).some(v => v && v.isFolder === true);
  }
  return false;
}

function contentTypeLabel(handler) {
  if (handler.includes("asmt") || handler.includes("assignment")) return "Nueva tarea";
  if (handler.includes("x-bb-test") || handler.includes("assessment")) return "Cuestionario";
  if (handler === "resource/x-bb-document") return "Documento";
  if (handler === "resource/x-bb-file") return "Archivo";
  if (handler === "resource/x-bb-externallink") return "Enlace";
  if (handler === "resource/x-bb-folder") return "Carpeta";
  if (handler === "resource/x-bb-lesson") return "Módulo";
  return "Material";
}

function contentUrl(cid, contentId, handler) {
  if (handler.includes("asmt") || handler.includes("assignment") || handler.includes("x-bb-test")) {
    return `${ULTRA_URL}courses/${cid}/assessment/${contentId}/overview?courseId=${cid}`;
  }
  if (handler === "resource/x-bb-document") {
    return `${ULTRA_URL}courses/${cid}/outline/edit/document/${contentId}?courseId=${cid}&view=content`;
  }
  return `${ULTRA_URL}courses/${cid}/outline`;
}

// ───────────────────────── Interfaz principal en Scriptable ─────────────────────────

async function showAppMenu() {
  const state = loadState();
  let user = getUserInfo();

  const alert = new Alert();
  alert.title = "🔔 NotifAula para iOS";
  if (user) {
    alert.message = `👤 ${user.displayName}\nÚltima comprobación: ${timeAgo(state.lastPoll)}\nAvisos en historial: ${(state.history || []).length}`;
    alert.addAction("🔄 Comprobar novedades ahora");
    alert.addAction("📜 Ver últimos avisos");
    alert.addAction("🔑 Volver a iniciar sesión");
    alert.addDestructiveAction("🗑️ Cerrar sesión y borrar datos");
    alert.addCancelAction("Cerrar");

    const choice = await alert.presentSheet();
    if (choice === 0) {
      try {
        const res = await runSync();
        const done = new Alert();
        done.title = "Comprobación completada";
        done.message = `Asignaturas revisadas: ${res.coursesChecked}\nNuevos avisos: ${res.newCount}`;
        done.addAction("OK");
        await done.present();
      } catch (e) {
        if (String(e.message).includes("SESSION_EXPIRED")) {
          await interactiveLogin();
        }
      }
    } else if (choice === 1) {
      await showHistoryMenu(state.history || []);
    } else if (choice === 2) {
      await interactiveLogin();
    } else if (choice === 3) {
      clearAllData();
    }
  } else {
    alert.message = "Inicia sesión en la web oficial del Aula Virtual (aulavirtual.ual.es) para activar los avisos en tu iPhone.";
    alert.addAction("🔑 Iniciar sesión en aulavirtual.ual.es");
    alert.addCancelAction("Cancelar");
    const choice = await alert.presentSheet();
    if (choice === 0) {
      const logged = await interactiveLogin();
      if (logged) {
        await runSync();
        const ok = new Alert();
        ok.title = "✅ ¡Listo!";
        ok.message = "Se ha registrado el estado actual de tus asignaturas. Configura una automatización en la app Atajos de iOS para comprobar en segundo plano.";
        ok.addAction("Perfecto");
        await ok.present();
      }
    }
  }
}

async function showHistoryMenu(history) {
  if (!history.length) {
    const a = new Alert();
    a.title = "Sin avisos recientes";
    a.message = "Todavía no se han recibido notificaciones nuevas.";
    a.addAction("Volver");
    await a.present();
    return;
  }
  const table = new UITable();
  table.showSeparators = true;
  for (const item of history.slice(0, 40)) {
    const row = new UITableRow();
    row.height = 76;
    const cell = row.addText(
      `${item.courseName ? `[${item.courseName}] ` : ""}${item.title}`,
      `${item.body || ""} · ${timeAgo(item.timestamp)}`
    );
    cell.titleFont = Font.boldSystemFont(15);
    cell.subtitleFont = Font.systemFont(13);
    cell.subtitleColor = Color.gray();
    row.onSelect = () => Safari.open(item.url);
    table.addRow(row);
  }
  await table.present(false);
}

// ───────────────────────── Widget de iOS y Punto de entrada ─────────────────────────

function buildWidget(state, user, sessionExpired) {
  const w = new ListWidget();
  w.backgroundColor = new Color("#0055A5");
  w.setPadding(14, 16, 14, 16);
  // Solicitar a iOS WidgetKit refresco tras 5 minutos
  w.refreshAfterDate = new Date(Date.now() + MIN_AUTO_INTERVAL_MS);

  const header = w.addText("🔔 NotifAula");
  header.font = Font.boldSystemFont(14);
  header.textColor = Color.white();
  w.addSpacer(6);

  if (sessionExpired || !user) {
    const msg = w.addText("Toca para iniciar sesión en aulavirtual.ual.es");
    msg.font = Font.systemFont(13);
    msg.textColor = Color.white();
    return w;
  }

  const latest = (state.history || [])[0];
  if (latest) {
    const courseTxt = w.addText(latest.courseName || "Aula Virtual");
    courseTxt.font = Font.boldSystemFont(11);
    courseTxt.textColor = new Color("#BFDBFE");
    const titleTxt = w.addText(latest.title || "");
    titleTxt.font = Font.boldSystemFont(13);
    titleTxt.textColor = Color.white();
    titleTxt.lineLimit = 2;
    w.url = latest.url;
  } else {
    const okTxt = w.addText("Todo al día · Sin avisos nuevos");
    okTxt.font = Font.systemFont(13);
    okTxt.textColor = Color.white();
  }

  w.addSpacer();
  const foot = w.addText(`Actualizado ${timeAgo(state.lastPoll)}`);
  foot.font = Font.systemFont(10);
  foot.textColor = new Color("#DBEAFE");
  return w;
}

async function main() {
  if (config.runsInApp) {
    await showAppMenu();
  } else if (config.runsInWidget) {
    // Ejecutado automáticamente por un Widget de Scriptable en la pantalla de inicio de iOS
    let expired = false;
    try {
      const state = loadState();
      if (!state.lastPoll || Date.now() - state.lastPoll >= MIN_AUTO_INTERVAL_MS) {
        await runSync();
      }
    } catch (e) {
      if (String(e.message).includes("SESSION_EXPIRED")) {
        expired = true;
        await sendSessionExpiredNotification();
      }
    }
    Script.setWidget(buildWidget(loadState(), getUserInfo(), expired));
  } else {
    // Ejecutado en segundo plano desde Atajos de iOS (Shortcuts) con control de intervalo de 5 min
    try {
      const state = loadState();
      if (!state.lastPoll || Date.now() - state.lastPoll >= MIN_AUTO_INTERVAL_MS) {
        await runSync();
      }
    } catch (e) {
      if (String(e.message).includes("SESSION_EXPIRED")) {
        await sendSessionExpiredNotification();
      }
    }
  }
  Script.complete();
}

await main();
