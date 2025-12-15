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

        // 初始化分类
        filterCategoryBox.getItems().add("全部分类");
        filterCategoryBox.getItems().addAll(CategoryManager.getParentCategories());
        filterCategoryBox.setValue("全部分类");

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
            private final javafx.scene.web.WebView webView = new javafx.scene.web.WebView();
            private final javafx.scene.web.WebEngine webEngine = webView.getEngine();

            {
                webView.setPrefHeight(25);
                webView.setMaxHeight(25);
                webView.setPageFill(javafx.scene.paint.Color.TRANSPARENT);
                webView.setContextMenuEnabled(false);
            }

            @Override
            protected void updateItem(String category, boolean empty) {
                super.updateItem(category, empty);
                if (empty || category == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    String emoji = CategoryManager.getEmoji(category);

                    // 【核心修改】不显示字符，而是生成 Twemoji 的图片链接
                    String imgUrl = getTwemojiUrl(emoji);

                    // 简单的 HTML，使用 Flex 布局居中
                    String html = String.format("""
                        <html>
                        <body style='margin: 0; padding: 0; background-color: transparent; overflow: hidden; font-family: "Microsoft YaHei", sans-serif;'>
                            <div style='
                                display: inline-flex;
                                align-items: center;
                                background-color: #e6f7ff;
                                border: 1px solid #91d5ff;
                                border-radius: 4px;
                                padding: 2px 8px;
                                box-sizing: border-box;
                                height: 22px;
                                white-space: nowrap;
                            '>
                                <img src='%s' style='width: 16px; height: 16px; margin-right: 4px; vertical-align: middle;'>
                                <span style='font-size: 12px; color: #096dd9; font-weight: bold;'>%s</span>
                            </div>
                        </body>
                        </html>
                        """, imgUrl, category);

                    webEngine.loadContent(html);
                    setGraphic(webView);
                    setText(null);
                }
            }

            /**
             * 修改后：从本地资源文件夹加载图片
             * 确保图片放在 src/main/resources/cn/bit/budget/icons/ 目录下
             */
            private String getTwemojiUrl(String emoji) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < emoji.length(); ) {
                    int codePoint = emoji.codePointAt(i);
                    if (codePoint != 0xFE0F) { // 忽略变体符
                        if (sb.length() > 0) sb.append("-");
                        sb.append(Integer.toHexString(codePoint).toLowerCase());
                    }
                    i += Character.charCount(codePoint);
                }

                String iconName = sb.toString() + ".png";

                // 【核心修改】获取本地资源的 URL
                // 注意：路径必须以 / 开头，对应 resources 目录下的结构
                java.net.URL localUrl = getClass().getResource("/cn/bit/budget/icons/" + iconName);

                if (localUrl != null) {
                    return localUrl.toExternalForm();
                } else {
                    // 如果万一忘了下载某张图，可以返回一个默认图，或者保持 CDN 作为备选
                    // System.err.println("缺失图标: " + iconName);
                    return "https://cdnjs.cloudflare.com/ajax/libs/twemoji/14.0.2/72x72/" + iconName;
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
     * 主页面：添加自定义一级分类
     */
    @FXML
    public void onAddFilterCategory(ActionEvent event) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("新增一级分类");
        dialog.setHeaderText("添加自定义一级分类");
        dialog.setContentText("分类名称:");

        dialog.showAndWait().ifPresent(name -> {
            if (!name.trim().isEmpty()) {
                // 1. 添加到管理器（会自动保存）
                CategoryManager.addCustomParentCategory(name);
                // 2. 刷新下拉框
                if (!filterCategoryBox.getItems().contains(name)) {
                    filterCategoryBox.getItems().add(name);
                }
                // 3. 自动选中新添加的分类
                filterCategoryBox.setValue(name);
                // 4. 提示用户
                showInfoAlert("添加成功", "已添加一级分类：" + name);
            }
        });
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

        // 步骤 E: (可选) 设置饼图标题动态变化
        if (isViewingSubCategories) {
            expensePieChart.setTitle(currentFilterCat + " - 支出明细");
        } else {
            expensePieChart.setTitle("总支出构成");
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
            FXMLLoader loader = new FXMLLoader(HelloController.class.getResource("/cn/bit/budget/add-bill-view.fxml"));
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

                showInfoAlert("导入成功", "成功导入了 " + importedBills.size() + " 条账单记录！");
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

    private void showInfoAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void showWarningAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}