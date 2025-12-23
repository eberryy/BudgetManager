module cn.bit.budget.budgetmanager {
    requires javafx.controls;
    requires javafx.fxml;
    requires org.apache.poi.poi;
    requires org.apache.poi.ooxml;
    requires com.jfoenix;
    requires javafx.web;
    requires javafx.graphics;
    requires java.net.http;
    requires com.google.gson;
    requires java.sql;
    // 允许 JFoenix 反射访问你的 Controller 包，否则 UI 组件没法注入
    opens cn.bit.budget.controller to javafx.fxml, com.jfoenix, javafx.base;
    // 允许 JFoenix 访问你的模型（如果有用到数据绑定）
    opens cn.bit.budget.model to javafx.base;
    // 允许 Gson 反射访问 util 包
    opens cn.bit.budget.util to com.google.gson;
    // 导出控制器包给 javafx.fxml 模块
    exports cn.bit.budget.controller to javafx.fxml;

    opens cn.bit.budget.budgetmanager to javafx.fxml;
    exports cn.bit.budget.budgetmanager;


}

