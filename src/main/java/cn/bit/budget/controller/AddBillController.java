package cn.bit.budget.controller;

import cn.bit.budget.model.Bill;
import cn.bit.budget.util.CategoryManager;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public class AddBillController {

    // --- FXML 注入控件 ---
    @FXML private DatePicker datePicker;
    @FXML private RadioButton rbExpense;
    @FXML private RadioButton rbIncome;
    @FXML private ToggleGroup typeGroup;

    // 新的分类控件
    @FXML private ComboBox<String> parentCategoryBox;
    @FXML private ComboBox<String> childCategoryBox;

    @FXML private TextField amountField;
    @FXML private TextField remarkField;

    // --- 内部数据 ---
    private Bill resultBill = null;

    @FXML
    public void initialize() {
        // 1. 初始化日期为今天
        datePicker.setValue(LocalDate.now());

        // 2. 初始化一级分类
        parentCategoryBox.getItems().addAll(CategoryManager.getParentCategories());
        parentCategoryBox.setEditable(true);

        // 3. 监听一级分类选择事件 (级联逻辑)
        parentCategoryBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                // 根据选中的一级分类，刷新二级分类列表
                childCategoryBox.getItems().clear();
                childCategoryBox.getItems().addAll(CategoryManager.getChildCategories(newVal));
                childCategoryBox.getSelectionModel().selectFirst();
            }
        });

        // 默认选中第一个
        if (!parentCategoryBox.getItems().isEmpty()) {
            parentCategoryBox.getSelectionModel().select(0);
        }
    }

    /**
     * 响应 "+" 按钮：添加自定义分类
     */
    @FXML
    void onAddCustomCategory(ActionEvent event) {
        String currentParent = parentCategoryBox.getValue();
        if (currentParent == null) {
            showAlert("请先选择一级分类");
            return;
        }

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("新增分类");
        dialog.setHeaderText("在【" + currentParent + "】下添加子分类");
        dialog.setContentText("分类名称:");

        dialog.showAndWait().ifPresent(name -> {
            if (!name.trim().isEmpty()) {
                // 1. 存入管理器
                CategoryManager.addCustomChildCategory(currentParent, name);
                // 2. 刷新当前下拉框
                childCategoryBox.getItems().add(name);
                childCategoryBox.getSelectionModel().select(name);
            }
        });
    }

    /**
     * 点击“保存”
     */
    @FXML
    void onSave(ActionEvent event) {
        LocalDate date = datePicker.getValue();
        String amountStr = amountField.getText();

        // 获取分类
        String parentCat = parentCategoryBox.getValue();
        //允许手动输入一级分类 (getValue() 用于获取不可编辑 ComboBox 的值)
        if (parentCat == null || parentCat.trim().isEmpty()) {
            // 如果 getValue 为空，尝试获取编辑器里的文本
            parentCat = parentCategoryBox.getEditor().getText();
        }
        // 允许手动输入二级分类 (getEditor().getText() 用于获取可编辑 ComboBox 的输入)
        String subCat = childCategoryBox.getValue();
        if (subCat == null && childCategoryBox.getEditor() != null) {
            subCat = childCategoryBox.getEditor().getText();
        }

        // 校验
        if (date == null || parentCat == null || amountStr == null || amountStr.trim().isEmpty()) {
            showAlert("请填写完整信息（日期、一级分类、金额）。");
            return;
        }

        try {
            double amount = Double.parseDouble(amountStr);
            if (amount < 0) {
                showAlert("金额不能为负数。");
                return;
            }

            String type = rbExpense.isSelected() ? "支出" : "收入";
            String remark = remarkField.getText();
            if (remark == null) remark = "";
            // 【新增】如果是一级新分类，注册到 Manager
            if (!CategoryManager.getParentCategories().contains(parentCat)) {
                CategoryManager.addCustomParentCategory(parentCat);
            }
            // 如果用户手输了一个新的二级分类，自动保存到 CategoryManager
            if (subCat != null && !subCat.isEmpty()) {
                CategoryManager.addCustomChildCategory(parentCat, subCat);
            }

            // --- 关键修正：使用 Bill 新的构造函数 (包含 subCategory) ---
            this.resultBill = new Bill(
                    UUID.randomUUID().toString(),
                    amount,
                    parentCat,  // 一级
                    subCat,     // 二级
                    date,
                    type,
                    remark,
                    LocalDateTime.now()
            );

            closeWindow();

        } catch (NumberFormatException e) {
            showAlert("金额格式不正确，请输入数字。");
        }
    }

    @FXML
    void onCancel(ActionEvent event) {
        this.resultBill = null;
        closeWindow();
    }

    public Bill getBill() {
        return resultBill;
    }

    private void closeWindow() {
        Stage stage = (Stage) amountField.getScene().getWindow();
        stage.close();
    }

    private void showAlert(String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("提示");
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}