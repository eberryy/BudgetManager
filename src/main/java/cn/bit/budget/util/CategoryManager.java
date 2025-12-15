package cn.bit.budget.util;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class CategoryManager {

    // 存储一级分类 -> 二级分类列表的映射
    private static final Map<String, List<String>> CATEGORY_MAP = new LinkedHashMap<>();

    // 存储分类名称 -> Emoji 的映射
    private static final Map<String, String> EMOJI_MAP = new HashMap<>();

    // 自定义分类存储文件
    private static final String CUSTOM_CATEGORY_FILE = "custom_categories.csv";

    static {
        initDefaultCategories();
        loadCustomCategories(); // 加载用户自定义分类
    }

    /**
     * 这里使用 Unicode 转义序列 (Surrogate Pairs) 来定义 Emoji。
     * 这样做的好处是：源文件是纯 ASCII 字符，彻底避免了 Windows GBK/UTF-8 编码冲突导致的乱码。
     * 例如：\uD83C\uDF54 就是 🍔
     */
    private static void initDefaultCategories() {
        // 1. 餐饮 (🍔 \uD83C\uDF54)
        addCategory("餐饮", "\uD83C\uDF54", Arrays.asList("三餐", "咖啡", "奶茶", "食材", "柴米油盐", "零食", "水果"));
        addSubEmojis(
                "三餐", "\uD83C\uDF5A",      // 🍚
                "咖啡", "\u2615",            // ☕
                "奶茶", "\uD83E\uDDCB",      // 🧋
                "食材", "\uD83E\uDD66",      // 🥦
                "柴米油盐", "\uD83E\uDDC2",  // 🧂
                "零食", "\uD83C\uDF6A",      // 🍪
                "水果", "\uD83C\uDF4E"       // 🍎
        );

        // 2. 购物 (🛍️ \uD83D\uDECD\uFE0F)
        addCategory("购物", "\uD83D\uDECD\uFE0F", Arrays.asList("鞋服", "日用", "数码", "包包", "厨房用品", "电器"));
        addSubEmojis(
                "鞋服", "\uD83D\uDC55",      // 👕
                "日用", "\uD83E\uDDFB",      // 🧻
                "数码", "\uD83D\uDCBB",      // 💻
                "包包", "\uD83D\uDC5C",      // 👜
                "厨房用品", "\uD83C\uDF73",   // 🍳
                "电器", "\uD83D\uDD0C"       // 🔌
        );

        // 3. 交通 (🚗 \uD83D\uDE97)
        addCategory("交通", "\uD83D\uDE97", Arrays.asList("公交地铁", "打车", "共享单车", "私家车", "火车", "飞机票", "加油", "大巴"));
        addSubEmojis(
                "公交地铁", "\uD83D\uDE87",   // 🚇
                "打车", "\uD83D\uDE95",      // 🚕
                "共享单车", "\uD83D\uDEB2",   // 🚲
                "私家车", "\uD83D\uDE98",     // 🚘
                "火车", "\uD83D\uDE86",      // 🚆
                "飞机票", "\u2708\uFE0F",    // ✈️
                "加油", "\u26FD",            // ⛽
                "大巴", "\uD83D\uDE8C"       // 🚌
        );

        // 4. 住宿 (🏠 \uD83C\uDFE0)
        addCategory("住宿", "\uD83C\uDFE0", Arrays.asList("房租", "物业水电", "维修"));
        addSubEmojis(
                "房租", "\uD83D\uDD11",      // 🔑
                "物业水电", "\uD83D\uDCA1",   // 💡
                "维修", "\uD83D\uDD27"       // 🔧
        );

        // 5. 日常 (📦 \uD83D\uDCE6)
        addCategory("日常", "\uD83D\uDCE6", Arrays.asList("快递", "理发"));
        addSubEmojis(
                "快递", "\uD83D\uDCE6",              // 📦
                "理发", "\uD83D\uDC87\u200D\u2642\uFE0F" // 💇‍♂️
        );

        // 6. 学习 (📚 \uD83D\uDCDA)
        addCategory("学习", "\uD83D\uDCDA", Arrays.asList("培训", "书籍", "文具耗材", "网课", "考试报名"));
        addSubEmojis(
                "培训", "\uD83C\uDFEB",      // 🏫
                "书籍", "\uD83D\uDCD6",      // 📖
                "文具耗材", "\uD83D\uDD8A\uFE0F", // 🖊️
                "网课", "\uD83D\uDDA5\uFE0F",     // 🖥️
                "考试报名", "\uD83D\uDCDD"    // 📝
        );

        // 7. 人情 (🧧 \uD83E\uDDE7)
        addCategory("人情", "\uD83E\uDDE7", Arrays.asList("送礼", "发红包", "请客", "亲密付", "孝心"));
        addSubEmojis(
                "送礼", "\uD83C\uDF81",      // 🎁
                "发红包", "\uD83E\uDDE7",    // 🧧
                "请客", "\uD83E\uDD42",      // 🥂
                "亲密付", "\uD83D\uDC91",    // 💑
                "孝心", "\u2764\uFE0F"       // ❤️
        );

        // 8. 娱乐 (🎮 \uD83C\uDFAE)
        addCategory("娱乐", "\uD83C\uDFAE", Arrays.asList("电影", "游戏", "健身", "休闲", "约会", "演唱会"));
        addSubEmojis(
                "电影", "\uD83C\uDFAC",      // 🎬
                "游戏", "\uD83C\uDFAE",      // 🎮
                "健身", "\uD83C\uDFCB\uFE0F", // 🏋️
                "休闲", "\uD83C\uDF75",      // 🍵
                "约会", "\uD83C\uDF39",      // 🌹
                "演唱会", "\uD83C\uDFA4"     // 🎤
        );

        // 9. 美妆 (💄 \uD83D\uDC84)
        addCategory("美妆", "\uD83D\uDC84", Arrays.asList("护肤品", "化妆品", "美容美发", "美甲美睫", "洗面奶"));
        addSubEmojis(
                "护肤品", "\uD83E\uDDF4",    // 🧴
                "化妆品", "\uD83D\uDC84",    // 💄
                "美容美发", "\uD83D\uDC88",  // 💈
                "美甲美睫", "\uD83D\uDC85",  // 💅
                "洗面奶", "\uD83E\uDDFC"     // 🧼
        );

        // 10. 旅游 (✈️ \u2708\uFE0F)
        addCategory("旅游", "\u2708\uFE0F", Arrays.asList("酒店", "景区门票", "伴手礼", "团费"));
        addSubEmojis(
                "酒店", "\uD83C\uDFE8",      // 🏨
                "景区门票", "\uD83C\uDFAB",   // 🎫
                "伴手礼", "\uD83C\uDF81",     // 🎁
                "团费", "\uD83D\uDEA9"       // 🚩
        );

        // 11. 医疗 (💊 \uD83D\uDC8A)
        addCategory("医疗", "\uD83D\uDC8A", Arrays.asList("就诊", "药品", "住院", "体检", "治疗", "保健"));
        addSubEmojis(
                "就诊", "\uD83C\uDFE5",      // 🏥
                "药品", "\uD83D\uDC8A",      // 💊
                "住院", "\uD83D\uDECF\uFE0F", // 🛏️
                "体检", "\uD83E\uDE7A",      // 🩺
                "治疗", "\uD83D\uDC89",      // 💉
                "保健", "\uD83E\uDD57"       // 🥗
        );

        // 12. 会员租用 (👑 \uD83D\uDC51)
        addCategory("会员", "\uD83D\uDC51", Arrays.asList("视频会员", "音乐会员", "办公软件", "社交会员", "书籍会员"));
        addSubEmojis(
                "视频会员", "\uD83C\uDFAC",   // 🎬
                "音乐会员", "\uD83C\uDFB5",   // 🎵
                "办公软件", "\uD83D\uDCCA",   // 📊
                "社交会员", "\uD83D\uDCAC",   // 💬
                "书籍会员", "\uD83D\uDCD6"    // 📖
        );

        // 13. 通讯 (📞 \uD83D\uDCDE)
        addCategory("通讯", "\uD83D\uDCDE", Arrays.asList("话费", "宽带"));
        addSubEmojis(
                "话费", "\uD83D\uDCF1",      // 📱
                "宽带", "\uD83C\uDF10"       // 🌐
        );

        // 收入类 (💰 \uD83D\uDCB0)
        addCategory("收入", "\uD83D\uDCB0", Arrays.asList("工资", "奖金", "理财", "兼职", "生活费", "其他收入"));
        addSubEmojis(
                "工资", "\uD83D\uDCB3",      // 💳
                "奖金", "\uD83C\uDFC6",      // 🏆
                "理财", "\uD83D\uDCC8",      // 📈
                "兼职", "\u2692\uFE0F",      // ⚒️
                "生活费", "\uD83E\uDD32",    // 🤲
                "其他收入", "\uD83D\uDC8E"   // 💎
        );
    }

    private static void addCategory(String parent, String emoji, List<String> children) {
        CATEGORY_MAP.put(parent, new ArrayList<>(children));
        EMOJI_MAP.put(parent, emoji);
    }

    private static void addSubEmojis(String... args) {
        if (args.length % 2 != 0) {
            throw new IllegalArgumentException("参数必须成对出现: 名称, Emoji");
        }
        for (int i = 0; i < args.length; i += 2) {
            EMOJI_MAP.put(args[i], args[i+1]);
        }
    }

    public static Set<String> getParentCategories() {
        return CATEGORY_MAP.keySet();
    }

    public static List<String> getChildCategories(String parent) {
        return CATEGORY_MAP.getOrDefault(parent, new ArrayList<>());
    }

    // 获取 Emoji，如果没有则返回默认图标 (🏷 \uD83C\uDFF7)
    public static String getEmoji(String categoryName) {
        return EMOJI_MAP.getOrDefault(categoryName, "\uD83C\uDFF7");
    }

    // 动态添加一级分类
    public static void addCustomParentCategory(String parentName) {
        if (!CATEGORY_MAP.containsKey(parentName)) {
            CATEGORY_MAP.put(parentName, new ArrayList<>());
            EMOJI_MAP.put(parentName, "\uD83C\uDFF7");
            saveCustomCategories(); // 持久化保存
        }
    }

    // 添加自定义二级分类
    public static void addCustomChildCategory(String parent, String childName) {
        if (CATEGORY_MAP.containsKey(parent)) {
            List<String> children = CATEGORY_MAP.get(parent);
            if (!children.contains(childName)) {
                children.add(childName);
                EMOJI_MAP.put(childName, "\uD83C\uDFF7");
                saveCustomCategories(); // 持久化保存
            }
        }
    }

    public static String getAllCategoriesString() {
        return CATEGORY_MAP.keySet().toString();
    }

    /**
     * 保存自定义分类到文件
     * 格式：parent,child1;child2;child3
     */
    private static void saveCustomCategories() {
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(CUSTOM_CATEGORY_FILE), StandardCharsets.UTF_8))) {
            
            for (Map.Entry<String, List<String>> entry : CATEGORY_MAP.entrySet()) {
                String parent = entry.getKey();
                List<String> children = entry.getValue();
                
                // 格式：一级分类,二级分类1;二级分类2;二级分类3
                String childrenStr = String.join(";", children);
                writer.write(parent + "," + childrenStr);
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("保存自定义分类失败：" + e.getMessage());
        }
    }

    /**
     * 从文件加载自定义分类
     */
    private static void loadCustomCategories() {
        File file = new File(CUSTOM_CATEGORY_FILE);
        if (!file.exists()) {
            return; // 文件不存在，使用默认分类
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                
                String[] parts = line.split(",", 2);
                if (parts.length < 1) continue;
                
                String parent = parts[0].trim();
                
                // 如果是新的一级分类（不在默认分类中），添加它
                if (!CATEGORY_MAP.containsKey(parent)) {
                    CATEGORY_MAP.put(parent, new ArrayList<>());
                    EMOJI_MAP.put(parent, "\uD83C\uDFF7");
                }
                
                // 处理二级分类
                if (parts.length == 2 && !parts[1].trim().isEmpty()) {
                    String[] children = parts[1].split(";");
                    List<String> childList = CATEGORY_MAP.get(parent);
                    
                    for (String child : children) {
                        String childName = child.trim();
                        if (!childName.isEmpty() && !childList.contains(childName)) {
                            childList.add(childName);
                            if (!EMOJI_MAP.containsKey(childName)) {
                                EMOJI_MAP.put(childName, "\uD83C\uDFF7");
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("加载自定义分类失败：" + e.getMessage());
        }
    }
}