package cn.bit.budget.util;

import cn.bit.budget.model.Bill;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 账单导入工具类 (V5.1)
 * 修复了 isHeaderRow 缺失、日期解析异常及列索引偏移问题
 */
public class BillImportUtil {

    private BillImportUtil() {}

    public static List<Bill> parse(File file) {
        String fileName = file.getName().toLowerCase();
        if (fileName.endsWith(".xlsx") || fileName.endsWith(".xls")) {
            return parseExcel(file);
        } else if (fileName.endsWith(".csv")) {
            // 依次尝试 GBK (微信/支付宝默认) 和 UTF-8
            List<Bill> bills = parseCSV(file, "GBK");
            if (bills.isEmpty()) {
                bills = parseCSV(file, "UTF-8");
            }
            return bills;
        }
        return new ArrayList<>();
    }

    /**
     * CSV 解析核心：动态表头定位
     */
    private static List<Bill> parseCSV(File file, String charset) {
        List<Bill> importedBills = new ArrayList<>();
        Map<String, Integer> colMap = new HashMap<>();
        boolean isDataStarted = false;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), Charset.forName(charset)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                // 标准 CSV 分割，处理内容中包含逗号的情况
                String[] columns = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);

                if (!isDataStarted) {
                    if (isHeaderRow(line)) {
                        colMap = mapHeaders(columns);
                        isDataStarted = true;
                    }
                    continue;
                }

                if (line.startsWith("---")) continue; // 跳过统计分隔线
                try {
                    Bill bill = createBillFromRow(columns, colMap);
                    if (bill != null) importedBills.add(bill);
                } catch (Exception e) {
                    System.err.println("跳过无效行: " + line + " | 原因: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return importedBills;
    }

    /**
     * Excel 解析核心：动态表头定位
     */
    private static List<Bill> parseExcel(File file) {
        List<Bill> importedBills = new ArrayList<>();
        Map<String, Integer> colMap = new HashMap<>();
        boolean isDataStarted = false;

        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                String[] rowData = getRowContent(row);
                if (rowData.length < 3) continue;

                if (!isDataStarted) {
                    // 检查这一行是否包含表头关键词
                    if (isHeaderRow(Arrays.toString(rowData))) {
                        colMap = mapHeaders(rowData);
                        isDataStarted = true;
                    }
                    continue;
                }

                try {
                    Bill bill = createBillFromRow(rowData, colMap);
                    if (bill != null) importedBills.add(bill);
                } catch (Exception e) {
                    // 记录解析异常
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return importedBills;
    }

    // ================== 辅助方法 ==================

    /**
     * 判断当前行是否为表头行
     */
    private static boolean isHeaderRow(String line) {
        return line.contains("时间") && line.contains("金额") &&
                (line.contains("收/支") || line.contains("类型"));
    }

    /**
     * 智能映射列索引：排除包含“单号”干扰的列，精准抓取商户和描述
     */
    private static Map<String, Integer> mapHeaders(String[] heads) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < heads.length; i++) {
            String h = clean(heads[i]);
            if (h.contains("时间")) map.put("time", i);
            else if (h.contains("金额")) map.put("amount", i);
            else if (h.contains("收/支") || h.equals("类型")) map.put("type", i);
                // 排除掉包含“单号”的列，防止抓错描述
            else if ((h.contains("商品") || h.contains("说明")) && !h.contains("单号")) {
                map.put("goods", i);
            }
            else if ((h.contains("交易对方") || h.contains("商户")) && !h.contains("单号")) {
                map.put("partner", i);
            }
        }
        return map;
    }

    /**
     * 根据映射关系从数组中构建 Bill 对象
     */
    private static Bill createBillFromRow(String[] cols, Map<String, Integer> colMap) throws Exception {
        if (!colMap.containsKey("time") || !colMap.containsKey("amount") || !colMap.containsKey("type")) {
            return null;
        }

        String rawDate = clean(cols[colMap.get("time")]);
        String rawAmount = clean(cols[colMap.get("amount")]).replace("¥", "").replace(",", "");
        String rawType = clean(cols[colMap.get("type")]);

        // 日期解析
        LocalDate date = parseFlexibleDate(rawDate);
        // 金额解析
        double amount = Double.parseDouble(rawAmount);
        // 收支归一化
        String type = rawType.contains("收入") ? "收入" : "支出";

        String partner = colMap.containsKey("partner") ? clean(cols[colMap.get("partner")]) : "";
        String goods = colMap.containsKey("goods") ? clean(cols[colMap.get("goods")]) : "";
        String remark = (partner + "-" + goods).trim() + " (导入)";

        return new Bill(UUID.randomUUID().toString(), amount, "未分类", null, date, type, remark, LocalDateTime.now());
    }

    /**
     * 暴力日期解析器：兼容 - 和 / 以及各种精度
     */
    private static LocalDate parseFlexibleDate(String raw) throws Exception {
        String[] patterns = {
                "yyyy-MM-dd HH:mm:ss", "yyyy/MM/dd HH:mm:ss",
                "yyyy-MM-dd HH:mm", "yyyy/MM/dd HH:mm",
                "yyyy-MM-dd", "yyyy/MM/dd"
        };

        for (String pattern : patterns) {
            try {
                if (pattern.length() > 10) {
                    return LocalDateTime.parse(raw, DateTimeFormatter.ofPattern(pattern)).toLocalDate();
                } else {
                    return LocalDate.parse(raw, DateTimeFormatter.ofPattern(pattern));
                }
            } catch (Exception ignored) {}
        }
        throw new Exception("无法解析日期格式: " + raw);
    }

    private static String clean(String s) {
        if (s == null) return "";
        // 移除引号、制表符及前后空格
        return s.replace("\"", "").replace("\t", "").trim();
    }

    private static String[] getRowContent(Row row) {
        int lastCellNum = row.getLastCellNum();
        if (lastCellNum < 0) return new String[0];
        String[] content = new String[lastCellNum];
        for (int i = 0; i < lastCellNum; i++) {
            content[i] = getCellValue(row.getCell(i));
        }
        return content;
    }

    private static String getCellValue(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING: return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                }
                return String.valueOf(cell.getNumericCellValue());
            default: return "";
        }
    }
}