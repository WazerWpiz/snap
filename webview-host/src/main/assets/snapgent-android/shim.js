// SPDX-License-Identifier: GPL-3.0-or-later
// shim.js - pengganti chrome.runtime + chrome.storage untuk Android WebView.
// Menggantikan background.js upstream: pesan dikirim ke Kotlin lewat window.SnapgentAndroid
// (@JavascriptInterface), balasan kembali lewat window.__snapgentResolve(id, json).
// Hanya chrome.runtime.sendMessage / onMessage dan chrome.storage.local yang dipakai core.
(function () {
  "use strict";
  if (window.__snapgentShim) return;
  window.__snapgentShim = true;

  var native = window.SnapgentAndroid || null;
  var pending = {};
  var nextId = 1;
  var listeners = [];
  var cache = {};

  if (native) {
    try {
      cache = JSON.parse(native.storageAll() || "{}") || {};
    } catch (e) {
      cache = {};
    }
  }

  function deferCallback(cb, value) {
    if (typeof cb === "function") setTimeout(function () { cb(value); }, 0);
    return Promise.resolve(value);
  }

  function send(message, cb) {
    var id = nextId++;
    // Chrome MV3: sendMessage selalu mengembalikan Promise; callback tetap didukung.
    return new Promise(function (resolve) {
      var done = function (resp) {
        if (typeof cb === "function") cb(resp);
        resolve(resp);
      };
      if (!native) {
        done({ ok: false, kind: "disconnected", error: "bridge Android tidak tersedia" });
        return;
      }
      pending[id] = done;
      try {
        native.request(id, JSON.stringify(message));
      } catch (e) {
        delete pending[id];
        done({ ok: false, kind: "disconnected", error: String(e) });
      }
    });
  }

  window.__snapgentResolve = function (id, json) {
    var cb = pending[id];
    delete pending[id];
    var resp;
    try {
      resp = JSON.parse(json);
    } catch (e) {
      resp = { ok: false, error: "balasan tidak valid" };
    }
    if (typeof cb === "function") {
      try { cb(resp); } catch (e) { console.error("[snapgent-android]", e); }
    }
  };

  window.__snapgentPush = function (json) {
    var msg;
    try { msg = JSON.parse(json); } catch (e) { return; }
    listeners.slice().forEach(function (fn) {
      try { fn(msg, {}, function () {}); } catch (e) { console.error("[snapgent-android]", e); }
    });
  };

  var runtime = {
    id: "snapgent-android",
    lastError: undefined,
    sendMessage: function (message, cb) {
      return send(message, function (resp) {
        runtime.lastError = undefined;
        if (typeof cb === "function") cb(resp);
      });
    },
    onMessage: {
      addListener: function (fn) { if (typeof fn === "function") listeners.push(fn); },
      removeListener: function (fn) {
        var index = listeners.indexOf(fn);
        if (index >= 0) listeners.splice(index, 1);
      },
      hasListener: function (fn) { return listeners.indexOf(fn) >= 0; }
    }
  };

  function selectKeys(keys) {
    if (keys == null) return Object.keys(cache);
    if (typeof keys === "string") return [keys];
    if (Array.isArray(keys)) return keys;
    return Object.keys(keys);
  }

  var local = {
    get: function (keys, cb) {
      var out = {};
      selectKeys(keys).forEach(function (key) {
        if (Object.prototype.hasOwnProperty.call(cache, key)) {
          out[key] = cache[key];
        } else if (keys && typeof keys === "object" && !Array.isArray(keys) && Object.prototype.hasOwnProperty.call(keys, key)) {
          out[key] = keys[key];
        }
      });
      return deferCallback(cb, out);
    },
    set: function (items, cb) {
      var data = items || {};
      Object.keys(data).forEach(function (key) { cache[key] = data[key]; });
      if (native) {
        try { native.storageWrite(JSON.stringify(data)); } catch (e) { console.error("[snapgent-android]", e); }
      }
      return deferCallback(cb, undefined);
    },
    remove: function (keys, cb) {
      var removed = selectKeys(keys).filter(function (key) {
        return Object.prototype.hasOwnProperty.call(cache, key);
      });
      removed.forEach(function (key) { delete cache[key]; });
      // Persistensikan penghapusan: tanpanya kunci muncul lagi setelah muat ulang.
      if (native && removed.length) {
        try { native.storageRemove(JSON.stringify(removed)); } catch (e) { console.error("[snapgent-android]", e); }
      }
      return deferCallback(cb, undefined);
    }
  };

  window.chrome = window.chrome || {};
  window.chrome.runtime = runtime;
  window.chrome.storage = { local: local, sync: local };
})();
