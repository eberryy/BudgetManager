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
    private static final String MODEL_NAME = "deepseek-ai/DeepSeek-V3";

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

            // 升级版 Prompt 3.0
            String prompt = String.format(
                    "你是一个极其聪明的财务分类 Agent。请根据以下信息对账单进行分类。\n\n" +
                            "【1. 现有分类体系】\n" +
                            "支出分类树: %s\n" +
                            "收入分类树: %s\n\n" +
                            "【2. 用户个性化定义】(请务必严格遵守这些特定偏好):\n- %s\n\n" +
                            "【3. 待处理账单明细】\n%s\n\n" +
                            "【4. 核心逻辑要求】\n" +
                            "1. 首先根据金额正负判断：正数为收入，负数为支出。严禁混淆收支体系。\n" +
                            "2. 优先匹配二级分类。如果匹配，输出格式为 '一级分类 - 二级分类'。\n" +
                            "3. 无法完全匹配时，建议一个新的一级分类名称。禁止使用'其他'。\n" +
                            "4. 必须提供 'fallback'，即如果不允许创建新分类时，最接近的【现有分类库】中的一级分类。\n" +
                            "请严格返回一个 JSON 对象，其 Key 必须是待处理明细中提供的 unique_id，" +
                            "Value 是一个包含 suggestion, isNew, fallback 的对象。不要包含任何 Markdown 格式。",
                    gson.toJson(expenseTree),
                    gson.toJson(incomeTree),
                    customInstructions,
                    gson.toJson(billItems)
            );
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", MODEL_NAME);
            requestBody.put("messages", List.of(
                    Map.of("role", "system", "content", "你是一个只输出 JSON 的高智商财务 Agent。"),
                    Map.of("role", "user", "content", prompt)
            ));
            requestBody.put("stream", false);
            requestBody.put("temperature", 0.1);
            requestBody.put("max_tokens", 12000); // 稍微调大一点，因为返回结构变复杂了

            String jsonBody = gson.toJson(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + API_KEY)
                    .timeout(Duration.ofSeconds(90))
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