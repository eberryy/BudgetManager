package cn.bit.budget.controller;

import cn.bit.budget.model.Bill;
import cn.bit.budget.util.CategoryManager;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialog;
import com.jfoenix.controls.JFXDialogLayout;
import javafx.scene.layout.VBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

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

    // 注入根布局
    @FXML
    private StackPane rootPane;


    @FXML
    public void initialize() {
        // 1. 初始化日期为今天
        datePicker.setValue(LocalDate.now());

        // 2. 初始化一级分类（不可编辑）
        parentCategoryBox.getItems().addAll(CategoryManager.getParentCategories());
        parentCategoryBox.setEditable(false); // 改为不可编辑

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
     * 响应一级分类 "+" 按钮：添加自定义一级分类
     */
    @FXML
    void onAddParentCategory(ActionEvent event) {
        // 1. 调用弹窗获取用户输入
        showModernInputDialog("添加一级分类", "请输入分类名称", (newCategoryName) -> {
            if (newCategoryName != null && !newCategoryName.trim().isEmpty()) {
                newCategoryName = newCategoryName.trim(); // 去除首尾空格

                if (parentCategoryBox.getItems().contains(newCategoryName)) {
                    showAlert(Alert.AlertType.WARNING, "重复添加", "该分类 '" + newCategoryName + "' 已经存在！");
                    return;
                }
                CategoryManager.addCustomParentCategory(newCategoryName);
                parentCategoryBox.getItems().add(newCategoryName);
                parentCategoryBox.getSelectionModel().select(newCategoryName);
            }
        });
    }

    /**
     * 显示一个文本输入对话框
     * @param title   对话框标题
     * @param prompt 对话框提示内容
     */
    private void showModernInputDialog(String title, String prompt, java.util.function.Consumer<String> onConfirm) {
        JFXDialogLayout content = new JFXDialogLayout();
        content.setHeading(new Label(title));

        TextField inputField = new TextField();
        inputField.setPromptText(prompt);
        inputField.getStyleClass().add("material-field"); // 确保你的 css 链接到了这个 fxml

        VBox body = new VBox(inputField);
        content.setBody(body);

        JFXDialog dialog = new JFXDialog(rootPane, content, JFXDialog.DialogTransition.CENTER);

        JFXButton btnCancel = new JFXButton("取消");
        btnCancel.setOnAction(e -> dialog.close());

        JFXButton btnConfirm = new JFXButton("确定");
        btnConfirm.setStyle("-fx-text-fill: #409eff; -fx-font-weight: bold;");
        btnConfirm.setOnAction(e -> {
            onConfirm.accept(inputField.getText());
            dialog.close();
        });

        content.setActions(btnCancel, btnConfirm);
        dialog.show();
    }

    /**
     * 通用弹窗辅助方法 (重载)
     * 支持自定义类型和标题
     */
    private void showAlert(Alert.AlertType alertType, String title, String content) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }


    /**
     * 响应二级分类 "+" 按钮：添加自定义二级分类 (JFoenix 现代化版)
     */
    @FXML
    void onAddChildCategory(ActionEvent event) {
        String currentParent = parentCategoryBox.getValue();
        if (currentParent == null) {
            // 这里也可以顺便改成 JFoenix 的提示，或者保留 Alert
            showAlert("请先选择一级分类");
            return;
        }

        // 调用现代化弹窗
        // 技巧：把当前父分类的名字拼接到标题里，让用户清楚自己在给谁加子分类
        showModernInputDialog(
                "新增二级分类 (" + currentParent + ")",
                "请输入分类名称",
                (name) -> {
                    // 这里的回调逻辑和之前一样
                    if (name != null && !name.trim().isEmpty()) {
                        String cleanName = name.trim();

                        // 1. 存入管理器（会自动保存）
                        CategoryManager.addCustomChildCategory(currentParent, cleanName);

                        // 2. 刷新当前下拉框
                        childCategoryBox.getItems().add(cleanName);
                        childCategoryBox.getSelectionModel().select(cleanName);
                    }
                }
        );
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
        
        // 允许手动输入二级分类 (getEditor().getText() 用于获取可编辑 ComboBox 的输入)
        String subCat = childCategoryBox.getValue();
        if (subCat == null && childCategoryBox.getEditor() != null) {
            subCat = childCategoryBox.getEditor().getText();
        }

        // 校验
        if (date == null || parentCat == null || parentCat.trim().isEmpty() || amountStr == null || amountStr.trim().isEmpty()) {
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
            
            // 如果用户手输了一个新的二级分类，自动保存到 CategoryManager
            if (subCat != null && !subCat.trim().isEmpty()) {
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