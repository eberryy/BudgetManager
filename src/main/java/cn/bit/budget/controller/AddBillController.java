package cn.bit.budget.controller;

import cn.bit.budget.model.Bill;
import cn.bit.budget.util.CategoryManager;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialog;
import com.jfoenix.controls.JFXDialogLayout;
import com.jfoenix.controls.JFXToggleButton;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;

public class AddBillController {

    // --- FXML 注入控件 ---
    @FXML private DatePicker datePicker;
    @FXML private RadioButton rbExpense;
    @FXML private RadioButton rbIncome;
    @FXML private ToggleGroup typeGroup;
    @FXML private Label titleLabel; // 🔥 新增：标题标签

    // 新的分类控件
    @FXML private ComboBox<String> parentCategoryBox;
    @FXML private ComboBox<String> childCategoryBox;

    @FXML private TextField amountField;
    @FXML private TextField remarkField;

    // --- 内部数据 ---
    private Bill resultBill = null;
    
    // 🔥 新增：编辑模式相关
    private boolean isEditMode = false;
    private Bill originalBill = null;

    // 注入根布局
    @FXML
    private StackPane rootPane;


    @FXML
    public void initialize() {
        // 1. 初始化日期为今天
        datePicker.setValue(LocalDate.now());

        // 2. 监听收支类型变化，动态更新一级分类列表
        typeGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            updateParentCategoryByType();
        });

        // 3. 初始化一级分类（不可编辑）
        parentCategoryBox.setEditable(false);
        updateParentCategoryByType(); // 根据默认选中的类型初始化分类

        // 4. 监听一级分类选择事件 (级联逻辑)
        parentCategoryBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                // 根据选中的一级分类，刷新二级分类列表
                childCategoryBox.getItems().clear();
                // 添加"无"选项作为第一项
                childCategoryBox.getItems().add("无");
                childCategoryBox.getItems().addAll(CategoryManager.getChildCategories(newVal));
                // 默认选中"无"
                childCategoryBox.getSelectionModel().selectFirst();
            }
        });
    }
    
    /**
     * 🔥 新增：设置编辑模式，填充现有账单数据
     * @param bill 要编辑的账单
     */
    public void setEditMode(Bill bill) {
        this.isEditMode = true;
        this.originalBill = bill;
        
        // 更新标题
        if (titleLabel != null) {
            titleLabel.setText("编辑账单");
        }
        
        // 填充数据
        datePicker.setValue(bill.getDate());
        amountField.setText(String.valueOf(bill.getAmount()));
        remarkField.setText(bill.getRemark());
        
        // 设置收支类型
        if ("收入".equals(bill.getType())) {
            rbIncome.setSelected(true);
        } else {
            rbExpense.setSelected(true);
        }
        
        // 等待类型更新后再设置分类
        javafx.application.Platform.runLater(() -> {
            // 设置一级分类
            if (bill.getCategory() != null) {
                parentCategoryBox.setValue(bill.getCategory());
            }
            
            // 等待一级分类更新后再设置二级分类
            javafx.application.Platform.runLater(() -> {
                if (bill.getSubCategory() != null && !bill.getSubCategory().isEmpty()) {
                    childCategoryBox.setValue(bill.getSubCategory());
                } else {
                    childCategoryBox.setValue("无");
                }
            });
        });
    }

    /**
     * 根据选中的收支类型更新一级分类列表
     */
    private void updateParentCategoryByType() {
        String currentSelection = parentCategoryBox.getValue();
        parentCategoryBox.getItems().clear();
        
        if (rbIncome.isSelected()) {
            // 收入类型：只显示"收入"分类
            parentCategoryBox.getItems().addAll(CategoryManager.getIncomeCategories());
        } else {
            // 支出类型：显示除"收入"外的所有分类
            parentCategoryBox.getItems().addAll(CategoryManager.getExpenseCategories());
        }
        
        // 尝试保持之前的选择，如果不在新列表中则选择第一个
        if (currentSelection != null && parentCategoryBox.getItems().contains(currentSelection)) {
            parentCategoryBox.setValue(currentSelection);
        } else if (!parentCategoryBox.getItems().isEmpty()) {
            parentCategoryBox.getSelectionModel().select(0);
        }
    }

    /**
     * 响应一级分类 "+" 按钮：添加自定义一级分类（带收支类型选择）
     */
    @FXML
    void onAddParentCategory(ActionEvent event) {
        // 创建自定义对话框布局
        JFXDialogLayout content = new JFXDialogLayout();
        content.setHeading(new Label("添加一级分类"));

        // 创建输入框
        TextField inputField = new TextField();
        inputField.setPromptText("请输入分类名称");
        inputField.getStyleClass().add("material-field");

        // 🔥 新增：收支类型选择开关
        Label typeLabel = new Label("收支类型：");
        typeLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #606266;");
        
        JFXToggleButton typeToggle = new JFXToggleButton();
        typeToggle.setText("支出");
        typeToggle.setStyle("-fx-font-size: 14px;");
        
        // 监听开关状态变化
        typeToggle.selectedProperty().addListener((obs, oldVal, newVal) -> {
            typeToggle.setText(newVal ? "收入" : "支出");
        });
        
        HBox typeBox = new HBox(10, typeLabel, typeToggle);
        typeBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        typeBox.setStyle("-fx-padding: 10 0 0 0;");

        VBox body = new VBox(10, inputField, typeBox);
        content.setBody(body);

        JFXDialog dialog = new JFXDialog(rootPane, content, JFXDialog.DialogTransition.CENTER);

        JFXButton btnCancel = new JFXButton("取消");
        btnCancel.setOnAction(e -> dialog.close());

        JFXButton btnConfirm = new JFXButton("确定");
        btnConfirm.setStyle("-fx-text-fill: #409eff; -fx-font-weight: bold;");
        btnConfirm.setOnAction(e -> {
            String newCategoryName = inputField.getText();
            if (newCategoryName != null && !newCategoryName.trim().isEmpty()) {
                newCategoryName = newCategoryName.trim();

                if (parentCategoryBox.getItems().contains(newCategoryName)) {
                    showAlert(Alert.AlertType.WARNING, "重复添加", "该分类 '" + newCategoryName + "' 已经存在！");
                    return;
                }
                
                // 🔥 根据开关状态确定类型
                String categoryType = typeToggle.isSelected() ? "收入" : "支出";
                
                // 添加分类并指定类型
                CategoryManager.addCustomParentCategory(newCategoryName, categoryType);
                
                // 🔥 只有当类型匹配当前收支类型时，才添加到下拉框
                String currentBillType = rbIncome.isSelected() ? "收入" : "支出";
                if (categoryType.equals(currentBillType)) {
                    parentCategoryBox.getItems().add(newCategoryName);
                    parentCategoryBox.getSelectionModel().select(newCategoryName);
                }
                
                dialog.close();
            }
        });

        content.setActions(btnCancel, btnConfirm);
        dialog.show();
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
        
        // 如果二级分类是"无"，则设为null
        if ("无".equals(subCat)) {
            subCat = null;
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

            // 🔥 修改：根据模式创建或更新账单
            if (isEditMode && originalBill != null) {
                // 编辑模式：更新现有账单
                this.resultBill = new Bill(
                        originalBill.getId(),  // 保持原ID
                        amount,
                        parentCat,
                        subCat,
                        date,
                        type,
                        remark,
                        originalBill.getCreateTime()  // 保持原创建时间
                );
            } else {
                // 新增模式：创建新账单
                this.resultBill = new Bill(
                        UUID.randomUUID().toString(),
                        amount,
                        parentCat,
                        subCat,
                        date,
                        type,
                        remark,
                        LocalDateTime.now()
                );
            }

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