package yh.hotplugin.system.domain.model;

/**
 * Read-only dynamic menu projection compatible with the existing frontend fields.
 */
public final class MenuItem {
    private final long id, parentId, moduleId;
    private final String menuName, menuType, path, component, icon, perms, visible;
    private final int orderNum;

    public MenuItem(long id, long parentId, long moduleId, String menuName, String menuType, int orderNum, String path, String component, String icon, String perms, String visible) {
        this.id = id;
        this.parentId = parentId;
        this.moduleId = moduleId;
        this.menuName = menuName;
        this.menuType = menuType;
        this.orderNum = orderNum;
        this.path = path;
        this.component = component;
        this.icon = icon;
        this.perms = perms;
        this.visible = visible;
    }

    public long getId() {
        return id;
    }

    public long getParentId() {
        return parentId;
    }

    public long getModuleId() {
        return moduleId;
    }

    public String getMenuName() {
        return menuName;
    }

    public String getMenuType() {
        return menuType;
    }

    public int getOrderNum() {
        return orderNum;
    }

    public String getPath() {
        return path;
    }

    public String getComponent() {
        return component;
    }

    public String getIcon() {
        return icon;
    }

    public String getPerms() {
        return perms;
    }

    public String getVisible() {
        return visible;
    }
}
