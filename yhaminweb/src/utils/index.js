/**
 * 通用工具函数
 */

/** 生成 uuid */
export function uuid() {
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === "x" ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

/** 简单数字递增 id（mock 用） */
let _seq = 1000;
export function nextId() {
  _seq += 1;
  return _seq;
}

/** 当前时间字符串 yyyy-MM-dd HH:mm:ss */
export function now() {
  return formatTime(new Date());
}

export function formatTime(date, fmt = "yyyy-MM-dd HH:mm:ss") {
  if (!date) return "";
  const d = date instanceof Date ? date : new Date(date);
  if (Number.isNaN(d.getTime())) return "";
  const map = {
    yyyy: d.getFullYear(),
    MM: String(d.getMonth() + 1).padStart(2, "0"),
    dd: String(d.getDate()).padStart(2, "0"),
    HH: String(d.getHours()).padStart(2, "0"),
    mm: String(d.getMinutes()).padStart(2, "0"),
    ss: String(d.getSeconds()).padStart(2, "0"),
  };
  return fmt.replace(/yyyy|MM|dd|HH|mm|ss/g, (m) => map[m]);
}

/** 深拷贝 */
export function deepClone(obj) {
  if (obj === null || typeof obj !== "object") return obj;
  if (typeof structuredClone === "function") return structuredClone(obj);
  return JSON.parse(JSON.stringify(obj));
}

/** 防抖 */
export function debounce(fn, delay = 200) {
  let timer = null;
  return function (...args) {
    if (timer) clearTimeout(timer);
    timer = setTimeout(() => fn.apply(this, args), delay);
  };
}

/** 下载文本/Blob 文件 */
export function downloadBlob(content, filename, type = "text/plain") {
  const blob =
    content instanceof Blob ? content : new Blob([content], { type });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}

/** 导出 JSON 为文件 */
export function exportJson(data, filename = "export.json") {
  downloadBlob(JSON.stringify(data, null, 2), filename, "application/json");
}

/** 导出 CSV */
export function exportCsv(rows, filename = "export.csv") {
  if (!rows || !rows.length) return;
  const keys = Object.keys(rows[0]);
  const csv = [
    keys.join(","),
    ...rows.map((r) =>
      keys.map((k) => `"${String(r[k] ?? "").replace(/"/g, '""')}"`).join(","),
    ),
  ].join("\n");
  downloadBlob(`\ufeff${csv}`, filename, "text/csv;charset=utf-8");
}
