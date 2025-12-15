package cn.bit.budget.dao;

import cn.bit.budget.model.Bill;

import java.io.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.OutputStreamWriter;
import java.io.InputStreamReader;

/**
 * 数据存储类 (V2.1)
 * 适配二级分类字段 (id, amount, category, subCategory, date, type, remark, createTime)
 */
public class DataStore {

    private static final String FILE_NAME = "budget_data.csv";
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private DataStore() {
    }


    public static void saveBills(List<Bill> bills) {
        // 【修复】使用 OutputStreamWriter 强制指定 UTF-8 编码写入
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(FILE_NAME), StandardCharsets.UTF_8))) { //

            for (Bill bill : bills) {
                String line = String.format("%s,%.2f,%s,%s,%s,%s,%s,%s",
                        bill.getId(),
                        bill.getAmount(),
                        escapeCsv(bill.getCategory()),
                        escapeCsv(bill.getSubCategory()),
                        bill.getDate().toString(),
                        escapeCsv(bill.getType()),
                        escapeCsv(bill.getRemark()),
                        bill.getCreateTime().format(DATETIME_FORMATTER)
                );
                writer.write(line);
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("保存数据错误：" + e.getMessage());
        }
    }

    public static List<Bill> loadBills() {
        List<Bill> bills = new ArrayList<>();
        File file = new File(FILE_NAME);
        if (!file.exists()) return bills;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;

                try {
                    String[] parts = line.split(",", -1);

                    // 兼容旧数据逻辑：如果只有 7 列，说明是老版本数据
                    // 新版本应该是 8 列

                    String id = parts[0];
                    double amount = Double.parseDouble(parts[1]);
                    String category = unescapeCsv(parts[2]);

                    String subCategory = null;
                    LocalDate date;
                    String type;
                    String remark;
                    LocalDateTime createTime;

                    if (parts.length >= 8) {
                        // 新版数据格式
                        subCategory = unescapeCsv(parts[3]);
                        date = LocalDate.parse(parts[4]);
                        type = unescapeCsv(parts[5]);
                        remark = unescapeCsv(parts[6]);
                        createTime = LocalDateTime.parse(parts[7], DATETIME_FORMATTER);
                    } else if (parts.length == 7) {
                        // 旧版数据兼容 (subCategory 默认为 null)
                        // 旧格式：id, amount, category, date, type, remark, createTime
                        date = LocalDate.parse(parts[3]);
                        type = unescapeCsv(parts[4]);
                        remark = unescapeCsv(parts[5]);
                        createTime = LocalDateTime.parse(parts[6], DATETIME_FORMATTER);
                    } else {
                        continue; // 无效行
                    }

                    Bill bill = new Bill(id, amount, category, subCategory, date, type, remark, createTime);
                    bills.add(bill);

                } catch (Exception e) {
                    System.err.println("跳过错误行: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        bills.sort((b1, b2) -> {
            if (b2.getDate().equals(b1.getDate())) {
                // 如果日期相同，按创建时间倒序（后记的在上面）
                return b2.getCreateTime().compareTo(b1.getCreateTime());
            }
            return b2.getDate().compareTo(b1.getDate());
        });

        System.out.println(
                "成功加载 " + bills.size() + " 条账单记录。"
        );
        return bills;
    }

    private static String escapeCsv(String value) {
        return (value == null || value.isEmpty()) ? "" : value.replace(",", "&#44;").trim();
    }

    private static String unescapeCsv(String value) {
        return (value == null || value.isEmpty()) ? "" : value.replace("&#44;", ",");
    }
}