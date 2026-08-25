/**
 * Mock 请求分发器
 * 拦截 request() 调用，根据 url + method 返回对应 mock 数据
 * 支持：列表分页 / 详情 / 新增 / 修改 / 删除 / 批量删除 / 自定义动作
 */
import * as db from "./db";

const TOKEN = "mock-token-admin-" + Date.now();

/** 通用响应 */
const ok = (data = null, msg = "操作成功") => ({ code: 200, msg, data });
const fail = (msg = "操作失败", code = 500) => ({ code, msg, data: null });
// 与异火后端约定一致：分页数据 rows/total 直接挂在顶层（而非 data 字段内）
const page = (rows, total) => ({ code: 200, msg: "查询成功", rows, total });

/** 模拟网络延迟 */
const delay = (ms = 120) => new Promise((r) => setTimeout(r, ms));

/** 从 url 提取路径（去 query / baseUrl） */
function parseUrl(url = "") {
  let u = url.split("?")[0];
  u = u.replace(/^\/?(dev-api|prod-api)\/?/, "");
  u = u.replace(/^\/+/, "").replace(/\/+$/, "");
  return u;
}

/** 从 url 或 params 提取 query */
function parseQuery(url = "", params = {}) {
  const q = { ...params };
  const idx = url.indexOf("?");
  if (idx >= 0) {
    new URLSearchParams(url.slice(idx + 1)).forEach((v, k) => {
      q[k] = v;
    });
  }
  return q;
}

/** 列表分页过滤 */
function paged(list, query) {
  let arr = [...list];
  // 通用关键字过滤
  if (query.keyword) {
    const kw = String(query.keyword).toLowerCase();
    arr = arr.filter((row) =>
      Object.values(row).some((v) => String(v).toLowerCase().includes(kw)),
    );
  }
  // 状态过滤
  if (
    query.status !== undefined &&
    query.status !== "" &&
    query.status !== null
  ) {
    arr = arr.filter((row) => String(row.status) === String(query.status));
  }
  // 部门过滤（用户管理）
  if (query.deptId) {
    arr = arr.filter((row) => {
      const dept = db.deptList.find((d) => d.id === Number(query.deptId));
      if (!dept) return false;
      return row.deptName?.includes(dept.label);
    });
  }
  // 时间范围（默认按 createTime，可由调用方覆盖 beginTime/endTime 到对应字段）
  if (query.beginTime) {
    arr = arr.filter(
      (row) =>
        (row.createTime || row.operTime || row.loginTime || "") >=
        query.beginTime,
    );
  }
  if (query.endTime) {
    arr = arr.filter(
      (row) =>
        (row.createTime || row.operTime || row.loginTime || "") <=
        query.endTime + " 23:59:59",
    );
  }
  const pageNum = Number(query.pageNum) || 1;
  const pageSize = Number(query.pageSize) || 10;
  const total = arr.length;
  const start = (pageNum - 1) * pageSize;
  const rows = arr.slice(start, start + pageSize);
  return page(rows, total);
}

/** 通用字段过滤（按指定字段集合做 substring 匹配 / 精确匹配） */
function fieldPaged(
  list,
  query,
  textFields = [],
  exactFields = [],
  timeField = "createTime",
) {
  let arr = [...list];
  textFields.forEach((f) => {
    if (query[f] !== undefined && query[f] !== "" && query[f] !== null) {
      const v = String(query[f]).toLowerCase();
      arr = arr.filter((row) =>
        String(row[f] || "")
          .toLowerCase()
          .includes(v),
      );
    }
  });
  exactFields.forEach((f) => {
    if (query[f] !== undefined && query[f] !== "" && query[f] !== null) {
      arr = arr.filter((row) => String(row[f]) === String(query[f]));
    }
  });
  if (query.beginTime) {
    arr = arr.filter((row) => (row[timeField] || "") >= query.beginTime);
  }
  if (query.endTime) {
    arr = arr.filter(
      (row) => (row[timeField] || "") <= query.endTime + " 23:59:59",
    );
  }
  const pageNum = Number(query.pageNum) || 1;
  const pageSize = Number(query.pageSize) || 10;
  const total = arr.length;
  const start = (pageNum - 1) * pageSize;
  const rows = arr.slice(start, start + pageSize);
  return page(rows, total);
}

/** 通用 CRUD 工厂 */
function crudHandlers(list, nameKey, idKey = "id") {
  return {
    list: (q) => paged(list, q),
    get: (id) => ok(list.find((x) => x[idKey] === Number(id)) || null),
    add: (data) => {
      const id = db.nextId();
      const row = { [idKey]: id, createTime: db.now(), status: "0", ...data };
      list.unshift(row);
      return ok(row, "新增成功");
    },
    update: (id, data) => {
      const idx = list.findIndex((x) => x[idKey] === Number(id));
      if (idx === -1) return fail(`${nameKey}不存在`);
      list[idx] = { ...list[idx], ...data, [idKey]: Number(id) };
      return ok(list[idx], "修改成功");
    },
    remove: (ids) => {
      const idArr = String(ids).split(",").map(Number);
      idArr.forEach((id) => {
        const idx = list.findIndex((x) => x[idKey] === id);
        if (idx !== -1) list.splice(idx, 1);
      });
      return ok(null, "删除成功");
    },
    changeStatus: (id, status) => {
      const row = list.find((x) => x[idKey] === Number(id));
      if (row) row.status = status;
      return ok(null, "状态修改成功");
    },
  };
}

/** 各模块 CRUD 实例 */
const handlers = {
  user: crudHandlers(db.users, "用户"),
  role: crudHandlers(db.roles, "角色"),
  module: crudHandlers(db.modules, "模块"),
  menu: crudHandlers(db.menus, "菜单"),
  dict: crudHandlers(db.dicts, "字典"),
  dictData: crudHandlers(db.dictData, "字典数据"),
  config: crudHandlers(db.configs, "参数"),
  notice: crudHandlers(db.notices, "通知"),
  operlog: crudHandlers(db.operlogs, "操作日志"),
  logininfor: crudHandlers(db.logininfors, "登录日志"),
  model: crudHandlers(db.aiModels, "模型"),
  provider: crudHandlers(db.aiModelProviders, "模型提供商"),
  knowledge: crudHandlers(db.knowledges, "知识库"),
  tools: crudHandlers(db.tools, "工具"),
  skills: crudHandlers(db.skills, "技能"),
  mcp: crudHandlers(db.mcps, "MCP服务"),
  workflow: crudHandlers(db.workflows, "工作流"),
  agent: crudHandlers(db.agents, "Agent"),
  bot: crudHandlers(db.bots, "Bot"),
};

/** 路由表：[method, regex, handler] */
const routes = [];

function register(method, pattern, handler) {
  routes.push([method.toUpperCase(), pattern, handler]);
}

// 登录
register("POST", "/login", async () => ok({ token: TOKEN }, "登录成功"));
register("GET", "/getInfo", async () =>
  ok(
    {
      user: {
        id: 1,
        username: "admin",
        nickname: "异火管理员",
        avatar: "",
        email: "admin@yh.com",
        phone: "15888888888",
      },
      roles: ["admin"],
      permissions: ["*"],
    },
    "获取成功",
  ),
);
register("POST", "/logout", async () => ok(null, "退出成功"));
register("GET", "/captchaImage", async () =>
  ok({ captchaEnabled: true, uuid: db.uuid(), img: "" }, "获取验证码成功"),
);

// Dashboard 统计
register("GET", "/dashboard/stats", async () => ok(db.dashboardStats));

// 部门树（特殊：返回树形结构）
register("GET", "/system/dept/tree", async () => ok(db.deptTree));
register("GET", "/system/dept/list", async (q) => {
  // 部门也走扁平列表
  let arr = [...db.deptList];
  if (q.deptName) arr = arr.filter((x) => x.label.includes(q.deptName));
  return ok(arr);
});

// 角色下拉
register("GET", "/system/role/list", async (q) => paged(db.roles, q));
register("GET", "/system/role/optionselect", async () =>
  ok(db.roles.map((r) => ({ id: r.id, roleName: r.roleName }))),
);

// 角色分配用户：获取已授权用户
register("GET", "/system/role/authUser/:roleId", async (q, params) => {
  const roleId = Number(params.roleId);
  const userIds = db.roleUsers
    .filter((ru) => ru.roleId === roleId)
    .map((ru) => ru.userId);
  const users = db.users.filter((u) => userIds.includes(u.id));
  return ok(users);
});

// 角色分配用户：保存授权
register("PUT", "/system/role/authUser", async (q, params, body) => {
  const roleId = Number(body.roleId);
  const userIds = Array.isArray(body.userIds) ? body.userIds.map(Number) : [];
  db.roleUsers.splice(
    0,
    db.roleUsers.length,
    ...db.roleUsers.filter((ru) => ru.roleId !== roleId),
  );
  userIds.forEach((userId) => {
    db.roleUsers.push({ roleId, userId });
  });
  return ok(null, "分配成功");
});

// 角色取消授权：单个用户
register(
  "DELETE",
  "/system/role/authUser/:roleId/:userId",
  async (q, params) => {
    const roleId = Number(params.roleId);
    const userId = Number(params.userId);
    const idx = db.roleUsers.findIndex(
      (ru) => ru.roleId === roleId && ru.userId === userId,
    );
    if (idx !== -1) db.roleUsers.splice(idx, 1);
    return ok(null, "取消授权成功");
  },
);

// 角色菜单授权：获取角色已授权菜单
register("GET", "/system/role/authMenu/:roleId", async (q, params) => {
  const roleId = Number(params.roleId);
  const menuIds = db.roleMenus
    .filter((rm) => rm.roleId === roleId)
    .map((rm) => rm.menuId);
  return ok(menuIds);
});

// 角色菜单授权：保存授权
register("PUT", "/system/role/authMenu", async (q, params, body) => {
  const roleId = Number(body.roleId);
  const menuIds = Array.isArray(body.menuIds) ? body.menuIds.map(Number) : [];
  db.roleMenus.splice(
    0,
    db.roleMenus.length,
    ...db.roleMenus.filter((rm) => rm.roleId !== roleId),
  );
  menuIds.forEach((menuId) => {
    db.roleMenus.push({ roleId, menuId });
  });
  return ok(null, "授权成功");
});

// 菜单树
register("GET", "/system/menu/treeselect", async () =>
  ok(buildMenuTree(db.menus)),
);
register("GET", "/system/menu/list", async (q) => {
  let arr = [...db.menus];
  if (q.menuName) arr = arr.filter((x) => x.menuName.includes(q.menuName));
  return ok(arr);
});

// 字典数据
register("GET", "/system/dict/data/type/:dictType", async (q, params) => {
  const map = {
    sys_normal_disable: [
      { dictLabel: "正常", dictValue: "0", listClass: "success" },
      { dictLabel: "停用", dictValue: "1", listClass: "danger" },
    ],
    sys_user_sex: [
      { dictLabel: "男", dictValue: "0", listClass: "primary" },
      { dictLabel: "女", dictValue: "1", listClass: "success" },
    ],
    ai_model_type: [
      { dictLabel: "对话", dictValue: "chat", listClass: "primary" },
      { dictLabel: "向量", dictValue: "embedding", listClass: "success" },
      { dictLabel: "重排", dictValue: "rerank", listClass: "warning" },
    ],
    ai_model_status: [
      { dictLabel: "启用", dictValue: "0", listClass: "success" },
      { dictLabel: "停用", dictValue: "1", listClass: "danger" },
    ],
    sys_notice_type: [
      { dictLabel: "公告", dictValue: "1", listClass: "success" },
      { dictLabel: "通知", dictValue: "2", listClass: "info" },
    ],
    sys_yes_no: [
      { dictLabel: "是", dictValue: "Y", listClass: "primary" },
      { dictLabel: "否", dictValue: "N", listClass: "info" },
    ],
  };
  return ok(map[params.dictType] || []);
});

// 字典数据（按 dictType 过滤）
register("GET", "/system/dict/data/list", async (q) => {
  let arr = [...db.dictData];
  if (q.dictType) arr = arr.filter((x) => x.dictType === q.dictType);
  if (q.dictLabel) arr = arr.filter((x) => x.dictLabel.includes(q.dictLabel));
  if (q.status !== undefined && q.status !== "")
    arr = arr.filter((x) => String(x.status) === String(q.status));
  const pageNum = Number(q.pageNum) || 1;
  const pageSize = Number(q.pageSize) || 10;
  const total = arr.length;
  const start = (pageNum - 1) * pageSize;
  return page(arr.slice(start, start + pageSize), total);
});

// 操作日志：自定义字段过滤（title/operName 模糊，businessType/status 精确，operTime 范围）
register("GET", "/monitor/operlog/list", async (q) =>
  fieldPaged(
    db.operlogs,
    q,
    ["title", "operName"],
    ["businessType", "status"],
    "operTime",
  ),
);
// 操作日志：清空
register("DELETE", "/monitor/operlog/clean", async () => {
  db.operlogs.length = 0;
  return ok(null, "清空成功");
});

// 登录日志：自定义字段过滤（ipaddr/userName 模糊，status 精确，loginTime 范围）
register("GET", "/monitor/logininfor/list", async (q) =>
  fieldPaged(
    db.logininfors,
    q,
    ["ipaddr", "userName"],
    ["status"],
    "loginTime",
  ),
);
// 登录日志：清空
register("DELETE", "/monitor/logininfor/clean", async () => {
  db.logininfors.length = 0;
  return ok(null, "清空成功");
});
// 登录日志：解锁账号
register("PUT", "/monitor/logininfor/unlock", async () =>
  ok(null, "账号解锁成功"),
);

// 通用 CRUD 路由：/module/list, /module/{id}, /module, /module/{ids}
Object.keys(handlers).forEach((mod) => {
  // list (GET)
  register("GET", `/${modPath(mod)}/list`, async (q) => handlers[mod].list(q));
  // get by id
  register("GET", `/${modPath(mod)}/:id`, async (q, params) =>
    handlers[mod].get(params.id),
  );
  // add (POST)
  register("POST", `/${modPath(mod)}`, async (q, params, body) =>
    handlers[mod].add(body),
  );
  // update (PUT)
  register("PUT", `/${modPath(mod)}`, async (q, params, body) =>
    handlers[mod].update(body.id, body),
  );
  // delete (DELETE)
  register("DELETE", `/${modPath(mod)}/:ids`, async (q, params) =>
    handlers[mod].remove(params.ids),
  );
  // changeStatus (PUT)
  register("PUT", `/${modPath(mod)}/changeStatus`, async (q, params, body) =>
    handlers[mod].changeStatus(body.id, body.status),
  );
});

register("PUT", "/ai/model/:id/default", async (q, params) => {
  const selected = db.aiModels.find((item) => item.id === Number(params.id));
  if (!selected) return fail("模型不存在", 404);
  db.aiModels.forEach((item) => {
    if (item.modelType === selected.modelType)
      item.isDefault = item.id === selected.id ? "1" : "0";
  });
  return ok(null, "默认模型设置成功");
});

// 用户特有：重置密码 / 分配角色
register("PUT", "/system/user/resetPwd", async (q, p, body) =>
  ok(null, "密码重置成功"),
);
register("PUT", "/system/user/changeStatus", async (q, p, body) =>
  handlers.user.changeStatus(body.id, body.status),
);
register("GET", "/system/user/authRole/:userId", async (q, p) =>
  ok({ roles: [], total: 0 }),
);
register("PUT", "/system/user/authRole", async () => ok(null, "分配成功"));

// 导出（mock：返回空）
register("GET", "/:mod/export", async () => ok(null, "导出成功"));

function modPath(mod) {
  // 路径映射：user → system/user，model → ai/model 等
  const systemMods = [
    "user",
    "role",
    "module",
    "menu",
    "dict",
    "config",
    "notice",
    "dictData",
  ];
  const monitorMods = ["operlog", "logininfor"];
  const aiMods = [
    "model",
    "knowledge",
    "tools",
    "skills",
    "mcp",
    "workflow",
    "agent",
    "bot",
  ];
  if (mod === "provider") return "ai/model/provider";
  if (systemMods.includes(mod)) return `system/${mod}`;
  if (monitorMods.includes(mod)) return `monitor/${mod}`;
  if (aiMods.includes(mod)) return `ai/${mod}`;
  return mod;
}

/** 构建菜单树（用于角色授权） */
function buildMenuTree(flat) {
  const map = new Map();
  const roots = [];
  flat.forEach((m) => map.set(m.id, { ...m }));
  map.forEach((m) => {
    if (m.parentId && map.has(m.parentId)) {
      const parent = map.get(m.parentId);
      if (!parent.children) parent.children = [];
      parent.children.push(m);
    } else {
      roots.push(m);
    }
  });
  // 排序
  const sortRec = (arr) => {
    arr.sort((a, b) => (a.order || 0) - (b.order || 0));
    arr.forEach((n) => n.children?.length && sortRec(n.children));
  };
  sortRec(roots);
  return roots;
}

/** 路由匹配 */
function match(method, url) {
  const path = parseUrl(url);
  for (const [m, pattern, handler] of routes) {
    if (m !== method.toUpperCase()) continue;
    const { matched, params } = matchPattern(pattern, path);
    if (matched) return { handler, params };
  }
  return null;
}

function matchPattern(pattern, path) {
  const pSegs = pattern.split("/").filter(Boolean);
  const tSegs = path.split("/").filter(Boolean);
  if (pSegs.length !== tSegs.length) return { matched: false, params: {} };
  const params = {};
  for (let i = 0; i < pSegs.length; i++) {
    if (pSegs[i].startsWith(":")) {
      params[pSegs[i].slice(1)] = decodeURIComponent(tSegs[i]);
    } else if (pSegs[i] !== tSegs[i]) {
      return { matched: false, params: {} };
    }
  }
  return { matched: true, params };
}

/**
 * 对外：mock 请求入口
 * @param {Object} config - { url, method, params, data }
 */
export async function mockRequest(config = {}) {
  await delay();
  const method = (config.method || "GET").toUpperCase();
  const url = config.url || "";
  const query = parseQuery(url, config.params || {});
  const body = config.data || {};
  const matched = match(method, url);
  if (!matched) {
    console.warn("[Mock] 未匹配到路由：", method, url);
    return fail(`Mock 未实现该接口：${method} ${url}`, 404);
  }
  try {
    const data = await matched.handler(query, matched.params, body);
    return data;
  } catch (e) {
    console.error("[Mock] handler error:", e);
    return fail(e.message || "Mock 异常", 500);
  }
}
