package cn.bit.budget.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class AICategorizer {

    // 替换为你的 SiliconCloud / DeepSeek Key
    private static final String API_KEY = "sk-kovzrnozjojynhribjnternslpdyptambrkrzjdbquyldady";
    private static final String API_URL = "https://api.siliconflow.cn/v1/chat/completions";
    // 推荐使用能力更强的模型来处理这种复杂逻辑
    private static final String MODEL_NAME = "Qwen/Qwen3-Next-80B-A3B-Instruct";

    private static final Gson gson = new Gson();
    private static final HttpClient client = HttpClient.newHttpClient();

    // 🌟 新增：AI 分析结果数据结构
    public static class CategoryResult {
        public String suggestion;      // AI 建议的分类（可能是新的，也可能是旧的）
        public boolean isNew;          // 这是否是一个系统中不存在的新分类
        public String fallback;        // 【关键】兜底分类（必须是现有分类之一）
        public String reason;          // (可选) AI 的理由，用于 log 或 tooltip
    }

    public static CompletableFuture<Map<String, CategoryResult>> categorizeAsync(
        List<Map<String, Object>> billItems, // 改为接收包含金额和描述的明细
        Map<String, List<String>> expenseTree,
        Map<String, List<String>> incomeTree,
        List<String> personalizations) {

    return CompletableFuture.supplyAsync(() -> {
        try {
            // 构造个性化指令字符串
            String customInstructions = personalizations.isEmpty() ? "无" :
                    String.join("\n- ", personalizations);
            String prompt = String.format(
                    "### 任务\n" +
                            "作为财务分类专家，请根据现有体系和用户偏好，为账单明细匹配最合适的分类。\n\n" +
                            "### 1. 现有分类体系\n" +
                            "支出树 (Expense): %s\n" +
                            "收入树 (Income): %s\n\n" +
                            "### 2. 用户个性化偏好\n" +
                            "%s\n\n" +
                            "### 3. 示例 (Few-Shot Examples)\n" +
                            "// 场景1：匹配现有分类\n" +
                            "输入: [{\"desc\": \"美团-村上一屋·日料\", \"amount\": -20.0, \"unique_id\": \"ex1\"}]\n" +
                            "输出: {\"ex1\": {\"suggestion\": \"餐饮 - 三餐\", \"isNew\": false, \"fallback\": \"餐饮\"}}\n\n" +
                            "// 场景2：发现新分类（要求：名称极简，不要废话）\n" +
                            "输入: [{\"desc\": \"北京鸿笙科技-标准洗\", \"amount\": -2.25, \"unique_id\": \"ex2\"}]\n" +
                            "输出: {\"ex2\": {\"suggestion\": \"洗衣\", \"isNew\": true, \"fallback\": \"日常\"}}\n\n" +
                            "输入: [{\"desc\": \"印之梦联营-自助打印\", \"amount\": -0.75, \"unique_id\": \"ex3\"}]\n" +
                            "输出: {\"ex3\": {\"suggestion\": \"办公\", \"isNew\": true, \"fallback\": \"学习\"}}\n\n" +
                            "输入: [{\"desc\": \"荣耀-鲜花卡\", \"amount\": -98.0, \"unique_id\": \"ex4\"}]\n" +
                            "输出: {\"ex4\": {\"suggestion\": \"虚拟产品\", \"isNew\": true, \"fallback\": \"会员\"}}\n\n" +
                            "### 4. 约束逻辑\n" +
                            "- 建议格式：优先建议 '一级分类 - 二级分类'。若当前确无合适的分类，需新建分类，仅建议一级分类名。\n" +
                            "- Fallback：若不允许新建分类，必须指定一个【现有】最接近的一级分类。\n\n" +
                            "### 5. 输出要求\n" +
                            "- 严格返回 JSON 对象，Key 为 unique_id。\n" +
                            "- **严禁**包含任何 Markdown 标签或额外的文字说明。\n\n" +
                            "- 不要输出 reason 字段，也不要输出任何分析文字。\n" +
                            "### 6. 待处理明细\n" +
                            "%s",
                    gson.toJson(expenseTree),
                    gson.toJson(incomeTree),
                    customInstructions,
                    gson.toJson(billItems)
            );

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", MODEL_NAME);
            // java/cn/bit/budget/util/AICategorizer.java

            requestBody.put("messages", List.of(
                    Map.of("role", "system", "content",
                            "你是一个冷酷的 JSON 生成器。严禁输出任何思考过程（<think>）。" +
                                    "严禁对结果进行任何解释。直接输出 JSON 字典，不要包含 Markdown 代码块。"),
                    Map.of("role", "user", "content", prompt)
            ));
            requestBody.put("stream", false);
            requestBody.put("temperature", 0.1);
            requestBody.put("max_tokens", 20000); // 稍微调大一点，因为返回结构变复杂了
            requestBody.put("response_format", Map.of("type", "json_object")); // 强制 JSON 模式

            String jsonBody = gson.toJson(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + API_KEY)
                    .timeout(Duration.ofSeconds(50))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String rawBody = response.body();
            // 🔥 关键：增加打印原始响应，帮你抓出“毒账单”
            if (response.statusCode() != 200) {
                System.err.println("API 错误: " + rawBody);
            }

            // 解析逻辑
            Map<String, Object> respMap = gson.fromJson(response.body(), new TypeToken<Map<String, Object>>(){}.getType());
            List<Map<String, Object>> choices = (List<Map<String, Object>>) respMap.get("choices");
            String content = (String) ((Map<String, Object>) choices.get(0).get("message")).get("content");

            // 1. 过滤 DeepSeek 的思考块
            if (content.contains("</think>")) {
                content = content.split("</think>")[1].trim();
            }
            // 2. 终极 JSON 提取大法：不管 AI 废话多少，只取大括号里的内容
            int startJson = content.indexOf("{");
            int endJson = content.lastIndexOf("}");
            if (startJson != -1 && endJson != -1 && startJson < endJson) {
                content = content.substring(startJson, endJson + 1);
            } else {
                System.err.println("AI 返回的内容不含有效 JSON: " + content);
                return new HashMap<>();
            }
            // 解析为新的复杂结构
            try {
                return gson.fromJson(content, new TypeToken<Map<String, CategoryResult>>(){}.getType());
            } catch (Exception e) {
                // 4. 🔥 解析失败时，把那个“断掉的 JSON”打印出来
                System.err.println("Gson 解析失败！可能是被截断了：\n" + content);
                throw e;
            }

            } catch (Exception e) {
                e.printStackTrace();
                return new HashMap<>();
            }
        });
    }
}