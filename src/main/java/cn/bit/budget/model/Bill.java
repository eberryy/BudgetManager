package cn.bit.budget.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 账单实体类 (Bill)
 * <p>
 * 该类用于映射数据库中的账单记录，包含每一笔收支的详细信息。
 * 它是个人记账软件核心业务模型的一部分。
 * </p>
 *
 * @author User
 * @version 1.1
 * @since 2025-12-08
 */
public class Bill {

    /**
     * 账单唯一标识符 (UUID)
     * 通常使用 java.util.UUID.randomUUID().toString() 生成
     */
    private String id;

    /**
     * 账单金额
     * 单位：元
     */
    private double amount;

    /**
     * 账单一级分类
     * 例如：餐饮、交通、购物、工资等
     */
    private String category;

    /**
     * 账单二级分类
     * 例如：餐饮下的“三餐”、“咖啡”；交通下的“地铁”、“打车”
     */
    private String subCategory;

    /**
     * 账单发生的日期
     * 格式：yyyy-MM-dd
     */
    private LocalDate date;

    /**
     * 账单类型
     * 用于区分是支出还是收入（例如："支出" 或 "收入"）
     */
    private String type;

    /**
     * 账单备注信息
     * 用于记录额外的说明内容
     */
    private String remark;

    /**
     * 记录创建时间
     * 用于记录该条数据插入数据库的具体时间
     */
    private LocalDateTime createTime;

    /**
     * 无参构造函数
     * <p>
     * 创建一个空的 Bill 对象实例。
     * 通常用于反射或序列化框架使用。
     * </p>
     */
    public Bill() {
    }

    /**
     * 全参构造函数
     * <p>
     * 创建并初始化一个完整的 Bill 对象实例。
     * </p>
     *
     * @param id          唯一标识符
     * @param amount      金额
     * @param category    一级分类
     * @param subCategory 二级分类
     * @param date        日期
     * @param type        类型（支出/收入）
     * @param remark      备注
     * @param createTime  创建时间
     */
    public Bill(String id, double amount, String category, String subCategory, LocalDate date, String type, String remark, LocalDateTime createTime) {
        this.id = id;
        this.amount = amount;
        this.category = category;
        this.subCategory = subCategory;
        this.date = date;
        this.type = type;
        this.remark = remark;
        this.createTime = createTime;
    }

    /**
     * 获取账单唯一标识符
     *
     * @return 账单的 ID
     */
    public String getId() {
        return id;
    }

    /**
     * 设置账单唯一标识符
     *
     * @param id 新的账单 ID
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * 获取账单金额
     *
     * @return 账单的金额
     */
    public double getAmount() {
        return amount;
    }

    /**
     * 设置账单金额
     *
     * @param amount 新的账单金额
     */
    public void setAmount(double amount) {
        this.amount = amount;
    }

    /**
     * 获取账单一级分类
     *
     * @return 账单的一级分类名称
     */
    public String getCategory() {
        return category;
    }

    /**
     * 设置账单一级分类
     *
     * @param category 新的一级分类
     */
    public void setCategory(String category) {
        this.category = category;
    }

    /**
     * 获取账单二级分类
     *
     * @return 账单的二级分类名称
     */
    public String getSubCategory() {
        return subCategory;
    }

    /**
     * 设置账单二级分类
     *
     * @param subCategory 新的二级分类
     */
    public void setSubCategory(String subCategory) {
        this.subCategory = subCategory;
    }

    /**
     * 获取账单日期
     *
     * @return 账单发生的日期
     */
    public LocalDate getDate() {
        return date;
    }

    /**
     * 设置账单日期
     *
     * @param date 新的账单日期
     */
    public void setDate(LocalDate date) {
        this.date = date;
    }

    /**
     * 获取账单类型
     *
     * @return 账单类型（支出/收入）
     */
    public String getType() {
        return type;
    }

    /**
     * 设置账单类型
     *
     * @param type 新的账单类型
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * 获取账单备注
     *
     * @return 账单的备注信息
     */
    public String getRemark() {
        return remark;
    }

    /**
     * 设置账单备注
     *
     * @param remark 新的备注信息
     */
    public void setRemark(String remark) {
        this.remark = remark;
    }

    /**
     * 获取记录创建时间
     *
     * @return 记录的创建时间
     */
    public LocalDateTime getCreateTime() {
        return createTime;
    }

    /**
     * 设置记录创建时间
     *
     * @param createTime 新的创建时间
     */
    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    /**
     * 重写 toString 方法
     * <p>
     * 返回该对象的字符串表示形式，包含所有属性值。
     * </p>
     *
     * @return Bill 对象的字符串表示
     */
    @Override
    public String toString() {
        return "Bill{" +
                "id='" + id + '\'' +
                ", amount=" + amount +
                ", category='" + category + '\'' +
                ", subCategory='" + subCategory + '\'' +
                ", date=" + date +
                ", type='" + type + '\'' +
                ", remark='" + remark + '\'' +
                ", createTime=" + createTime +
                '}';
    }
}