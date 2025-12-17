package cn.bit.budget.controller;

import cn.bit.budget.dao.DataStore;
import cn.bit.budget.model.Bill;
import cn.bit.budget.util.BillImportUtil;
import cn.bit.budget.util.CategoryManager;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialog;
import com.jfoenix.controls.JFXDialogLayout;
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
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.net.URL;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.stream.Collectors;
import javafx.scene.paint.Color;

import javafx.scene.layout.VBox;
import java.util.function.Consumer;
import javafx.util.Duration;


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

        // 初始化分类
        updateCategoryFilterByType();

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

        // 2. 设置分类列：使用 WebView 加载 Twemoji 图片，实现全平台彩色显示
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
     * 核心方法：点击“查询/刷新”
     * 根据筛选条件过滤 allBills，并更新 UI
     */
    @FXML
    public void onSearchClick(ActionEvent event) {
        LocalDate start = startDatePicker.getValue();
        LocalDate end = endDatePicker.getValue();
        String category = filterCategoryBox.getValue();
        String type = typeFilterBox.getValue(); // 获取类型

        // 使用 Stream API 进行多条件过滤
        List<Bill> filteredList = allBills.stream()
                // 1. 日期过滤
                .filter(b -> start == null || !b.getDate().isBefore(start))
                .filter(b -> end == null || !b.getDate().isAfter(end))
                // 2. 分类过滤
                .filter(b -> category == null || "全部分类".equals(category) || category.equals(b.getCategory()))
                // 3. 【新增】收支类型过滤
                .filter(b -> type == null || "全部".equals(type) || type.equals(b.getType()))
                .collect(Collectors.toList());

        // 更新表格和图表
        updateTableAndChart(filteredList);
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
     * 主页面：添加自定义一级分类
     */
    /**
     * 现代化的新增一级分类
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
     * 删除自定义分类（一级或二级）
     */
    @FXML
    public void onDeleteFilterCategory(ActionEvent event) {
        String selectedCategory = filterCategoryBox.getValue();
        
        if (selectedCategory == null || "全部分类".equals(selectedCategory)) {
            showWarningAlert("提示", "请先选择要删除的分类");
            return;
        }

        // 检查是否为自定义分类
        if (!CategoryManager.isCustomCategory(selectedCategory)) {
            showWarningAlert("提示", "默认分类不能删除，只能删除自定义分类");
            return;
        }

        // 显示删除确认对话框
        showDeleteCategoryConfirmDialog(selectedCategory);
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
     * 执行删除分类操作
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
     * 核心方法：同时更新表格和统计图
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
                    // 如果是二级分类，也显示emoji
                    String emoji = CategoryManager.getEmoji(categoryName);
                    label = emoji + " " + categoryName;
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
        
        // 步骤 D2: 应用emoji字体样式到图例标签
        expensePieChart.setLegendVisible(true);
        javafx.application.Platform.runLater(() -> {
            applyEmojiStyleToPieChart();
        });

        // 步骤 E: 设置饼图标题动态变化（根据收支类型和分类）
        String selectedType = typeFilterBox.getValue();
        String typeLabel = "收入".equals(selectedType) ? "收入" : "支出";
        
        if (isViewingSubCategories) {
            expensePieChart.setTitle(currentFilterCat + " - " + typeLabel + "明细");
        } else {
            expensePieChart.setTitle("总" + typeLabel + "构成");
        }
    }

    /**
     * 为饼图应用emoji字体样式，确保emoji显示清晰
     */
    private void applyEmojiStyleToPieChart() {
        // 查找图例节点并应用emoji字体
        for (javafx.scene.Node node : expensePieChart.lookupAll(".chart-legend")) {
            if (node instanceof javafx.scene.layout.Region) {
                javafx.scene.layout.Region legend = (javafx.scene.layout.Region) node;
                
                // 遍历图例中的每个标签
                for (javafx.scene.Node item : legend.getChildrenUnmodifiable()) {
                    if (item instanceof javafx.scene.control.Label) {
                        javafx.scene.control.Label label = (javafx.scene.control.Label) item;
                        // 应用emoji字体，确保彩色显示
                        label.setStyle("-fx-font-family: 'Segoe UI Emoji', 'Apple Color Emoji', 'Noto Color Emoji', sans-serif; -fx-font-size: 14px;");
                    }
                }
            }
        }
        
        // 查找饼图标签节点并应用emoji字体
        for (javafx.scene.Node node : expensePieChart.lookupAll(".chart-pie-label")) {
            if (node instanceof javafx.scene.text.Text) {
                javafx.scene.text.Text text = (javafx.scene.text.Text) node;
                // 应用emoji字体
                text.setStyle("-fx-font-family: 'Segoe UI Emoji', 'Apple Color Emoji', 'Noto Color Emoji', sans-serif;");
            }
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

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("确认删除");
        alert.setHeaderText(null);
        alert.setContentText("确定要删除选中的 " + selectedItems.size() + " 条记录吗？");

        if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            // 1. 从总数据源中移除 (注意：removeAll 需要对象的 equals 方法支持，或使用 ID 匹配)
            // 由于 Bill 类没有重写 equals，这里建议直接使用 Collection 的 removeAll
            // 前提是 allBills 里的对象引用和表格里的是同一个 (目前逻辑是同一个，没问题)
            allBills.removeAll(selectedItems);

            // 2. 保存全量数据
            DataStore.saveBills(allBills);

            // 3. 刷新视图
            onSearchClick(null);

            // 清除选择
            billTable.getSelectionModel().clearSelection();
        }
    }

    @FXML
    public void onImportClick(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("选择微信账单文件");

        // 修改点 1: 添加支持 .csv 和 .xlsx
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("账单文件 (CSV, Excel)", "*.csv", "*.xlsx", "*.xls")
        );

        File file = fileChooser.showOpenDialog(billTable.getScene().getWindow());
        if (file != null) {

            // 修改点 2: 调用通用的 parse 方法，而不是 parseWeChatCSV
            List<Bill> importedBills = BillImportUtil.parse(file);

            if (!importedBills.isEmpty()) {
                allBills.addAll(importedBills);
                DataStore.saveBills(allBills);
                onSearchClick(null); // 刷新界面
                showGeneralSuccess("成功导入 " + importedBills.size() + " 条账单！");
            } else {
                showWarningAlert("导入提示", "未解析出有效账单，请确认文件格式是否为微信导出格式。");
            }
        }
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

    // --- 辅助方法 ---

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

// ==========================================
    //       ✨ 通用右上角胶囊弹窗逻辑 ✨
    // ==========================================

    /**
     * 场景 A：添加分类成功（自动根据分类名找图标）
     */
    private void showTopRightSuccess(String categoryName, String message) {
        String emoji = CategoryManager.getEmoji(categoryName);
        // 调用通用方法
        showUniversalToast(emoji, message);
    }

    /**
     * 场景 B：通用操作成功（如导入成功，手动指定一个图标，这里用 🎉）
     */
    private void showGeneralSuccess(String message) {
        // \uD83C\uDF89 是 🎉 的 Unicode，确保你的 icons 文件夹里有 1f389.png
        // 如果没有这个图，代码里的 try-catch 会自动处理，只显示文字
        showUniversalToast("\uD83C\uDF89", message);
    }

    /**
     * 核心私有方法：构建并显示弹窗
     * @param emojiStr Emoji 字符 (用于查找文件名)
     * @param message  提示文字
     */
    private void showUniversalToast(String emojiStr, String message) {
        // 1. 创建容器 HBox
        javafx.scene.layout.HBox toast = new javafx.scene.layout.HBox();
        toast.getStyleClass().add("top-right-toast");

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
}