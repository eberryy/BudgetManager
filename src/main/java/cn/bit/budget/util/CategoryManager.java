package cn.bit.budget.util;

import java.util.*;

public class CategoryManager {

    // 存储一级分类 -> 二级分类列表的映射
    private static final Map<String, List<String>> CATEGORY_MAP = new LinkedHashMap<>();

    // 存储分类名称 -> Emoji 的映射 (一级和二级都存在这里)
    private static final Map<String, String> EMOJI_MAP = new HashMap<>();

    static {
        // 初始化你的默认数据
        initDefaultCategories();
    }

    private static void initDefaultCategories() {
        // 1. 餐饮
        addCategory("餐饮", "🍔", Arrays.asList("三餐", "咖啡", "奶茶", "食材", "柴米油盐", "零食", "水果"));
        addSubEmojis(
                "三餐", "🍚", "咖啡", "☕", "奶茶", "🧋", "食材", "🥦",
                "柴米油盐", "🧂", "零食", "🍪", "水果", "🍎"
        );

        // 2. 购物
        addCategory("购物", "🛍️", Arrays.asList("鞋服", "日用", "数码", "包包", "厨房用品", "电器"));
        addSubEmojis(
                "鞋服", "👕", "日用", "🧻", "数码", "💻",
                "包包", "👜", "厨房用品", "🍳", "电器", "🔌"
        );

        // 3. 交通
        addCategory("交通", "🚗", Arrays.asList("公交地铁", "打车", "共享单车", "私家车", "火车", "飞机票", "加油", "大巴"));
        addSubEmojis(
                "公交地铁", "🚇", "打车", "🚕", "共享单车", "🚲", "私家车", "🚘",
                "火车", "🚆", "飞机票", "✈️", "加油", "⛽", "大巴", "🚌"
        );

        // 4. 住宿
        addCategory("住宿", "🏠", Arrays.asList("房租", "物业水电", "维修"));
        addSubEmojis("房租", "🔑", "物业水电", "💡", "维修", "🔧");

        // 5. 日常
        addCategory("日常", "📦", Arrays.asList("快递", "理发"));
        addSubEmojis("快递", "📦", "理发", "💇‍♂️");

        // 6. 学习
        addCategory("学习", "📚", Arrays.asList("培训", "🏫", "书籍", "📖", "文具耗材", "🖊️", "网课", "💻", "考试报名", "📝"));
        // 注意：上面一行代码我不小心把emoji混进list了，为了保持addCategory原始逻辑，
        // 这里需要分开写，以下是修正后的写法：
        addCategory("学习", "📚", Arrays.asList("培训", "书籍", "文具耗材", "网课", "考试报名"));
        addSubEmojis("培训", "🏫", "书籍", "📖", "文具耗材", "🖊️", "网课", "🖥️", "考试报名", "📝");

        // 7. 人情
        addCategory("人情", "🧧", Arrays.asList("送礼", "发红包", "请客", "亲密付", "孝心"));
        addSubEmojis("送礼", "🎁", "发红包", "🧧", "请客", "🥂", "亲密付", "💑", "孝心", "❤️");

        // 8. 娱乐
        addCategory("娱乐", "🎮", Arrays.asList("电影", "游戏", "健身", "休闲", "约会", "演唱会"));
        addSubEmojis("电影", "🎬", "游戏", "🎮", "健身", "🏋️", "休闲", "🍵", "约会", "🌹", "演唱会", "🎤");

        // 9. 美妆
        addCategory("美妆", "💄", Arrays.asList("护肤品", "化妆品", "美容美发", "美甲美睫", "洗面奶"));
        addSubEmojis("护肤品", "🧴", "化妆品", "💄", "美容美发", "💈", "美甲美睫", "💅", "洗面奶", "🧼");

        // 10. 旅游
        addCategory("旅游", "✈️", Arrays.asList("酒店", "景区门票", "伴手礼", "团费"));
        addSubEmojis("酒店", "🏨", "景区门票", "🎫", "伴手礼", "🎁", "团费", "🚩");

        // 11. 医疗
        addCategory("医疗", "💊", Arrays.asList("就诊", "药品", "住院", "体检", "治疗", "保健"));
        addSubEmojis("就诊", "🏥", "药品", "💊", "住院", "🛏️", "体检", "🩺", "治疗", "💉", "保健", "🥗");

        // 12. 会员租用
        addCategory("会员", "👑", Arrays.asList("视频会员", "音乐会员", "办公软件", "社交会员", "书籍会员"));
        addSubEmojis("视频会员", "🎬", "音乐会员", "🎵", "办公软件", "📊", "社交会员", "💬", "书籍会员", "📖");

        // 13. 通讯
        addCategory("通讯", "📞", Arrays.asList("话费", "宽带"));
        addSubEmojis("话费", "📱", "宽带", "🌐");

        // 收入类
        addCategory("收入", "💰", Arrays.asList("工资", "奖金", "理财", "兼职", "生活费", "其他收入"));
        addSubEmojis(
                "工资", "💳", "奖金", "🏆", "理财", "📈",
                "兼职", "⚒️", "生活费", "🤲", "其他收入", "💎"
        );
    }

    // 原始添加方法 (保持不变)
    private static void addCategory(String parent, String emoji, List<String> children) {
        CATEGORY_MAP.put(parent, new ArrayList<>(children));
        EMOJI_MAP.put(parent, emoji);
    }

    // 新增：批量注册二级分类 Emoji 的辅助方法
    // 使用可变参数，格式必须为：Key1, Emoji1, Key2, Emoji2 ...
    private static void addSubEmojis(String... args) {
        if (args.length % 2 != 0) {
            throw new IllegalArgumentException("参数必须成对出现: 名称, Emoji");
        }
        for (int i = 0; i < args.length; i += 2) {
            EMOJI_MAP.put(args[i], args[i+1]);
        }
    }

    // 获取所有一级分类
    public static Set<String> getParentCategories() {
        return CATEGORY_MAP.keySet();
    }

    // 根据一级分类获取二级分类
    public static List<String> getChildCategories(String parent) {
        return CATEGORY_MAP.getOrDefault(parent, new ArrayList<>());
    }

    // 获取 Emoji，如果没有则返回默认图标
    public static String getEmoji(String categoryName) {
        // 默认图标改为通用标签，避免 null
        return EMOJI_MAP.getOrDefault(categoryName, "🏷️");
    }


    // 动态添加一级分类
    public static void addCustomParentCategory(String parentName)
    {
        if
        (!CATEGORY_MAP.containsKey(parentName)) {
            // 新建一个空列表作为该一级分类的子分类容器
            CATEGORY_MAP.put(parentName,
                    new
                            ArrayList<>());
            // 给个默认 Emoji
            EMOJI_MAP.put(parentName,
                    "🏷️"
            );
        }
    }

    // 添加自定义二级分类
    public static void addCustomChildCategory(String parent, String childName) {
        if (CATEGORY_MAP.containsKey(parent)) {
            List<String> children = CATEGORY_MAP.get(parent);
            if (!children.contains(childName)) {
                children.add(childName);
                // 默认为自定义分类添加一个通用 Emoji，或者你可以让用户稍后设置
                EMOJI_MAP.put(childName, "🏷️");
            }
        }
    }

    // 获取给 AI 用的扁平化分类列表
    public static String getAllCategoriesString() {
        return CATEGORY_MAP.keySet().toString();
    }
}