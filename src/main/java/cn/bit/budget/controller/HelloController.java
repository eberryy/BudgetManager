package cn.bit.budget.controller;

import cn.bit.budget.dao.DataStore;
import cn.bit.budget.model.Bill;
import cn.bit.budget.util.AICategorizer;
import cn.bit.budget.util.BillImportUtil;
import cn.bit.budget.util.CategoryManager;
import com.jfoenix.controls.*;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.PieChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import javafx.scene.layout.Region;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.net.URL;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import javafx.scene.paint.Color;
import javafx.scene.layout.VBox;
import java.util.function.Consumer;
import javafx.util.Duration;

import javafx.geometry.Pos;
import javafx.geometry.Insets;

/**
 * 主界面控制器 (V2.0)
 * 增加了筛选、统计图表和数据流管理
 */
public class HelloController implements Initializable {

    // --- 筛选控件 ---
    @FXML
    private DatePicker startDatePicker;
    @FXML
    private DatePicker endDatePicker;
    @FXML
    private ComboBox<String> filterCategoryBox;
    @FXML
    private ComboBox<String> filterSubCategoryBox;
    @FXML
    private ComboBox<String> typeFilterBox;

    // --- 表格控件 ---
    @FXML
    private TableView<Bill> billTable;
    @FXML
    private TableColumn<Bill, LocalDate> colDate;
    @FXML
    private TableColumn<Bill, String> colCategory;
    @FXML
    private TableColumn<Bill, Double> colAmount;
    @FXML
    private TableColumn<Bill, String> colRemark;

    // --- 图表控件 ---
    @FXML
    private PieChart expensePieChart;

    // --- 按钮控件 ---
    @FXML
    private Button btnAdd;
    @FXML
    private Button btnDelete;
    @FXML
    private Button btnImport;

    // 注入 StackPane
    @FXML
    private StackPane rootStackPane;

    // --- 设置控件 ---
    @FXML
    private boolean isAutoCreateCategory = false; // 默认为关闭（安全模式）



    // --- 核心数据源 ---
    // 内存中保存的所有账单数据 (Master List)
    private List<Bill> allBills = new ArrayList<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // --- 初始化收支类型筛选 ---
        typeFilterBox.getItems().addAll("全部", "支出", "收入");
        typeFilterBox.setValue("全部");

        // 监听收支类型变化，动态更新分类筛选列表
        typeFilterBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            updateCategoryFilterByType();
        });

        // 初始化一级分类
        updateCategoryFilterByType();

        // 监听一级分类变化，动态更新二级分类筛选列表
        filterCategoryBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            updateSubCategoryFilter();
        });

        // 初始化二级分类
        filterSubCategoryBox.getItems().add("全部");
        filterSubCategoryBox.setValue("全部");

        // ============================================================
        // 【🔥🔥 找回这一段：绑定数据列 (核心修复) 🔥🔥】
        // 告诉表格列：你去 Bill 对象的哪个属性里拿数据？
        // ============================================================
        colDate.setCellValueFactory(new PropertyValueFactory<>("date"));
        colCategory.setCellValueFactory(new PropertyValueFactory<>("category")); // 对应 Bill.category
        colAmount.setCellValueFactory(new PropertyValueFactory<>("amount"));     // 对应 Bill.amount
        colRemark.setCellValueFactory(new PropertyValueFactory<>("remark"));     // 对应 Bill.remark

        // ============================================================
        //  UI 美化逻辑 (保持不变)
        // ============================================================

        // 1. 设置金额列：支出显示红色，收入显示绿色
        colAmount.setCellFactory(column -> new TableCell<Bill, Double>() {
            @Override
            protected void updateItem(Double amount, boolean empty) {
                super.updateItem(amount, empty);
                if (empty || amount == null) {
                    setText(null);
                    setStyle("");
                } else {
                    Bill currentBill = getTableView().getItems().get(getIndex());
                    if ("支出".equals(currentBill.getType())) {
                        setText("- " + String.format("%.2f", Math.abs(amount)));
                        setTextFill(Color.RED);
                    } else {
                        setText("+ " + String.format("%.2f", Math.abs(amount)));
                        setTextFill(Color.GREEN);
                    }
                }
            }
        });

        // 2. 设置分类列：使用 ImageView 加载 Twemoji 图片，实现全平台彩色显示
        colCategory.setCellFactory(column -> new TableCell<Bill, String>() {
            private final javafx.scene.image.ImageView imageView = new javafx.scene.image.ImageView();

            {
                // 初始化 ImageView 大小
                imageView.setFitHeight(20);
                imageView.setFitWidth(20);
                imageView.setPreserveRatio(true);
            }

            @Override
            protected void updateItem(String category, boolean empty) {
                super.updateItem(category, empty);
                if (empty || category == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    // 获取当前行的Bill对象
                    Bill currentBill = getTableView().getItems().get(getIndex());
                    String subCategory = currentBill.getSubCategory();

                    // 根据是否有二级分类决定显示内容
                    String displayText;
                    String emojiToUse;

                    if (subCategory != null && !subCategory.trim().isEmpty()) {
                        // 有二级分类：显示 "二级分类emoji + 一级分类名称 - 二级分类名称"
                        emojiToUse = CategoryManager.getEmoji(subCategory);
                        displayText = category + " - " + subCategory;
                    } else {
                        // 无二级分类：显示 "一级分类emoji + 一级分类名称"
                        emojiToUse = CategoryManager.getEmoji(category);
                        displayText = category;
                    }

                    // 1. 获取图片路径
                    String iconName = getIconName(emojiToUse);

                    // 2. 使用 JavaFX 原生 Image 加载 (带缓存，性能极高)
                    try {
                        // 注意：路径必须保证正确，getResourceAsStream 是读取 jar/classes 内部资源的最佳方式
                        java.io.InputStream is = getClass().getResourceAsStream("/cn/bit/budget/icons/" + iconName);
                        if (is != null) {
                            imageView.setImage(new javafx.scene.image.Image(is));
                        } else {
                            // 如果找不到图片，可以在这里加载一个默认的“问号”图，或者留空
                            // System.out.println("找不到图标: " + iconName);
                            imageView.setImage(null);
                        }
                    } catch (Exception e) {
                        imageView.setImage(null);
                    }

                    // 3. 设置文字和图标的排版
                    setText(displayText);
                    setGraphic(imageView);
                    setContentDisplay(ContentDisplay.LEFT);
                    setGraphicTextGap(8);

                    // 4. 给文字加点样式
                    setStyle("-fx-text-fill: #606266; -fx-font-weight: bold; -fx-alignment: CENTER-LEFT;");
                }
            }

        });

        // 3. 开启表格多选
        billTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        setupContextMenu();

        // 4. 加载数据
        allBills = DataStore.loadBills();

        // 5. 默认显示
        onThisMonthClick(null);
    }

    // 辅助方法：把 Emoji 转换成文件名 (从之前的逻辑提取出来的)
    private String getIconName(String emoji) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < emoji.length(); ) {
            int codePoint = emoji.codePointAt(i);
            if (codePoint != 0xFE0F) {
                if (sb.length() > 0) sb.append("-");
                sb.append(Integer.toHexString(codePoint).toLowerCase());
            }
            i += Character.charCount(codePoint);
        }
        return sb.toString() + ".png";
    }

    /**
     * 设置按钮点击事件处理方法
     * 当用户点击设置按钮时，显示系统设置对话框
     *
     * @param event ActionEvent对象，包含事件相关信息
     */
    @FXML
    void onSettingsClick(ActionEvent event) {
        try {
            // 1. 加载布局
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/cn/bit/budget/budgetmanager/settings-view.fxml"));
            VBox settingsRoot = loader.load();

            // 获取控件引用
            JFXToggleButton autoModeToggle = (JFXToggleButton) settingsRoot.lookup("#autoModeToggle");
            JFXListView<HBox> listView = (JFXListView<HBox>) settingsRoot.lookup("#instructionListView");
            TextField inputField = (TextField) settingsRoot.lookup("#newInstructionField");
            Button btnAdd = (Button) settingsRoot.lookup("#btnAddInstruction");

            // 2. 初始化数据
            autoModeToggle.setSelected(this.isAutoCreateCategory);
            autoModeToggle.setOnAction(e -> this.isAutoCreateCategory = autoModeToggle.isSelected());

            // 加载已有的个性化信息到 ListView
            refreshInstructionList(listView);

            // 3. 绑定添加逻辑
            btnAdd.setOnAction(e -> {
                String text = inputField.getText();
                if (text != null && !text.trim().isEmpty()) {
                    CategoryManager.addPersonalization(text); // 后端持久化
                    inputField.clear();
                    refreshInstructionList(listView); // 刷新界面
                }
            });

            // 4. 弹出弹窗
            JFXDialogLayout layout = new JFXDialogLayout();
            layout.setHeading(new Label("⚙ 系统与 AI 设置"));
            layout.setBody(settingsRoot);

            JFXDialog dialog = new JFXDialog(rootStackPane, layout, JFXDialog.DialogTransition.CENTER);

            JFXButton btnClose = new JFXButton("完成");
            btnClose.setOnAction(e -> dialog.close());
            layout.setActions(btnClose);

            dialog.show();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 刷新指令列表，带删除按钮
     */
    private void refreshInstructionList(JFXListView<HBox> listView) {
        listView.getItems().clear();
        List<String> data = CategoryManager.getPersonalizations();

        for (String info : data) {
            HBox cell = new HBox();
            cell.setAlignment(Pos.CENTER_LEFT);
            cell.setSpacing(10);

            Label text = new Label(info);
            text.setMaxWidth(300);
            text.setWrapText(true);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            // 删除按钮 (小红叉)
            JFXButton btnDel = new JFXButton("✕");
            btnDel.setStyle("-fx-text-fill: #f56c6c; -fx-font-weight: bold; -fx-cursor: hand;");
            btnDel.setOnAction(e -> {
                CategoryManager.removePersonalization(info);
                refreshInstructionList(listView);
            });

            cell.getChildren().addAll(text, spacer, btnDel);

            // 如果是最后一个元素，手动去掉边框样式（通过加一个特定的 style class）
            if (data.indexOf(info) == data.size() - 1) {
                cell.setStyle("-fx-border-width: 0;");
            }
            listView.getItems().add(cell);
        }
    }
    /**
     * 核心方法：点击“查询/刷新”
     * 根据筛选条件过滤 allBills，并更新 UI
     */
    @FXML
    public void onSearchClick(ActionEvent event) {
        LocalDate start = startDatePicker.getValue();
        LocalDate end = endDatePicker.getValue();
        String category = filterCategoryBox.getValue();
        String subCategory = filterSubCategoryBox.getValue();
        String type = typeFilterBox.getValue(); // 获取类型

        // 使用 Stream API 进行多条件过滤
        List<Bill> filteredList = allBills.stream()
                // 1. 日期过滤
                .filter(b -> start == null || !b.getDate().isBefore(start))
                .filter(b -> end == null || !b.getDate().isAfter(end))
                // 2. 一级分类过滤
                .filter(b -> category == null || "全部分类".equals(category) || category.equals(b.getCategory()))
                // 3. 二级分类过滤
                .filter(b -> subCategory == null || "全部".equals(subCategory) || subCategory.equals(b.getSubCategory()))
                // 4. 收支类型过滤
                .filter(b -> type == null || "全部".equals(type) || type.equals(b.getType()))
                .collect(Collectors.toList());

        // 判断是否有二级分类筛选
        boolean hasSubCategoryFilter = subCategory != null && !"全部".equals(subCategory);

        if (hasSubCategoryFilter) {
            // 如果有二级分类筛选，只更新表格，不更新饼图
            updateTableOnly(filteredList);
        } else {
            // 否则更新表格和饼图
            updateTableAndChart(filteredList);
        }
    }

    /**
     * 快捷按钮：本月
     * 自动设置日期范围为本月第一天到最后一天，并触发查询
     */
    @FXML
    public void onThisMonthClick(ActionEvent event) {
        LocalDate today = LocalDate.now();

        // 设置为本月第1天
        startDatePicker.setValue(today.with(TemporalAdjusters.firstDayOfMonth()));

        // 设置为本月最后1天
        endDatePicker.setValue(today.with(TemporalAdjusters.lastDayOfMonth()));

        // 自动触发查询
        onSearchClick(null);
    }
    /**
     * 快捷按钮：本年
     * 自动设置日期范围为本月第一天到最后一天，并触发查询
     */
    public void onThisYearClick(ActionEvent event) {
        LocalDate today = LocalDate.now();
        // 设置为本年第1天
        startDatePicker.setValue(today.with(TemporalAdjusters.firstDayOfYear()));
        // 设置为本年最后1天
        endDatePicker.setValue(today.with(TemporalAdjusters.lastDayOfYear()));
        onSearchClick(null);
    }
    /**
     * 根据选中的收支类型更新分类筛选列表
     */
    private void updateCategoryFilterByType() {
        String currentSelection = filterCategoryBox.getValue();
        filterCategoryBox.getItems().clear();

        // 始终添加"全部分类"选项
        filterCategoryBox.getItems().add("全部分类");

        String selectedType = typeFilterBox.getValue();
        if ("收入".equals(selectedType)) {
            // 收入类型：只显示"收入"分类
            filterCategoryBox.getItems().addAll(CategoryManager.getIncomeCategories());
        } else if ("支出".equals(selectedType)) {
            // 支出类型：显示除"收入"外的所有分类
            filterCategoryBox.getItems().addAll(CategoryManager.getExpenseCategories());
        } else {
            // 全部类型：显示所有分类
            filterCategoryBox.getItems().addAll(CategoryManager.getParentCategories());
        }

        // 尝试保持之前的选择，如果不在新列表中则选择"全部分类"
        if (currentSelection != null && filterCategoryBox.getItems().contains(currentSelection)) {
            filterCategoryBox.setValue(currentSelection);
        } else {
            filterCategoryBox.setValue("全部分类");
        }
    }

    /**
     * 根据选中的一级分类更新二级分类筛选列表
     */
    private void updateSubCategoryFilter() {
        String currentSelection = filterSubCategoryBox.getValue();
        filterSubCategoryBox.getItems().clear();

        // 始终添加"全部"选项
        filterSubCategoryBox.getItems().add("全部");

        String selectedCategory = filterCategoryBox.getValue();
        if (selectedCategory != null && !"全部分类".equals(selectedCategory)) {
            // 获取该一级分类下的所有二级分类
            List<String> subCategories = CategoryManager.getChildCategories(selectedCategory);
            filterSubCategoryBox.getItems().addAll(subCategories);
        }

        // 尝试保持之前的选择，如果不在新列表中则选择"全部"
        if (currentSelection != null && filterSubCategoryBox.getItems().contains(currentSelection)) {
            filterSubCategoryBox.setValue(currentSelection);
        } else {
            filterSubCategoryBox.setValue("全部");
        }
    }

    /**
     * 主页面：添加自定义一级分类
     */
    @FXML
    public void onAddFilterCategory(ActionEvent event) {
        showInputDialog("新增一级分类", "请输入新的分类名称：", (name) -> {
            if (!name.trim().isEmpty()) {
                CategoryManager.addCustomParentCategory(name);
                if (!filterCategoryBox.getItems().contains(name)) {
                    filterCategoryBox.getItems().add(name);
                }
                filterCategoryBox.setValue(name);
                showTopRightSuccess(name, "已添加一级分类：" + name);
            }
        });
    }

    /**
     * 主页面：添加自定义二级分类
     */
    @FXML
    public void onAddFilterSubCategory(ActionEvent event) {
        String currentParent = filterCategoryBox.getValue();

        if (currentParent == null || "全部分类".equals(currentParent)) {
            showWarningAlert("提示", "请先选择一级分类");
            return;
        }

        showInputDialog("新增二级分类 (" + currentParent + ")", "请输入新的二级分类名称：", (name) -> {
            if (!name.trim().isEmpty()) {
                CategoryManager.addCustomChildCategory(currentParent, name);
                if (!filterSubCategoryBox.getItems().contains(name)) {
                    filterSubCategoryBox.getItems().add(name);
                }
                filterSubCategoryBox.setValue(name);
                showTopRightSuccess(name, "已添加二级分类：" + name);
            }
        });
    }

    /**
     * 删除自定义一级分类
     */
    @FXML
    public void onDeleteFilterCategory(ActionEvent event) {
        String selectedCategory = filterCategoryBox.getValue();

        if (selectedCategory == null || "全部分类".equals(selectedCategory)) {
            showTopRightError("请先选择要删除的分类");
            return;
        }

        // 检查是否为自定义分类
        if (!CategoryManager.isCustomCategory(selectedCategory)) {
            showTopRightError("默认分类不能删除，只能删除自定义分类");
            return;
        }

        // 显示删除确认对话框
        showDeleteCategoryConfirmDialog(selectedCategory);
    }

    /**
     * 删除自定义二级分类
     */
    @FXML
    public void onDeleteFilterSubCategory(ActionEvent event) {
        String currentParent = filterCategoryBox.getValue();
        String selectedSubCategory = filterSubCategoryBox.getValue();

        if (currentParent == null || "全部分类".equals(currentParent)) {
            showTopRightError("请先选择一级分类");
            return;
        }

        if (selectedSubCategory == null || "全部".equals(selectedSubCategory)) {
            showTopRightError("请先选择要删除的二级分类");
            return;
        }

        // 检查是否为自定义分类
        if (!CategoryManager.isCustomChildCategory(currentParent, selectedSubCategory)) {
            showTopRightError("默认二级分类不能删除，只能删除自定义分类");
            return;
        }

        // 显示删除确认对话框
        showDeleteSubCategoryConfirmDialog(currentParent, selectedSubCategory);
    }

    /**
     * 显示删除分类确认对话框
     */
    private void showDeleteCategoryConfirmDialog(String categoryName) {
        JFXDialogLayout content = new JFXDialogLayout();
        content.setHeading(new Text("确认删除"));

        // 构建提示信息
        String message = String.format(
                "确定要删除 \"%s\" 分类吗？\n\n删除该分类后，相应的账单条目也会一并删除哦！",
                categoryName
        );

        Text bodyText = new Text(message);
        bodyText.setStyle("-fx-font-size: 14px; -fx-fill: #606266;");
        content.setBody(bodyText);

        JFXDialog dialog = new JFXDialog(rootStackPane, content, JFXDialog.DialogTransition.CENTER);

        // 返回按钮
        JFXButton btnCancel = new JFXButton("返回");
        btnCancel.setStyle("-fx-text-fill: #909399; -fx-font-size: 14px;");
        btnCancel.setOnAction(e -> dialog.close());

        // 确认按钮
        JFXButton btnConfirm = new JFXButton("确认删除");
        btnConfirm.setStyle("-fx-text-fill: #f56c6c; -fx-font-weight: bold; -fx-font-size: 14px;");
        btnConfirm.setOnAction(e -> {
            dialog.close();
            performDeleteCategory(categoryName);
        });

        content.setActions(btnCancel, btnConfirm);
        dialog.show();
    }

    /**
     * 执行删除一级分类操作
     */
    private void performDeleteCategory(String categoryName) {
        // 删除分类
        boolean deleted = CategoryManager.deleteParentCategory(categoryName);

        if (deleted) {
            // 删除相关账单
            int deletedBillCount = DataStore.deleteBillsByCategory(categoryName);

            // 从下拉框中移除
            filterCategoryBox.getItems().remove(categoryName);
            filterCategoryBox.setValue("全部分类");

            // 重新加载数据
            allBills = DataStore.loadBills();
            onSearchClick(null);

            // 显示成功提示
            String successMsg = String.format(
                    "已删除分类 \"%s\"，同时删除了 %d 条相关账单",
                    categoryName, deletedBillCount
            );
            showGeneralSuccess(successMsg);
        } else {
            showWarningAlert("删除失败", "无法删除该分类");
        }
    }

    /**
     * 显示删除二级分类确认对话框
     */
    private void showDeleteSubCategoryConfirmDialog(String parentCategory, String subCategory) {
        JFXDialogLayout content = new JFXDialogLayout();
        content.setHeading(new Text("确认删除"));

        String message = String.format(
                "确定要删除 \"%s - %s\" 分类吗？\n\n删除该分类后，相应的账单条目也会一并删除哦！",
                parentCategory, subCategory
        );

        Text bodyText = new Text(message);
        bodyText.setStyle("-fx-font-size: 14px; -fx-fill: #606266;");
        content.setBody(bodyText);

        JFXDialog dialog = new JFXDialog(rootStackPane, content, JFXDialog.DialogTransition.CENTER);

        // 返回按钮
        JFXButton btnCancel = new JFXButton("返回");
        btnCancel.setStyle("-fx-text-fill: #909399; -fx-font-size: 14px;");
        btnCancel.setOnAction(e -> dialog.close());

        // 确认按钮
        JFXButton btnConfirm = new JFXButton("确认删除");
        btnConfirm.setStyle("-fx-text-fill: #f56c6c; -fx-font-weight: bold; -fx-font-size: 14px;");
        btnConfirm.setOnAction(e -> {
            dialog.close();
            performDeleteSubCategory(parentCategory, subCategory);
        });

        content.setActions(btnCancel, btnConfirm);
        dialog.show();
    }

    /**
     * 执行删除二级分类操作
     */
    private void performDeleteSubCategory(String parentCategory, String subCategory) {
        // 删除分类
        boolean deleted = CategoryManager.deleteChildCategory(parentCategory, subCategory);

        if (deleted) {
            // 删除相关账单
            int deletedBillCount = DataStore.deleteBillsBySubCategory(parentCategory, subCategory);

            // 从下拉框中移除
            filterSubCategoryBox.getItems().remove(subCategory);
            filterSubCategoryBox.setValue("全部");

            // 重新加载数据
            allBills = DataStore.loadBills();
            onSearchClick(null);

            // 显示成功提示
            String successMsg = String.format(
                    "已删除分类 \"%s - %s\"，同时删除了 %d 条相关账单",
                    parentCategory, subCategory, deletedBillCount
            );
            showGeneralSuccess(successMsg);
        } else {
            showWarningAlert("删除失败", "无法删除该分类");
        }
    }

    /**
     * 通用：显示现代化输入弹窗
     */
    private void showInputDialog(String title, String prompt, Consumer<String> onConfirm) {
        // 1. 创建布局容器
        JFXDialogLayout content = new JFXDialogLayout();
        content.setHeading(new Label(title));

        // 2. 创建输入框
        TextField inputField = new TextField();
        inputField.setPromptText(prompt);
        inputField.getStyleClass().add("material-field"); // 应用CSS
        inputField.setPrefWidth(300);

        VBox body = new VBox(inputField);
        content.setBody(body);

        // 3. 创建弹窗对象 (rootStackPane 是你在 HelloController 注入的 StackPane)
        JFXDialog dialog = new JFXDialog(rootStackPane, content, JFXDialog.DialogTransition.CENTER);

        // 4. 按钮
        JFXButton btnCancel = new JFXButton("取消");
        btnCancel.setStyle("-fx-text-fill: #909399; -fx-font-size: 14px;");
        btnCancel.setOnAction(e -> dialog.close());

        JFXButton btnConfirm = new JFXButton("确定");
        btnConfirm.setStyle("-fx-text-fill: #409eff; -fx-font-weight: bold; -fx-font-size: 14px;");
        btnConfirm.setOnAction(e -> {
            onConfirm.accept(inputField.getText());
            dialog.close();
        });

        content.setActions(btnCancel, btnConfirm);
        dialog.show();
    }

    /**
     * 只更新表格，不更新饼图（用于二级分类筛选）
     *
     * @param targetList 经过筛选后的账单列表
     */
    private void updateTableOnly(List<Bill> targetList) {
        billTable.setItems(FXCollections.observableArrayList(targetList));
    }

    /**
     * 核心方法：同时更新表格和统计图
     *
     * @param targetList 经过筛选后的账单列表
     */
    private void updateTableAndChart(List<Bill> targetList) {
        // ===========================
        // 1. 更新表格 (Table View)
        // ===========================
        billTable.setItems(FXCollections.observableArrayList(targetList));

        // ===========================
        // 2. 更新饼图 (Pie Chart)
        // ===========================

        // 步骤 A: 确定我们要统计“一级分类”还是“二级分类”
        // 逻辑：如果下拉框选的是"全部分类"（或没选），我们就按一级分类统计。
        //      如果用户已经选了"餐饮"，那饼图就应该显示"三餐"、"奶茶"等二级细分。
        String currentFilterCat = filterCategoryBox.getValue();
        boolean isViewingSubCategories = currentFilterCat != null && !"全部分类".equals(currentFilterCat);

        // 步骤 B: 使用 Stream API 进行分组统计
        Map<String, Double> statsMap = targetList.stream()
                // 过滤逻辑：
                // 如果用户在类型筛选里专门选了“收入”，我们就统计收入。
                // 否则默认只统计“支出”，因为把收入和支出画在一个饼图里很奇怪。
                .filter(b -> {
                    String selectedType = typeFilterBox.getValue(); // 获取当前的收支筛选状态
                    if ("收入".equals(selectedType)) {
                        return "收入".equals(b.getType());
                    }
                    return "支出".equals(b.getType());
                })
                .collect(Collectors.groupingBy(
                        bill -> {
                            // 【核心智能逻辑】
                            if (isViewingSubCategories) {
                                // 如果正在看特定分类，按二级分类分组 (防止空指针，如果没有二级则归为"其他")
                                return bill.getSubCategory() == null ? "其他" : bill.getSubCategory();
                            } else {
                                // 否则按一级分类分组
                                return bill.getCategory();
                            }
                        },
                        // 求和：注意要用 Math.abs 取绝对值，防止支出是负数导致饼图画不出来
                        Collectors.summingDouble(b -> Math.abs(b.getAmount()))
                ));

        // 步骤 C: 转换为 PieChart.Data 并添加 Emoji
        ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();

        statsMap.forEach((categoryName, totalAmount) -> {
            if (totalAmount > 0) {
                String label;

                if (isViewingSubCategories) {
                    // 如果是二级分类，显示emoji
                    String emoji;
                    if ("".equals(categoryName)) {
                        // 如果是无二级分类的条目，使用当前一级分类的emoji
                        emoji = CategoryManager.getEmoji(currentFilterCat);
                        label = emoji + " " + "其他";
                    } else {
                        // 否则使用二级分类自己的emoji
                        emoji = CategoryManager.getEmoji(categoryName);
                        label = emoji + " " + categoryName;
                    }
                } else {
                    // 如果是一级分类，加上 Emoji 前缀
                    String emoji = CategoryManager.getEmoji(categoryName);
                    label = emoji + " " + categoryName;
                }

                // 创建饼图数据
                PieChart.Data data = new PieChart.Data(label, totalAmount);
                pieData.add(data);

                // 保存分类信息，用于tooltip
                final String categoryForTooltip = categoryName;

                // 在数据添加到图表后，为饼图扇区添加tooltip
                javafx.application.Platform.runLater(() -> {
                    if (data.getNode() != null) {
                        // 为饼图扇区添加tooltip，显示详细信息
                        javafx.scene.control.Tooltip tooltip = new javafx.scene.control.Tooltip(
                                String.format("%s\n金额: ¥%.2f\n占比: %.1f%%",
                                        categoryForTooltip,
                                        totalAmount,
                                        (totalAmount / statsMap.values().stream().mapToDouble(Double::doubleValue).sum()) * 100)
                        );
                        javafx.scene.control.Tooltip.install(data.getNode(), tooltip);
                    }
                });
            }
        });

        // 步骤 D: 只有当数据发生变化时才重置数据，防止闪烁
        expensePieChart.setData(pieData);


        // 步骤 E: 设置饼图标题动态变化（根据收支类型和分类）
        String selectedType = typeFilterBox.getValue();
        String typeLabel = "收入".equals(selectedType) ? "收入" : "支出";

        if (isViewingSubCategories) {
            expensePieChart.setTitle(currentFilterCat + " - " + typeLabel + "明细");
        } else {
            expensePieChart.setTitle("总" + typeLabel + "构成");
        }
    }

    // ================== 原有功能的适配修改 ==================

    @FXML
    public void onAddClick(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(HelloController.class.getResource("/cn/bit/budget/budgetmanager/add-bill-view.fxml"));
            Parent root = loader.load();
            AddBillController addController = loader.getController();

            Stage stage = new Stage();
            stage.setTitle("记一笔");
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.showAndWait();

            Bill newBill = addController.getBill();
            if (newBill != null) {
                // 1. 加到总数据源
                allBills.add(newBill);

                // 【新增】重新排序：日期倒序 -> 创建时间倒序
                allBills.sort((b1, b2) -> {
                    if (b2.getDate().equals(b1.getDate())) {
                        return b2.getCreateTime().compareTo(b1.getCreateTime());
                    }
                    return b2.getDate().compareTo(b1.getDate());
                });
                // 2. 保存全量数据
                DataStore.saveBills(allBills);
                // 3. 刷新视图 (新数据如果符合当前筛选条件，会立即显示在表格和图中)
                onSearchClick(null);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    public void onDeleteClick(ActionEvent event) {
        ObservableList<Bill> selectedItems = billTable.getSelectionModel().getSelectedItems();
        if (selectedItems.isEmpty()) return;

        // 使用JFoenix风格的确认对话框
        showDeleteConfirmDialog(selectedItems);
    }

    /**
     * 显示删除账单确认对话框（JFoenix风格）
     */
    private void showDeleteConfirmDialog(ObservableList<Bill> selectedItems) {
        JFXDialogLayout content = new JFXDialogLayout();
        content.setHeading(new Text("确认删除"));

        // 在显示对话框时保存选中项目数量
        int selectedCount = selectedItems.size();
        String message = String.format("确定要删除选中的 %d 条记录吗？", selectedCount);
        Text bodyText = new Text(message);
        bodyText.setStyle("-fx-font-size: 14px; -fx-fill: #606266;");
        content.setBody(bodyText);

        JFXDialog dialog = new JFXDialog(rootStackPane, content, JFXDialog.DialogTransition.CENTER);

        // 取消按钮
        JFXButton btnCancel = new JFXButton("取消");
        btnCancel.setStyle("-fx-text-fill: #909399; -fx-font-size: 14px;");
        btnCancel.setOnAction(e -> dialog.close());

        // 确认按钮
        JFXButton btnConfirm = new JFXButton("确定");
        btnConfirm.setStyle("-fx-text-fill: #f56c6c; -fx-font-weight: bold; -fx-font-size: 14px;");
        btnConfirm.setOnAction(e -> {
            dialog.close();
            performDeleteBills(selectedItems, selectedCount); // 传递数量
        });

        content.setActions(btnCancel, btnConfirm);
        dialog.show();
    }

    /**
     * 执行删除账单操作
     */
    private void performDeleteBills(ObservableList<Bill> selectedItems, int selectedCount) {
        // 1. 从总数据源中移除
        allBills.removeAll(selectedItems);

        // 2. 保存全量数据
        DataStore.saveBills(allBills);

        // 3. 刷新视图
        onSearchClick(null);

        // 4. 清除选择
        billTable.getSelectionModel().clearSelection();

        // 5. 显示成功提示 (使用传入的数量)
        showGeneralSuccess(String.format("已删除 %d 条账单记录", selectedCount));
    }

    // --------- 导入逻辑 ----------
    /**
     * 导入账单
     */
    @FXML
    public void onImportClick(ActionEvent event) {
        // 1. 完整的文件选择器（找回了你担心的多格式支持！）
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("选择微信/支付宝账单文件");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("账单文件 (CSV, Excel)", "*.csv", "*.xlsx", "*.xls")
        );

        File file = fileChooser.showOpenDialog(billTable.getScene().getWindow());
        if (file == null) return;

        // 2. 解析文件
        List<Bill> rawBills = BillImportUtil.parse(file);
        if (rawBills.isEmpty()) return;

        // 3. 启动“分区呈现”的 Agent 审查流程
        showAgentReviewFlow(rawBills);
    }

    /**
     * 分区呈现进度并处理 Agent 逻辑
     */
    private void showAgentReviewFlow(List<Bill> rawBills) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/cn/bit/budget/budgetmanager/import-review-view.fxml"));
            VBox reviewRoot = loader.load();

            // 提取 UI 引用
            javafx.scene.control.ProgressBar progressBar = (javafx.scene.control.ProgressBar) reviewRoot.lookup("#importProgressBar");
            Label statusLabel = (Label) reviewRoot.lookup("#statusLabel");
            Label progressText = (Label) reviewRoot.lookup("#progressText");
            TableView<ReviewItem> table = (TableView<ReviewItem>) reviewRoot.lookup("#reviewTable");

            // 配置表格列 (包括 ComboBox 修正逻辑)
            setupReviewTableColumns(table);

            // 4. 创建弹窗
            JFXDialogLayout layout = new JFXDialogLayout();
            layout.setHeading(new Label("🤖 智能导入审查工作流"));
            layout.setBody(reviewRoot);

            // 强制设定布局尺寸，确保16:9
            layout.setPrefSize(960, 540);
            // 防止 VBox 缩水
            reviewRoot.setMinWidth(900);
            // 增大窗口
            reviewRoot.setPrefSize(960, 540); // 16:9 的 960x540
            JFXDialog dialog = new JFXDialog(rootStackPane, layout, JFXDialog.DialogTransition.CENTER);
            dialog.setOverlayClose(false);

            JFXButton btnFinish = new JFXButton("完成导入");
            btnFinish.setDisable(true); // 分析完之前不能点
            btnFinish.setStyle("-fx-background-color: #409eff; -fx-text-fill: white;");
            layout.setActions(btnFinish);
            dialog.show();

            // 5. 分批次执行 AI 分析 (实现进度条平滑移动)
            runBatchCategorization(rawBills, table, progressBar, progressText, statusLabel, btnFinish);

            // 6. 保存逻辑
            btnFinish.setOnAction(e -> {
                handleFinalImport(rawBills, table.getItems());
                dialog.close();
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 核心分批逻辑 (V3.0 - 唯一ID绑定版)
     * 解决了重复键报错 (如美团收支并存) 和漏网之鱼问题
     */
    private void runBatchCategorization(List<Bill> rawBills, TableView<ReviewItem> table,
                                        ProgressBar pb, Label pText, Label sLabel, Button btn) {

        // 1. 全量分组：将所有账单按 [安全描述 + 收支类型] 进行物理捆绑
        // 这样“美团|支出”和“美团|收入”会成为两个独立的组，拥有唯一的 UniqueKey
        Map<String, List<Bill>> groupedBills = rawBills.stream()
                .collect(Collectors.groupingBy(b -> getSafeDesc(b.getRemark()) + "|" + b.getType()));

        List<String> allUniqueKeys = new ArrayList<>(groupedBills.keySet());
        int totalItems = allUniqueKeys.size();

        ObservableList<ReviewItem> reviewData = FXCollections.observableArrayList();
        table.setItems(reviewData);

        // 2. 链式异步调用
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        int batchSize = 5;

        for (int i = 0; i < totalItems; i += batchSize) {
            final int start = i;
            final int end = Math.min(i + batchSize, totalItems);
            List<String> batchKeys = allUniqueKeys.subList(start, end);

            chain = chain.thenCompose(v -> {
                // 构造 AI 格式的输入，明确告知唯一 ID
                List<Map<String, Object>> batchItems = new ArrayList<>();
                for (String key : batchKeys) {
                    // 取该组第一个账单作为代表发送给 AI
                    Bill sample = groupedBills.get(key).get(0);
                    Map<String, Object> aiItem = prepareBillForAi(sample);
                    aiItem.put("unique_id", key); // 🔥 注入唯一 ID，防止 JSON 重复键报错
                    batchItems.add(aiItem);
                }

                return AICategorizer.categorizeAsync(batchItems,
                                CategoryManager.getExpenseCategoryTree(),
                                CategoryManager.getIncomeCategoryTree(),
                                CategoryManager.getPersonalizations())
                        .thenAccept(results -> javafx.application.Platform.runLater(() -> {
                            // 3. 根据 AI 返回的 UniqueKey 精准还原到 ReviewTable
                            results.forEach((uniqueId, res) -> {
                                if (groupedBills.containsKey(uniqueId)) {
                                    // 每一组经过审计的分类，都会被应用到 groupedBills.get(uniqueId) 里的所有账单
                                    Bill sample = groupedBills.get(uniqueId).get(0);
                                    reviewData.add(new ReviewItem(sample, res, uniqueId)); // 需确保 ReviewItem 构造函数支持 uniqueId
                                }
                            });

                            // 更新进度条
                            double p = (double) end / totalItems;
                            pb.setProgress(p);
                            pText.setText(end + " / " + totalItems);
                        }));
            });
        }

        chain.thenRun(() -> javafx.application.Platform.runLater(() -> {
            sLabel.setText("✅ 分析完成，请核对并修正结果");
            btn.setDisable(false);
        })).exceptionally(ex -> {
            javafx.application.Platform.runLater(() -> showTopRightError("AI 分析中断：" + ex.getMessage()));
            return null;
        });
    }

    /**
     * 配置表格列 (包括 ComboBox 修正逻辑)
     * @param table
     */
    private void setupReviewTableColumns(TableView<ReviewItem> table) {
        // 1. 描述列
        TableColumn<ReviewItem, String> colDesc = new TableColumn<>("交易描述");
        colDesc.setCellValueFactory(new PropertyValueFactory<>("originalDesc"));
        colDesc.setPrefWidth(240);

        // 2. 一级分类列 (ComboBox)
        TableColumn<ReviewItem, String> colParent = new TableColumn<>("一级分类");
        colParent.setPrefWidth(180);
        colParent.setCellValueFactory(d -> d.getValue().parentCategoryProperty());
        colParent.setCellFactory(column -> new TableCell<>() {
            private final ComboBox<String> combo = new ComboBox<>();
            {
                combo.setMaxWidth(Double.MAX_VALUE);
                combo.setOnAction(e -> {
                    if (getItem() != null && getTableRow().getItem() != null) {
                        getTableRow().getItem().parentCategoryProperty().set(combo.getValue());
                        getTableRow().getItem().subCategoryProperty().set("无"); // 切换一级时重置二级
                    }
                });
            }
            // 针对 colParent 的 ComboBox 修复
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    ReviewItem row = getTableRow().getItem();
                    List<String> options = new ArrayList<>();
                    if ("收入".equals(row.getBillType())) options.addAll(CategoryManager.getIncomeCategories());
                    else options.addAll(CategoryManager.getExpenseCategories());

                    // 🔥 核心：必须把当前的 item (AI建议) 强行塞进 options，否则框里会不显示文字
                    if (item != null && !options.contains(item)) {
                        options.add(0, item);
                    }

                    combo.setItems(FXCollections.observableArrayList(options));
                    combo.setValue(item); // 这时它肯定能找到了
                    setGraphic(combo);
                }
            }
        });

        // 3. 二级分类列 (联动 ComboBox)
        TableColumn<ReviewItem, String> colSub = new TableColumn<>("二级分类");
        colSub.setPrefWidth(180);
        colSub.setCellValueFactory(d -> d.getValue().subCategoryProperty());
        colSub.setCellFactory(column -> new TableCell<>() {
            private final ComboBox<String> subCombo = new ComboBox<>();

            {
                subCombo.setMaxWidth(Double.MAX_VALUE);
                subCombo.setOnAction(e -> {
                    if (getItem() != null && getTableRow().getItem() != null) {
                        getTableRow().getItem().subCategoryProperty().set(subCombo.getValue());
                    }
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    ReviewItem row = getTableRow().getItem();

                    // 🔥 核心改进：监听一级分类的变化
                    row.parentCategoryProperty().addListener((obs, oldVal, newVal) -> {
                        updateSubOptions(newVal);
                    });

                    // 初始化当前列表
                    updateSubOptions(row.parentCategoryProperty().get());
                    subCombo.setValue(item);
                    setGraphic(subCombo);
                }
            }

            // 辅助方法：刷新下拉选项
            private void updateSubOptions(String parent) {
                List<String> options = new ArrayList<>();
                options.add("无");
                if (parent != null) {
                    options.addAll(CategoryManager.getChildCategories(parent));
                }
                subCombo.setItems(FXCollections.observableArrayList(options));
            }
        });

        // 4. 状态/审批列 (CheckBox)
        TableColumn<ReviewItem, Boolean> colStatus = new TableColumn<>("批准创建");
        colStatus.setCellValueFactory(cellData -> cellData.getValue().approvedProperty());
        colStatus.setPrefWidth(120);

        colStatus.setCellFactory(column -> new TableCell<>() {
            private final CheckBox checkBox = new CheckBox("批准新分类");
            @Override
            protected void updateItem(Boolean approved, boolean empty) {
                super.updateItem(approved, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    ReviewItem rowData = getTableRow().getItem();
                    // 只有 AI 建议的是新分类，才显示勾选框
                    if (rowData.isNewProperty().get()) {
                        checkBox.setSelected(approved);
                        checkBox.setOnAction(e -> rowData.approvedProperty().set(checkBox.isSelected()));
                        setGraphic(checkBox);
                    } else {
                        setGraphic(new Label("✅ 已匹配现有类"));
                    }
                }
            }
        });


        table.getColumns().setAll(colDesc, colParent, colSub, colStatus);
    }
    /**
     * 处理最终的账单导入
     */
    private void handleFinalImport(List<Bill> rawBills, List<ReviewItem> items) {
        for (ReviewItem item : items) {
            String finalParent;
            String finalSub = "无".equals(item.subCategoryProperty().get()) ? null : item.subCategoryProperty().get();

            // 核心修复：检查当前值是否是已存在的分类
            boolean isExisting = CategoryManager.getParentCategories().contains(item.parentCategoryProperty().get());

            if (item.isNewProperty().get()) {
                if (item.approvedProperty().get()) {
                    // 情况 A：批准创建 -> 注册并应用
                    finalParent = item.parentCategoryProperty().get();
                    CategoryManager.addCustomParentCategory(finalParent);
                } else if (isExisting) {
                    // 情况 B：没准新建，但用户改选了已有的 -> 尊重用户，应用已有的
                    finalParent = item.parentCategoryProperty().get();
                } else {
                    // 情况 C：没准新建，也没选现成的 -> 强制打回原形！
                    finalParent = item.getFallback();
                    finalSub = null;
                }
            } else {
                // 已有分类，直接用
                finalParent = item.parentCategoryProperty().get();
            }

            // 精准同步：必须匹配 [描述] 和 [收支类型]
            for (Bill b : rawBills) {
                String billUniqueId = getSafeDesc(b.getRemark()) + "|" + b.getType();
                if (billUniqueId.equals(item.getUniqueId())) { // ReviewItem 里要存这个 uniqueId
                    b.setCategory(finalParent);
                    b.setSubCategory(finalSub);
                }
            }
        }
        // 保存入库并刷新主界面
        allBills.addAll(rawBills);
        DataStore.saveBills(allBills);
        onSearchClick(null);
        updateCategoryFilterByType(); // 刷新主界面左侧的筛选下拉框
        showGeneralSuccess("成功导入并分类 " + rawBills.size() + " 条账单！");
    }
    /**
     * 最终应用分类到账单
     * @param approvedNewCategories 用户(或自动模式)批准创建的新分类列表
     */
    private void applyCategories(List<Bill> rawBills, Map<String, AICategorizer.CategoryResult> resultMap, java.util.Set<String> approvedNewCategories) {
        int count = 0;
        for (Bill bill : rawBills) {
            String key = bill.getRemark().split("-")[0];
            AICategorizer.CategoryResult res = resultMap.get(key);

            if (res != null) {
                String finalCategory;

                if (res.isNew) {
                    // 如果是新分类，检查是否被批准
                    if (approvedNewCategories != null && approvedNewCategories.contains(res.suggestion)) {
                        finalCategory = res.suggestion; // ✅ 批准：使用新分类
                    } else {
                        finalCategory = res.fallback;   // ❌ 拒绝：使用兜底分类 (Plan B)
                    }
                } else {
                    finalCategory = res.suggestion; // 原有分类，直接用
                }

                // 再次校验合法性 (防止 fallback 也是瞎编的)
                if (CategoryManager.getParentCategories().contains(finalCategory)) {
                    bill.setCategory(finalCategory);
                    bill.setSubCategory(null);
                    count++;
                } else {
                    bill.setCategory("其他"); // 最后的最后，真正的兜底
                }
            }
        }

        // 保存并刷新
        allBills.addAll(rawBills);
        DataStore.saveBills(allBills);
        onSearchClick(null);
        updateCategoryFilterByType();

        if (approvedNewCategories == null || approvedNewCategories.isEmpty()) {
            showGeneralSuccess("导入完成 (已使用现有分类归档 " + count + " 条)");
        }
    }

    /**
     * 模拟 Agent 交互对话框 (用户决策)
     */
    private void showAgentInteractionDialog(Map<String, String> proposals, List<Bill> rawBills, Map<String, AICategorizer.CategoryResult> resultMap) {
        JFXDialogLayout content = new JFXDialogLayout();
        content.setHeading(new Label("🤖 待确认的分类建议"));

        // 构建提示信息：左边是建议(Plan A)，右边是如果不选的后果(Plan B)
        StringBuilder sb = new StringBuilder("AI 发现部分账单不属于现有分类，建议方案如下：\n\n");

        for (Map.Entry<String, String> entry : proposals.entrySet()) {
            sb.append(String.format("• 🆕 %s  (若拒绝则归入: %s)\n", entry.getKey(), entry.getValue()));
        }

        sb.append("\n是否批准创建这些新分类？");

        Label bodyText = new Label(sb.toString());
        bodyText.setStyle("-fx-font-size: 14px; -fx-text-fill: #606266;");
        content.setBody(bodyText);

        JFXDialog dialog = new JFXDialog(rootStackPane, content, JFXDialog.DialogTransition.CENTER);
        dialog.setOverlayClose(false); // 必须做决定

        // 按钮 A: 拒绝 (Use Fallback)
        JFXButton btnReject = new JFXButton("拒绝 (使用已有分类)");
        btnReject.setStyle("-fx-text-fill: #909399;");
        btnReject.setOnAction(e -> {
            dialog.close();
            // 传一个空的 Set，表示一个都没批准 -> 全部走 Fallback 逻辑
            applyCategories(rawBills, resultMap, new java.util.HashSet<>());
            showGeneralSuccess("已拒绝新分类，将使用相近分类归档。");
        });

        // 按钮 B: 批准 (Create New)
        JFXButton btnConfirm = new JFXButton("批准创建");
        btnConfirm.setStyle("-fx-text-fill: #409eff; -fx-font-weight: bold;");
        btnConfirm.setOnAction(e -> {
            dialog.close();
            // 真正的创建逻辑在这里
            for (String newCat : proposals.keySet()) {
                CategoryManager.addCustomParentCategory(newCat);
            }
            // 传入所有新分类 -> 全部走 New Logic
            applyCategories(rawBills, resultMap, proposals.keySet());
            showTopRightSuccess("Agent", "已成功创建并应用新分类");
        });

        content.setActions(btnReject, btnConfirm);
        dialog.show();
    }
    /**
     * 辅助方法：应用 AI 分类结果并保存
     */
    private void applyAiCategoriesAndSave(List<Bill> rawBills, Map<String, String> categoryMap) {
        int autoCategorizedCount = 0;

        for (Bill bill : rawBills) {
            String key = bill.getRemark().split("-")[0];
            String aiCat = categoryMap.get(key);

            // 如果 AI 返回的分类系统里有 (可能是刚创建的，也可能是原有的)
            if (aiCat != null && CategoryManager.getParentCategories().contains(aiCat)) {
                bill.setCategory(aiCat);
                bill.setSubCategory(null); // 清空二级
                autoCategorizedCount++;
            } else {
                // 兜底策略：如果用户拒绝了创建新分类，或者 AI 返回了乱码
                // 暂时归为 "其他" (你需要确保 CategoryManager 里有"其他"这个分类，或者保留原值)
                bill.setCategory("其他");
            }
        }

        allBills.addAll(rawBills);
        DataStore.saveBills(allBills);
        onSearchClick(null);

        // 更新左侧筛选栏 (因为可能有新分类)
        updateCategoryFilterByType();

        showGeneralSuccess("导入成功！AI 自动归类了 " + autoCategorizedCount + " 条账单");
    }

    @FXML
    void onHelpClick(ActionEvent event) {
        // 1. 定义弹窗内容
        JFXDialogLayout content = new JFXDialogLayout();
        content.setHeading(new Text("如何获取微信账单？")); // 标题
        content.setBody(new Text("1. 打开手机微信 -> 我 -> 服务 -> 钱包\n2. 点击右上角 [账单] -> 常见问题 -> 下载账单\n3. 选择 [用于个人对账]，导出时间范围\n4. 选择发送到微信或发送到指定邮箱\n5. 通过微信或邮箱接收到账单 csv/xlsx 文件即可导入本软件。")); // 正文

        // 2. 创建弹窗对象
        JFXDialog dialog = new JFXDialog(rootStackPane, content, JFXDialog.DialogTransition.CENTER);

        // 3. 定义关闭按钮
        JFXButton closeButton = new JFXButton("我知道了");
        closeButton.setOnAction(e -> dialog.close());
        // 给按钮加个样式
        closeButton.setStyle("-fx-text-fill: #409eff; -fx-font-weight: bold;");

        content.setActions(closeButton);

        // 4. 显示
        dialog.show();
    }

    // ---------------------- 辅助方法 ----------------------

    private void setupContextMenu() {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem selectMonthItem = new MenuItem("选中本月至此的所有账单");
        selectMonthItem.setOnAction(e -> handleSelectCurrentMonthUpToHere());
        MenuItem selectAllItem = new MenuItem("全选");
        selectAllItem.setOnAction(e -> billTable.getSelectionModel().selectAll());
        contextMenu.getItems().addAll(selectMonthItem, new SeparatorMenuItem(), selectAllItem);
        billTable.setContextMenu(contextMenu);
    }

    private void handleSelectCurrentMonthUpToHere() {
        Bill selectedItem = billTable.getSelectionModel().getSelectedItem();
        if (selectedItem == null) return;
        LocalDate targetDate = selectedItem.getDate();
        int targetMonth = targetDate.getMonthValue();
        int targetYear = targetDate.getYear();

        billTable.getSelectionModel().clearSelection();
        // 注意：这里是在当前显示的表格(items)中遍历，还是在 allBills 中遍历？
        // 应该在表格显示的项中遍历才符合直觉
        for (Bill bill : billTable.getItems()) {
            LocalDate d = bill.getDate();
            if (d.getYear() == targetYear && d.getMonthValue() == targetMonth) {
                if (!d.isAfter(targetDate)) {
                    billTable.getSelectionModel().select(bill);
                }
            }
        }
    }


    private void showWarningAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    /**
     * 显示JFoenix风格的警告提示框
     */
    private void showJFoenixWarning(String title, String content) {
        JFXDialogLayout dialogContent = new JFXDialogLayout();
        dialogContent.setHeading(new Text(title));

        Text bodyText = new Text(content);
        bodyText.setStyle("-fx-font-size: 14px; -fx-fill: #606266;");
        dialogContent.setBody(bodyText);

        JFXDialog dialog = new JFXDialog(rootStackPane, dialogContent, JFXDialog.DialogTransition.CENTER);

        // 确定按钮
        JFXButton btnOk = new JFXButton("确定");
        btnOk.setStyle("-fx-text-fill: #409eff; -fx-font-weight: bold; -fx-font-size: 14px;");
        btnOk.setOnAction(e -> dialog.close());

        dialogContent.setActions(btnOk);
        dialog.show();
    }

// ==========================================
    //       ✨ 通用右上角胶囊弹窗逻辑 ✨
    // ==========================================

    /**
     * 场景 A：添加分类成功（自动根据分类名找图标）
     */
    private void showTopRightSuccess(String categoryName, String message) {
        String emoji = CategoryManager.getEmoji(categoryName);
        // 调用通用方法
        showUniversalToast(emoji, message, false);
    }

    /**
     * 场景 B：通用操作成功（如导入成功，手动指定一个图标，这里用 🎉）
     */
    private void showGeneralSuccess(String message) {
        // \uD83C\uDF89 是 🎉 的 Unicode，确保你的 icons 文件夹里有 1f389.png
        // 如果没有这个图，代码里的 try-catch 会自动处理，只显示文字
        showUniversalToast("\uD83C\uDF89", message, false);
    }

    /**
     * 场景 C：显示错误提示（使用 ❌ 图标）
     */
    private void showTopRightError(String message) {
        // \u274C 是 ❌ 的 Unicode
        showUniversalToast("\u274C", message, true);
    }

    /**
     * 核心私有方法：构建并显示弹窗
     *
     * @param emojiStr Emoji 字符 (用于查找文件名)
     * @param message  提示文字
     * @param isError  是否为错误提示（true=红色边框，false=黄色边框）
     */
    private void showUniversalToast(String emojiStr, String message, boolean isError) {
        // 1. 创建容器 HBox
        javafx.scene.layout.HBox toast = new javafx.scene.layout.HBox();
        // 根据类型选择样式类
        toast.getStyleClass().add(isError ? "top-right-error-toast" : "top-right-toast");

        // 🔥🔥🔥 核心修复：禁止 StackPane 拉伸这个 HBox 🔥🔥🔥
        // USE_PREF_SIZE 告诉父容器：我多大就是多大，别把老子拉宽！
        toast.setMaxSize(javafx.scene.layout.Region.USE_PREF_SIZE, javafx.scene.layout.Region.USE_PREF_SIZE);

        // 2. 创建图片 ImageView
        javafx.scene.image.ImageView iconView = new javafx.scene.image.ImageView();
        iconView.setFitWidth(24);
        iconView.setFitHeight(24);

        try {
            String iconFile = getIconName(emojiStr); // 使用现有的转换方法
            java.io.InputStream is = getClass().getResourceAsStream("/cn/bit/budget/icons/" + iconFile);
            if (is != null) {
                iconView.setImage(new javafx.scene.image.Image(is));
                toast.getChildren().add(iconView); // 只有找到图片才添加
            }
        } catch (Exception e) {
            // 图片加载失败不做处理，直接显示纯文字
        }

        // 3. 创建文字 Label
        Label msgLabel = new Label(message);
        msgLabel.setStyle("-fx-text-fill: #303133; -fx-font-weight: bold; -fx-font-size: 14px;");
        toast.getChildren().add(msgLabel);

        // 4. 定位到右上角
        rootStackPane.getChildren().add(toast);
        StackPane.setAlignment(toast, javafx.geometry.Pos.TOP_RIGHT);
        StackPane.setMargin(toast, new javafx.geometry.Insets(20, 20, 0, 0));

        // 5. 动画效果
        javafx.animation.FadeTransition fadeIn = new javafx.animation.FadeTransition(Duration.millis(300), toast);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);

        javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(Duration.millis(2500));

        javafx.animation.FadeTransition fadeOut = new javafx.animation.FadeTransition(Duration.millis(500), toast);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);

        fadeOut.setOnFinished(e -> rootStackPane.getChildren().remove(toast));

        javafx.animation.SequentialTransition seq = new javafx.animation.SequentialTransition(fadeIn, pause, fadeOut);
        seq.play();
    }

    /**
     * 显示加载中弹窗 (禁止点击外部关闭)
     * @param message 提示文字，如 "AI 正在思考中..."
     * @return 返回 dialog 对象，以便任务完成后手动调用 .close()
     */
    private JFXDialog showLoadingDialog(String message) {
        JFXDialogLayout content = new JFXDialogLayout();

        // 1. 创建加载动画
        javafx.scene.control.ProgressIndicator spinner = new javafx.scene.control.ProgressIndicator();
        spinner.setPrefSize(30, 30);

        // 2. 创建提示文字
        Label label = new Label(message);
        label.setStyle("-fx-font-size: 15px; -fx-text-fill: #606266; -fx-font-weight: bold;");

        // 3. 布局：垂直排列，居中
        VBox layout = new VBox(15, spinner, label); // 间距 15px
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(20)); // 内边距，让弹窗不那么挤

        content.setBody(layout);

        // 4. 创建弹窗 (依附于 rootStackPane)
        JFXDialog dialog = new JFXDialog(rootStackPane, content, JFXDialog.DialogTransition.CENTER);

        // 🔥🔥🔥 核心设置 🔥🔥🔥
        // 设置为 false，禁止用户点击遮罩层关闭弹窗
        // 这样用户在 AI 分析完成前就无法操作其他界面，保证数据安全
        dialog.setOverlayClose(false);

        dialog.show();
        return dialog;
    }

    /**
     * 辅助方法：将 Bill 对象包装成发送给 AI 的数据格式
     */
    private Map<String, Object> prepareBillForAi(Bill b) {
        Map<String, Object> map = new HashMap<>();

        // 1. 提取描述：通常取备注的第一部分作为核心特征
        String cleanDesc = b.getRemark() != null ? b.getRemark().replace(" (导入)", "").split("-")[0] : "未知消费";
        map.put("desc", cleanDesc);

        // 2. 传入金额：用于 AI 判断收支逻辑
        map.put("amount", b.getAmount());

        // 3. 传入收支类型提示
        map.put("type_hint", b.getType());

        return map;
    }

    private String getSafeDesc(String remark) {
        if (remark == null || remark.isEmpty()) return "其他交易";
        // 移除导入后缀
        String clean = remark.replace(" (导入)", "").trim();
        // 针对“商户消费”这种没有横杠的情况，直接返回全文
        if (clean.contains("-")) {
            String parts[] = clean.split("-");
            // 如果横杠前是空的（如“-商户消费”），取全文，否则取前半部分
            return parts[0].trim().isEmpty() ? clean : parts[0].trim();
        }
        return clean;
    }
    // ---------- 辅助类 -----------
    public static class ReviewItem {
        private final String originalDesc;
        private final StringProperty parentCategory = new SimpleStringProperty(); // 一级
        private final StringProperty subCategory = new SimpleStringProperty();    // 二级
        private final BooleanProperty isNew = new SimpleBooleanProperty();
        private final BooleanProperty approved = new SimpleBooleanProperty(true);
        private final String fallback; // 记录 AI 提供的兜底一级分类
        private final String billType;
        private final String uniqueId;

        public ReviewItem(Bill bill, AICategorizer.CategoryResult res, String uniqueId) {
            this.originalDesc = bill.getRemark().split("-")[0];
            this.isNew.set(res.isNew);
            this.fallback = res.fallback;
            this.billType = bill.getType();
            this.uniqueId = uniqueId;

            // 解析 AI 的建议，例如 "餐饮 - 三餐"
            if (res.suggestion.contains(" - ")) {
                String[] parts = res.suggestion.split(" - ");
                this.parentCategory.set(parts[0].trim());
                this.subCategory.set(parts[1].trim());
            } else {
                this.parentCategory.set(res.suggestion.trim());
                this.subCategory.set("无"); // 默认无二级
            }
        }

        // --- 公开 Getter 确保表格能读取 ---
        public String getOriginalDesc() { return originalDesc; }
        public String getBillType() { return billType; }
        public StringProperty parentCategoryProperty() { return parentCategory; }
        public StringProperty subCategoryProperty() { return subCategory; }
        public BooleanProperty isNewProperty() { return isNew; }
        public BooleanProperty approvedProperty() { return approved; }
        public String getFallback() { return fallback; }
        public String getUniqueId() { return uniqueId; }
    }

    /**
     * 账单聚合 Key：通过 [备注关键字 + 收支类型] 共同决定唯一性
     */
    public static class GroupKey {
        private final String desc;
        private final String type;

        public GroupKey(String desc, String type) {
            this.desc = desc;
            this.type = type;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            GroupKey groupKey = (GroupKey) o;
            return Objects.equals(desc, groupKey.desc) && Objects.equals(type, groupKey.type);
        }

        @Override
        public int hashCode() {
            return Objects.hash(desc, type);
        }

        public String getDesc() { return desc; }
        public String getType() { return type; }
    }
}



