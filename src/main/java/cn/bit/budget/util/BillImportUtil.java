package cn.bit.budget.util;

import cn.bit.budget.model.Bill;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.nio.charset.Charset; // 引入 Charset
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 账单导入工具类 (V2.2 - 修复导入乱码版)
 * 适配二级分类模型
 */
public class BillImportUtil {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private BillImportUtil() {}

    public static List<Bill> parse(File file) {
        String fileName = file.getName().toLowerCase();
        if (fileName.endsWith(".csv")) {
            return parseWeChatCSV(file);
        } else if (fileName.endsWith(".xlsx") || fileName.endsWith(".xls")) {
            return parseWeChatExcel(file);
        } else {
            System.err.println("不支持的文件格式: " + fileName);
            return new ArrayList<>();
        }
    }

    private static List<Bill> parseWeChatExcel(File file) {
        // Excel 文件 (xlsx) 内部是 XML 结构，POI 会自动处理编码，通常不需要改动
        List<Bill> importedBills = new ArrayList<>();
        boolean isDataStart = false;

        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                if (row == null) continue;

                String firstCellVal = getCellValue(row.getCell(0));
                if (!isDataStart) {
                    if (firstCellVal.contains("交易时间")) isDataStart = true;
                    continue;
                }

                try {
                    String timeStr = getCellValue(row.getCell(0));
                    if (timeStr.isEmpty()) continue;

                    LocalDateTime dateTime = LocalDateTime.parse(timeStr, TIME_FORMATTER);
                    LocalDate date = dateTime.toLocalDate();

                    String category = getCellValue(row.getCell(1));
                    String partner = getCellValue(row.getCell(2));
                    String goods = getCellValue(row.getCell(3));
                    String type = getCellValue(row.getCell(4));
                    String amountStr = getCellValue(row.getCell(5)).replace("¥", "").trim();
                    double amount = Double.parseDouble(amountStr);

                    String remark = partner + "-" + goods + " (导入)";

                    Bill bill = new Bill(
                            UUID.randomUUID().toString(),
                            amount,
                            category,
                            null,
                            date,
                            type,
                            remark,
                            LocalDateTime.now()
                    );
                    importedBills.add(bill);

                } catch (Exception e) {
                    System.err.println("Excel 行解析失败: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return importedBills;
    }

    private static List<Bill> parseWeChatCSV(File file) {
        List<Bill> importedBills = new ArrayList<>();
        boolean isDataStart = false;

        // 【关键修复】
        // 微信/支付宝导出的 CSV 通常是 GBK 编码。
        // 如果这里强行用 UTF-8 读，中文就会变成 ""
        Charset csvCharset = Charset.forName("GBK");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), csvCharset))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                // 微信账单头部可能有非CSV格式的说明，跳过直到找到表头
                if (!isDataStart) {
                    if (line.contains("交易时间")) isDataStart = true;
                    continue;
                }

                try {
                    String[] columns = line.split(",", -1);
                    if (columns.length < 6) continue;

                    String timeStr = clean(columns[0]);
                    LocalDateTime dateTime = LocalDateTime.parse(timeStr, TIME_FORMATTER);
                    LocalDate date = dateTime.toLocalDate();

                    String category = clean(columns[1]); // 这里必须是正确的中文，否则 CategoryManager 匹配不到 Emoji
                    String partner = clean(columns[2]);
                    String goods = clean(columns[3]);
                    String type = clean(columns[4]);
                    String amountStr = clean(columns[5]).replace("¥", "");
                    double amount = Double.parseDouble(amountStr);

                    String remark = partner + "-" + goods + " (导入)";

                    Bill bill = new Bill(
                            UUID.randomUUID().toString(),
                            amount,
                            category,
                            null,
                            date,
                            type,
                            remark,
                            LocalDateTime.now()
                    );
                    importedBills.add(bill);

                } catch (Exception e) {
                    // ignore format errors
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return importedBills;
    }

    private static String clean(String text) {
        return text == null ? "" : text.replace("\"", "").trim();
    }

    private static String getCellValue(Cell cell) {
        if (cell == null) return "";
        try {
            switch (cell.getCellType()) {
                case STRING: return cell.getStringCellValue().trim();
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) return cell.getLocalDateTimeCellValue().format(TIME_FORMATTER);
                    return String.valueOf(cell.getNumericCellValue());
                case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
                default: return "";
            }
        } catch (Exception e) { return ""; }
    }
}