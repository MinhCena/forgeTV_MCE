package vn.forge.launcher;

/** Một dòng trong menu kính (menu ứng dụng hoặc Cài đặt). */
final class MenuItemSpec {
    static final int ACTION = 0, TOGGLE = 1, VALUE = 2, DANGER = 3, INFO = 4;

    final String label;
    final String value;    // chữ phụ ở bên phải (kiểu VALUE) hoặc mô tả nhỏ
    final int type;
    final boolean on;      // cho TOGGLE
    final Runnable action;

    MenuItemSpec(String label, String value, int type, boolean on, Runnable action) {
        this.label = label;
        this.value = value;
        this.type = type;
        this.on = on;
        this.action = action;
    }

    static MenuItemSpec action(String label, Runnable r) { return new MenuItemSpec(label, null, ACTION, false, r); }
    static MenuItemSpec danger(String label, Runnable r) { return new MenuItemSpec(label, null, DANGER, false, r); }
    static MenuItemSpec toggle(String label, boolean on, Runnable r) { return new MenuItemSpec(label, null, TOGGLE, on, r); }
    static MenuItemSpec value(String label, String value, Runnable r) { return new MenuItemSpec(label, value, VALUE, false, r); }
}
