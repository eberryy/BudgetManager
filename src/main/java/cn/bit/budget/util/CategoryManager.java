package cn.bit.budget.util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

/**
 * 分类管理器 (V3.0 - SQLite 驱动版)
 * 核心逻辑：DB 存储 + 内存缓存。支持级联删除和事务一致性。
 */
public class CategoryManager {

    private static final String DB_URL = "jdbc:sqlite:budget_manager.db";

    // 内存缓存：保持 UI 的毫秒级响应
    private static final Map<String, List<String>> CATEGORY_MAP = new LinkedHashMap<>();
    private static final Map<String, String> EMOJI_MAP = new HashMap<>();
    private static final Map<String, String> CATEGORY_TYPE_MAP = new HashMap<>();

    // 个性化指令依然保留为轻量级文本存储
    private static final String PERSONALIZATION_FILE = "user_personalization.txt";
    private static final List<String> PERSONALIZATIONS = new ArrayList<>();

    static {
        initDatabase();          // 初始化数据库表
        initDefaultCategories(); // 注入程序内置的基础分类
        loadFromDb();            // 从数据库加载用户自定义分类
        loadPersonalizations();  // 加载个性化指令
    }

    private static void initDatabase() {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            // 1. 一级分类表
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS categories (
                    name TEXT PRIMARY KEY,
                    type TEXT NOT NULL,
                    emoji TEXT
                );
            """);
            // 2. 二级分类表
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS sub_categories (
                    name TEXT NOT NULL,
                    parent_name TEXT NOT NULL,
                    emoji TEXT,
                    PRIMARY KEY (name, parent_name),
                    FOREIGN KEY (parent_name) REFERENCES categories(name) ON DELETE CASCADE
                );
            """);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static void loadFromDb() {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            // 加载一级分类
            try (ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM categories")) {
                while (rs.next()) {
                    String name = rs.getString("name");
                    String type = rs.getString("type");
                    String emoji = rs.getString("emoji");

                    CATEGORY_MAP.putIfAbsent(name, new ArrayList<>());
                    CATEGORY_TYPE_MAP.put(name, type);
                    if (emoji != null && !EMOJI_MAP.containsKey(name)) {
                        EMOJI_MAP.put(name, emoji);
                    }
                }
            }
            // 加载二级分类
            try (ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM sub_categories")) {
                while (rs.next()) {
                    String name = rs.getString("name");
                    String parent = rs.getString("parent_name");
                    String emoji = rs.getString("emoji");

                    if (CATEGORY_MAP.containsKey(parent)) {
                        List<String> children = CATEGORY_MAP.get(parent);
                        if (!children.contains(name)) children.add(name);
                        if (emoji != null && !EMOJI_MAP.containsKey(name)) {
                            EMOJI_MAP.put(name, emoji);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // --- 修改操作：同步更新 DB 和内存 ---
    public static void addCustomParentCategory(String parentName, String type) {
        // 1. 先检查是否真的不存在（决定是否写库）
        boolean isNew = !CATEGORY_MAP.containsKey(parentName);

        // 2. 无论是否新分类，都更新/同步内存中的类型映射
        CATEGORY_MAP.putIfAbsent(parentName, new ArrayList<>());
        CATEGORY_TYPE_MAP.put(parentName, type);

        // 3. 只有真正的新分类才执行 SQL 插入
        if (isNew) {
            String emoji = "\uD83C\uDFF7"; // 默认标签 🏷
            String sql = "INSERT OR IGNORE INTO categories(name, type, emoji) VALUES (?, ?, ?)";

            try (Connection conn = DriverManager.getConnection(DB_URL);
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, parentName);
                pstmt.setString(2, type);
                pstmt.setString(3, emoji);
                pstmt.executeUpdate();

                // 同步更新 Emoji 缓存
                if (!EMOJI_MAP.containsKey(parentName)) {
                    EMOJI_MAP.put(parentName, emoji);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public static void addCustomChildCategory(String parent, String childName) {
        if (CATEGORY_MAP.containsKey(parent)) {
            List<String> children = CATEGORY_MAP.get(parent);
            if (!children.contains(childName)) {
                String emoji = "\uD83C\uDFF7";
                String sql = "INSERT OR IGNORE INTO sub_categories(name, parent_name, emoji) VALUES (?, ?, ?)";

                try (Connection conn = DriverManager.getConnection(DB_URL);
                     PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, childName);
                    pstmt.setString(2, parent);
                    pstmt.setString(3, emoji);
                    pstmt.executeUpdate();

                    // 更新内存
                    children.add(childName);
                    if (!EMOJI_MAP.containsKey(childName)) {
                        EMOJI_MAP.put(childName, emoji);
                    }
                } catch (SQLException e) { e.printStackTrace(); }
            }
        }
    }

    public static boolean deleteParentCategory(String parentName) {
        if (isCustomCategory(parentName)) {
            String sql = "DELETE FROM categories WHERE name = ?";
            try (Connection conn = DriverManager.getConnection(DB_URL);
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, parentName);
                pstmt.executeUpdate();

                // 内存同步
                CATEGORY_MAP.remove(parentName);
                CATEGORY_TYPE_MAP.remove(parentName);
                return true;
            } catch (SQLException e) { e.printStackTrace(); }
        }
        return false;
    }

    /**
     * 删除指定的二级分类（仅限自定义分类）
     * @param parentName 一级分类名称
     * @param childName 要删除的二级分类名称
     * @return true 如果删除成功
     */
    public static boolean deleteChildCategory(String parentName, String childName) {
        // 1. 安全校验：防止删除系统内置的二级分类
        if (!isCustomChildCategory(parentName, childName)) {
            System.err.println("无法删除系统默认二级分类：" + childName);
            return false;
        }

        String sql = "DELETE FROM sub_categories WHERE name = ? AND parent_name = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, childName);
            pstmt.setString(2, parentName);
            int affectedRows = pstmt.executeUpdate();

            if (affectedRows > 0) {
                // 2. 同步更新内存缓存，保持 UI 实时刷新
                if (CATEGORY_MAP.containsKey(parentName)) {
                    CATEGORY_MAP.get(parentName).remove(childName);
                    EMOJI_MAP.remove(childName);
                }
                return true;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
    /**
     * 判断一级分类是否为自定义分类（非内置）
     * @param categoryName 分类名称
     * @return true 如果是用户后来添加的，允许删除和特殊标记
     */
    public static boolean isCustomCategory(String categoryName) {
        // 1. 定义程序所有默认的一级分类名单
        // 注意：这里的名单必须和你 initDefaultCategories() 里的保持绝对一致
        Set<String> defaultCategories = Set.of(
                "餐饮", "购物", "交通", "住宿", "日常", "学习", "人情",
                "娱乐", "美妆", "旅游", "医疗", "会员", "通讯",
                "工资", "奖金", "理财", "兼职", "生活费", "其他收入"
        );
        // 2. 如果不在这个白名单里，就属于自定义分类
        return !defaultCategories.contains(categoryName);
    }

    /**
     * 判断二级分类是否为自定义
     */
    public static boolean isCustomChildCategory(String parentCategory, String childCategory) {
        // 调用内部私有方法进行匹配检查
        return !isDefaultChildCategory(parentCategory, childCategory);
    }

    /**
     * 内部辅助：核对是否属于预设的二级分类树
     */
    private static boolean isDefaultChildCategory(String parentCategory, String childCategory) {
        // 定义所有内置的二级分类对应关系
        Map<String, Set<String>> defaultSubCategories = new HashMap<>();

        defaultSubCategories.put("餐饮", Set.of("三餐", "咖啡", "奶茶", "食材", "柴米油盐", "零食", "水果"));
        defaultSubCategories.put("购物", Set.of("鞋服", "日用", "数码", "包包", "厨房用品", "电器"));
        defaultSubCategories.put("交通", Set.of("公交地铁", "打车", "共享单车", "私家车", "火车", "飞机票", "加油", "大巴"));
        defaultSubCategories.put("住宿", Set.of("房租", "物业水电", "维修"));
        defaultSubCategories.put("日常", Set.of("快递", "理发"));
        defaultSubCategories.put("学习", Set.of("培训", "书籍", "文具耗材", "网课", "考试报名"));
        defaultSubCategories.put("人情", Set.of("送礼", "发红包", "请客", "亲密付", "孝心"));
        defaultSubCategories.put("娱乐", Set.of("电影", "游戏", "健身", "休闲", "约会", "演唱会"));
        defaultSubCategories.put("美妆", Set.of("护肤品", "化妆品", "美容美发", "美甲美睫", "洗面奶"));
        defaultSubCategories.put("旅游", Set.of("酒店", "景区门票", "伴手礼", "团费"));
        defaultSubCategories.put("医疗", Set.of("就诊", "药品", "住院", "体检", "治疗", "保健"));
        defaultSubCategories.put("会员", Set.of("视频会员", "音乐会员", "办公软件", "社交会员", "书籍会员"));
        defaultSubCategories.put("通讯", Set.of("话费", "宽带"));

        Set<String> subCats = defaultSubCategories.get(parentCategory);
        return subCats != null && subCats.contains(childCategory);
    }
    // --- 临时数据搬家方法 ---
    /**
    public static void migrateCsvToDb() {
        File oldFile = new File("custom_categories.csv");
        if (!oldFile.exists()) return;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(oldFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",", 3);
                if (parts.length >= 2) {
                    String parent = parts[0].trim();
                    String type = parts[1].trim();
                    addCustomParentCategory(parent, type); // 内部会自动去重并写库

                    if (parts.length == 3 && !parts[2].isEmpty()) {
                        for (String child : parts[2].split(";")) {
                            addCustomChildCategory(parent, child.trim());
                        }
                    }
                }
            }
            // 搬完后改名
            oldFile.renameTo(new File("custom_categories_backup.csv"));
        } catch (IOException e) { e.printStackTrace(); }
    }
     */
    // --- 原有只读方法（保持不变，UI无需改动） ---
    public static Set<String> getParentCategories() { return CATEGORY_MAP.keySet(); }
    public static List<String> getChildCategories(String parent) { return CATEGORY_MAP.getOrDefault(parent, new ArrayList<>()); }
    public static String getEmoji(String name) { return EMOJI_MAP.getOrDefault(name, "\uD83C\uDFF7"); }
    public static Set<String> getIncomeCategories() {
        Set<String> incomes = new LinkedHashSet<>();
        CATEGORY_TYPE_MAP.forEach((k, v) -> { if ("收入".equals(v)) incomes.add(k); });
        return incomes;
    }
    public static Set<String> getExpenseCategories() {
        Set<String> expenses = new LinkedHashSet<>();
        CATEGORY_TYPE_MAP.forEach((k, v) -> { if ("支出".equals(v)) expenses.add(k); });
        return expenses;
    }

    // --- 默认分类初始化 (Hardcoded) ---
    /**
     * 这里使用 Unicode 转义序列 (Surrogate Pairs) 来定义 Emoji。
     * 这样做的好处是：源文件是纯 ASCII 字符，彻底避免了 Windows GBK/UTF-8 编码冲突导致的乱码。
     * 例如：\uD83C\uDF54 就是 🍔
     */
    private static void initDefaultCategories() {
        // 1. 餐饮 (🍔 \uD83C\uDF54)
        addCustomParentCategory("餐饮", "支出");
        CATEGORY_MAP.putIfAbsent("餐饮", new ArrayList<>(Arrays.asList("三餐", "咖啡", "奶茶", "食材", "柴米油盐", "零食", "水果")));
        addSubEmojis(
                "三餐", "\uD83C\uDF5A",      // 🍚
                "咖啡", "\u2615",            // ☕
                "奶茶", "\uD83E\uDDCB",      // 🧋
                "食材", "\uD83E\uDD66",      // 🥦
                "柴米油盐", "\uD83E\uDDC2",  // 🧂
                "零食", "\uD83C\uDF6A",      // 🍪
                "水果", "\uD83C\uDF4E"       // 🍎
        );
        EMOJI_MAP.put("餐饮", "\uD83C\uDF54");

        // 2. 购物 (🛍 \uD83D\uDECD)
        addCustomParentCategory("购物", "支出");
        CATEGORY_MAP.putIfAbsent("购物", new ArrayList<>(Arrays.asList("鞋服", "日用", "数码", "包包", "厨房用品", "电器")));
        addSubEmojis(
                "鞋服", "\uD83D\uDC55",      // 👕
                "日用", "\uD83E\uDDFB",      // 🧻
                "数码", "\uD83D\uDCBB",      // 💻
                "包包", "\uD83D\uDC5C",      // 👜
                "厨房用品", "\uD83C\uDF73",   // 🍳
                "电器", "\uD83D\uDD0C"       // 🔌
        );
        EMOJI_MAP.put("购物", "\uD83D\uDECD");

        // 3. 交通 (🚗 \uD83D\uDE97)
        addCustomParentCategory("交通", "支出");
        CATEGORY_MAP.putIfAbsent("交通", new ArrayList<>(Arrays.asList("公交地铁", "打车", "共享单车", "私家车", "火车", "飞机票", "加油", "大巴")));
        addSubEmojis(
                "公交地铁", "\uD83D\uDE88",   // 🚇
                "打车", "\uD83D\uDE95",      // 🚕
                "共享单车", "\uD83D\uDEB2",   // 🚲
                "私家车", "\uD83D\uDE97",     // 🚘
                "火车", "\uD83D\uDE84",      // 🚆
                "飞机票", "\u2708",    // ✈
                "加油", "\u26FD",            // ⛽
                "大巴", "\uD83D\uDE8C"       // 🚌
        );
        EMOJI_MAP.put("交通", "\uD83D\uDE98");

        // 4. 住宿 (🏠 \uD83C\uDFE0)
        addCustomParentCategory("住宿", "支出");
        CATEGORY_MAP.putIfAbsent("住宿", new ArrayList<>(Arrays.asList("房租", "物业水电", "维修")));
        addSubEmojis(
                "房租", "\uD83D\uDD11",      // 🔑
                "物业水电", "\uD83D\uDCA1",   // 💡
                "维修", "\uD83D\uDD27"       // 🔧
        );
        EMOJI_MAP.put("住宿", "\uD83C\uDFE0");

        // 5. 日常 (📦 \uD83D\uDCE6)
        addCustomParentCategory("日常", "支出");
        CATEGORY_MAP.putIfAbsent("日常", new ArrayList<>(Arrays.asList("快递", "理发")));
        addSubEmojis(
                "快递", "\uD83D\uDCE6",              // 📦
                "理发", "\u2702" // ✂ 剪刀
        );
        EMOJI_MAP.put("日常", "\uD83D\uDCE6");

        // 6. 学习 (📚 \uD83D\uDCDA)
        addCustomParentCategory("学习", "支出");
        CATEGORY_MAP.putIfAbsent("学习", new ArrayList<>(Arrays.asList("培训", "书籍", "文具耗材", "网课", "考试报名")));
        addSubEmojis(
                "培训", "\uD83C\uDFEB",      // 🏫
                "书籍", "\uD83D\uDCDA",      // 📖
                "文具耗材", "\u270F", // ✏ 铅笔
                "网课", "\uD83D\uDCBB",     // 💻 电脑
                "考试报名", "\uD83D\uDCDD"    // 📝
        );
        EMOJI_MAP.put("学习", "\uD83D\uDCDA");

        // 7. 人情 (💖 \uD83D\uDC96)
        addCustomParentCategory("人情", "支出");
        CATEGORY_MAP.putIfAbsent("人情", new ArrayList<>(Arrays.asList("送礼", "发红包", "请客", "亲密付", "孝心")));
        addSubEmojis(
                "送礼", "\uD83C\uDF81",      // 🎁
                "发红包", "\uD83E\uDDE7",    // 🧧
                "请客", "\uD83E\uDD42",      // 🥂
                "亲密付", "\uD83D\uDC95",    // 💑
                "孝心", "\uD83D\uDC9D"       // ❤ 红心
        );
        EMOJI_MAP.put("人情", "\uD83D\uDC96");

        // 8. 娱乐 (🎮 \uD83C\uDFAE)
        addCustomParentCategory("娱乐", "支出");
        CATEGORY_MAP.putIfAbsent("娱乐", new ArrayList<>(Arrays.asList("电影", "游戏", "健身", "休闲", "约会", "演唱会")));
        addSubEmojis(
                "电影", "\uD83C\uDFAC",      // 🎬
                "游戏", "\uD83D\uDD79",      // 🎮
                "健身", "\uD83C\uDFCB",      // 🏋
                "休闲", "\uD83C\uDF75",      // 🍵
                "约会", "\uD83C\uDF39",      // 🌹
                "演唱会", "\uD83C\uDFA4"     // 🎤
        );
        EMOJI_MAP.put("娱乐", "\uD83C\uDFAE");

        // 9. 美妆 (💄 \uD83D\uDC84)
        addCustomParentCategory("美妆", "支出");
        CATEGORY_MAP.putIfAbsent("美妆", new ArrayList<>(Arrays.asList("护肤品", "化妆品", "美容美发", "美甲美睫", "洗面奶")));
        addSubEmojis(
                "护肤品", "\uD83E\uDDF4",    // 🧴
                "化妆品", "\uD83D\uDC84",    // 💄
                "美容美发", "\uD83D\uDC88",  // 💈
                "美甲美睫", "\uD83D\uDC85",  // 💅
                "洗面奶", "\uD83E\uDDFC"     // 🧼
        );
        EMOJI_MAP.put("美妆", "\uD83D\uDC84");

        // 10. 旅游 (✈ \u2708)
        addCustomParentCategory("旅游", "支出");
        CATEGORY_MAP.putIfAbsent("旅游", new ArrayList<>(Arrays.asList("酒店", "景区门票", "伴手礼", "团费")));
        addSubEmojis(
                "酒店", "\uD83C\uDFE8",      // 🏨
                "景区门票", "\uD83C\uDFAB",   // 🎫
                "伴手礼", "\uD83C\uDF81",     // 🎁
                "团费", "\uD83D\uDEA9"       // 🚩
        );
        EMOJI_MAP.put("旅游", "\u2708");

        // 11. 医疗 (💊 \uD83D\uDC8A)
        addCustomParentCategory("医疗", "支出");
        CATEGORY_MAP.putIfAbsent("医疗", new ArrayList<>(Arrays.asList("就诊", "药品", "住院", "体检", "治疗", "保健")));
        addSubEmojis(
                "就诊", "\uD83C\uDFE5",      // 🏥
                "药品", "\uD83D\uDC8A",      // 💊
                "住院", "\uD83D\uDECC",      //
                "体检", "\uD83E\uDE7A",      // 🩺
                "治疗", "\uD83D\uDC89",      // 💉
                "保健", "\uD83C\uDF3F"       // 🥗
        );
        EMOJI_MAP.put("医疗", "\uD83D\uDC8A");

        // 12. 会员租用 (👑 \uD83D\uDC51)
        addCustomParentCategory("会员", "支出");
        CATEGORY_MAP.putIfAbsent("会员", new ArrayList<>(Arrays.asList("视频会员", "音乐会员", "办公软件", "社交会员", "书籍会员")));
        addSubEmojis(
                "视频会员", "\uD83C\uDFAC",   // 🎬
                "音乐会员", "\uD83C\uDFB5",   // 🎵
                "办公软件", "\uD83D\uDCCA",   // 📊
                "社交会员", "\uD83D\uDCAC",   // 💬
                "书籍会员", "\uD83D\uDCD6"    // 📖
        );
        EMOJI_MAP.put("会员", "\uD83D\uDC51");

        // 13. 通讯 (📞 \uD83D\uDCDE)
        addCustomParentCategory("通讯", "支出");
        CATEGORY_MAP.putIfAbsent("通讯", new ArrayList<>(Arrays.asList("话费", "宽带")));
        addSubEmojis(
                "话费", "\uD83D\uDCF1",      // 📱
                "宽带", "\uD83C\uDF10"       // 🌐
        );
        EMOJI_MAP.put("通讯", "\uD83D\uDCDE");

        // 收入类 - 将原来的二级分类提升为一级分类
        addCustomParentCategory("工资", "收入");      // 💳 信用卡
        CATEGORY_MAP.putIfAbsent("工资", new ArrayList<>());
        EMOJI_MAP.put("工资", "\uD83D\uDCB3");

        addCustomParentCategory("奖金", "收入");      // 🏆 奖杯
        CATEGORY_MAP.putIfAbsent("奖金", new ArrayList<>());
        EMOJI_MAP.put("奖金", "\uD83C\uDFC6");

        addCustomParentCategory("理财", "收入");      // 📈 上升趋势
        CATEGORY_MAP.putIfAbsent("理财", new ArrayList<>());
        EMOJI_MAP.put("理财", "\uD83D\uDCC8");

        addCustomParentCategory("兼职", "收入");      // 🛠 工具（去掉变体选择符）
        CATEGORY_MAP.putIfAbsent("兼职", new ArrayList<>());
        EMOJI_MAP.put("兼职", "\uD83D\uDEE0");

        addCustomParentCategory("生活费", "收入");    // 💰 钱袋
        CATEGORY_MAP.putIfAbsent("生活费", new ArrayList<>());
        EMOJI_MAP.put("生活费", "\uD83D\uDCB0");

        addCustomParentCategory("其他收入", "收入");  // 💎 宝石
        CATEGORY_MAP.putIfAbsent("其他收入", new ArrayList<>());
        EMOJI_MAP.put("其他收入", "\uD83D\uDC8E");
    }

    /**
     * 辅助方法：批量为二级分类设置 Emoji 图标
     * @param args 成对出现的字符串，格式为："分类名称", "Emoji字符"
     */
    private static void addSubEmojis(String... args) {
        if (args.length % 2 != 0) {
            throw new IllegalArgumentException("参数必须成对出现: 名称, Emoji");
        }
        for (int i = 0; i < args.length; i += 2) {
            // 直接存入内存缓存 EMOJI_MAP 中
            // 这样在表格渲染时，就能通过 CategoryManager.getEmoji(name) 找到对应的图标了
            EMOJI_MAP.put(args[i], args[i+1]);
        }
    }

    /**
     * 导出支出分类树：Map<一级分类, List<二级分类>>
     * 专门用于喂给 AI，让它知道目前有哪些支出类目
     */
    public static Map<String, List<String>> getExpenseCategoryTree() {
        Map<String, List<String>> tree = new LinkedHashMap<>();
        // 获取所有标记为“支出”的一级分类
        Set<String> expenseParents = getExpenseCategories();
        for (String parent : expenseParents) {
            // 绑定对应的二级分类列表
            tree.put(parent, getChildCategories(parent));
        }
        return tree;
    }

    /**
     * 导出收入分类树
     */
    public static Map<String, List<String>> getIncomeCategoryTree() {
        Map<String, List<String>> tree = new LinkedHashMap<>();
        // 获取所有标记为“收入”的一级分类
        Set<String> incomeParents = getIncomeCategories();
        for (String parent : incomeParents) {
            tree.put(parent, getChildCategories(parent));
        }
        return tree;
    }

    // --- 个性化信息管理 ---
    public static void addPersonalization(String info) {
        if (info != null && !info.trim().isEmpty() && !PERSONALIZATIONS.contains(info)) {
            PERSONALIZATIONS.add(info.trim());
            savePersonalizations();
        }
    }

    public static void removePersonalization(String info) {
        PERSONALIZATIONS.remove(info);
        savePersonalizations();
    }

    public static List<String> getPersonalizations() {
        return new ArrayList<>(PERSONALIZATIONS);
    }

    private static void savePersonalizations() {
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(PERSONALIZATION_FILE), StandardCharsets.UTF_8))) {
            for (String p : PERSONALIZATIONS) {
                writer.println(p);
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    private static void loadPersonalizations() {
        File file = new File(PERSONALIZATION_FILE);
        if (!file.exists()) return;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) PERSONALIZATIONS.add(line.trim());
            }
        } catch (IOException e) { e.printStackTrace(); }
    }
}

