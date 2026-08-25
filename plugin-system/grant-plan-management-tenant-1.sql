-- 为租户 1 的 admin 角色授权“套餐管理”。
-- MySQL 脚本，可重复执行，不会重复创建菜单或角色菜单关联。

START TRANSACTION;

SET @tenant_id := 1;
SET @role_key := 'admin';

INSERT INTO sys_menu
    (parent_id, module_id, menu_name, menu_type, order_num, path, component,
     icon, perms, status, visible, remark)
SELECT parent_menu.id, parent_menu.module_id, '套餐管理', 'C', 95,
       'plan', 'system/plan/index', 'Tickets', 'system:plan:list',
       '0', '0', 'SaaS 套餐版本、权益和配额管理'
FROM sys_menu parent_menu
JOIN sys_module system_module
  ON system_module.id = parent_menu.module_id
 AND system_module.module_code = 'system'
 AND system_module.status = '0'
WHERE parent_menu.parent_id = 0
  AND parent_menu.menu_type = 'M'
  AND (parent_menu.menu_name = '系统管理' OR COALESCE(parent_menu.path, '') = '')
  AND parent_menu.status = '0'
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu existing
      WHERE existing.perms = 'system:plan:list'
  )
ORDER BY parent_menu.id
LIMIT 1;

INSERT INTO sys_menu
    (parent_id, module_id, menu_name, menu_type, order_num, path, component,
     icon, perms, status, visible, remark)
SELECT plan_menu.id, plan_menu.module_id, permission_def.menu_name, 'F',
       permission_def.order_num, '', '', '', permission_def.perms,
       '0', '1', '套餐管理按钮权限'
FROM sys_menu plan_menu
JOIN (
    SELECT '套餐查询' AS menu_name, 'system:plan:query' AS perms, 1 AS order_num
    UNION ALL SELECT '套餐新增', 'system:plan:add', 2
    UNION ALL SELECT '套餐编辑', 'system:plan:edit', 3
    UNION ALL SELECT '套餐发布', 'system:plan:publish', 4
    UNION ALL SELECT '套餐删除', 'system:plan:remove', 5
    UNION ALL SELECT '租户订阅管理', 'system:tenant:subscription', 6
) permission_def
WHERE plan_menu.perms = 'system:plan:list'
  AND NOT EXISTS (
      SELECT 1 FROM sys_menu existing
      WHERE existing.perms = permission_def.perms
  );

INSERT INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT @tenant_id, role_item.id, menu_item.id
FROM sys_role role_item
JOIN sys_menu menu_item
  ON menu_item.perms IN (
      'system:plan:list',
      'system:plan:query',
      'system:plan:add',
      'system:plan:edit',
      'system:plan:publish',
      'system:plan:remove',
      'system:tenant:subscription'
  )
WHERE role_item.tenant_id = @tenant_id
  AND role_item.role_key = @role_key
  AND role_item.status = '0'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu existing
      WHERE existing.tenant_id = @tenant_id
        AND existing.role_id = role_item.id
        AND existing.menu_id = menu_item.id
  );

COMMIT;

-- 正常应返回 7 条权限记录。
SELECT role_item.id AS role_id,
       role_item.role_name,
       menu_item.menu_name,
       menu_item.perms
FROM sys_role role_item
JOIN sys_role_menu relation
  ON relation.tenant_id = role_item.tenant_id
 AND relation.role_id = role_item.id
JOIN sys_menu menu_item ON menu_item.id = relation.menu_id
WHERE role_item.tenant_id = @tenant_id
  AND role_item.role_key = @role_key
  AND menu_item.perms IN (
      'system:plan:list',
      'system:plan:query',
      'system:plan:add',
      'system:plan:edit',
      'system:plan:publish',
      'system:plan:remove',
      'system:tenant:subscription'
  )
ORDER BY menu_item.id;
