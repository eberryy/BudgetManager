module cn.bit.budget.budgetmanager {
    requires javafx.controls;
    requires javafx.fxml;
    requires org.apache.poi.poi;
    requires org.apache.poi.ooxml;
    requires com.jfoenix;
    // 允许 JFoenix 反射访问你的 Controller 包，否则 UI 组件没法注入
    opens cn.bit.budget.controller to javafx.fxml, com.jfoenix;

    // 允许 JFoenix 访问你的模型（如果有用到数据绑定）
    opens cn.bit.budget.model to javafx.base;
    // 导出控制器包给 javafx.fxml 模块
    exports cn.bit.budget.controller to javafx.fxml;

    opens cn.bit.budget.budgetmanager to javafx.fxml;
    exports cn.bit.budget.budgetmanager;


}

