package cn.bit.budget.dao;

import cn.bit.budget.model.Bill;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;


/**
 * 数据存储类 (V3.0 - SQLite 数据库版)
 * 相比 CSV 版本：支持 ACID 事务、毫秒级查询、数据类型强制约束
 */
public class DataStore {

    private static final String DB_URL = "jdbc:sqlite:budget_manager.db";
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    static {
        // 1. 初始化数据库表结构
        initDatabase();
    }

    private DataStore() {}

    /**
     * 初始化数据库：如果表不存在则创建
     */
    private static void initDatabase() {
        String sql = """
            CREATE TABLE IF NOT EXISTS bills (
                id TEXT PRIMARY KEY,
                amount REAL NOT NULL,
                category TEXT NOT NULL,
                sub_category TEXT,
                date TEXT NOT NULL,
                type TEXT NOT NULL,
                remark TEXT,
                create_time TEXT NOT NULL
            );
            """;
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            System.err.println("数据库初始化失败: " + e.getMessage());
        }
    }

    /**
     * 全量保存账单（兼容原有逻辑）
     * 采用“删除记录+事务批处理插入”方案，确保原子性
     */
    public static void saveBills(List<Bill> bills) {
        String deleteSql = "DELETE FROM bills";
        String insertSql = "INSERT INTO bills VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            conn.setAutoCommit(false); // 🔥 开启事务

            // 1. 先清空表（对应原来 CSV 的覆盖写入）
            try (Statement delStmt = conn.createStatement()) {
                delStmt.executeUpdate(deleteSql);
            }

            // 2. 批量插入
            try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                for (Bill bill : bills) {
                    pstmt.setString(1, bill.getId());
                    pstmt.setDouble(2, bill.getAmount());
                    pstmt.setString(3, bill.getCategory());
                    pstmt.setString(4, bill.getSubCategory());
                    pstmt.setString(5, bill.getDate().toString());
                    pstmt.setString(6, bill.getType());
                    pstmt.setString(7, bill.getRemark());
                    pstmt.setString(8, bill.getCreateTime().format(DATETIME_FORMATTER));
                    pstmt.addBatch(); // 添加到批处理
                }
                pstmt.executeBatch(); // 🔥 执行批处理
            }

            conn.commit(); // 🔥 提交事务
        } catch (SQLException e) {
            System.err.println("保存数据库失败，已回滚: " + e.getMessage());
        }
    }

    /**
     * 从数据库加载所有账单
     */
    public static List<Bill> loadBills() {
        List<Bill> bills = new ArrayList<>();
        String sql = "SELECT * FROM bills ORDER BY date DESC, create_time DESC";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                Bill bill = new Bill(
                        rs.getString("id"),
                        rs.getDouble("amount"),
                        rs.getString("category"),
                        rs.getString("sub_category"),
                        LocalDate.parse(rs.getString("date")),
                        rs.getString("type"),
                        rs.getString("remark"),
                        LocalDateTime.parse(rs.getString("create_time"), DATETIME_FORMATTER)
                );
                bills.add(bill);
            }
        } catch (SQLException e) {
            System.err.println("读取数据库失败: " + e.getMessage());
        }

        System.out.println("成功从 SQLite 加载 " + bills.size() + " 条账单记录。");
        return bills;
    }

    /**
     * 删除指定一级分类的所有账单（原生 SQL 实现，效率极高）
     */
    public static int deleteBillsByCategory(String category) {
        String sql = "DELETE FROM bills WHERE category = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, category);
            return pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("删除分类账单失败: " + e.getMessage());
            return 0;
        }
    }

    /**
     * 删除指定二级分类的所有账单
     */
    public static int deleteBillsBySubCategory(String parentCategory, String subCategory) {
        String sql = "DELETE FROM bills WHERE category = ? AND sub_category = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, parentCategory);
            pstmt.setString(2, subCategory);
            return pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("删除二级分类账单失败: " + e.getMessage());
            return 0;
        }
    }

}

