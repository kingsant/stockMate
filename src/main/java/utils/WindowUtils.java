package utils;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @Created by DAIE
 * @Date 2021/3/8 20:26
 * @Description leek面板TABLE工具类
 */
public class WindowUtils {
    //股票表头
    public static final String STOCK_TABLE_HEADER_KEY = "stock_table_header_key2"; //移动表头时存储的key
    public static final String STOCK_TABLE_HEADER_VALUE = "编码,股票名称,涨跌幅,最高价,最低价,当前价,更新时间";

    private static final HashMap<String, String> remapPinYinMap = new HashMap<>();

    static {
        remapPinYinMap.put(PinYinUtils.toPinYin("编码"), "编码");
        remapPinYinMap.put(PinYinUtils.toPinYin("更新时间"), "更新时间");
        remapPinYinMap.put(PinYinUtils.toPinYin("股票名称"), "股票名称");
        remapPinYinMap.put(PinYinUtils.toPinYin("当前价"), "当前价");
        remapPinYinMap.put(PinYinUtils.toPinYin("涨跌幅"), "涨跌幅");
        remapPinYinMap.put(PinYinUtils.toPinYin("最高价"), "最高价");
        remapPinYinMap.put(PinYinUtils.toPinYin("最低价"), "最低价");
    }

    /**
     * 通过列名 获取该TABLE的列的数组下标
     *
     * @param columnNames 列名数组
     * @param columnName  要获取的列名
     * @return 返回给出列名的数组下标 匹配失败返回-1
     */
    public static int getColumnIndexByName(String[] columnNames, String columnName) {
        for (int i = 0; i < columnNames.length; i++) {
            if (columnNames[i].equals(columnName)) {
                return i;
            }
        }
        return -1;
    }

    public static String remapPinYin(String pinyin) {
        return remapPinYinMap.getOrDefault(pinyin, pinyin);
    }

    public static String[] normalizeStockColumns(String savedColumns) {
        Set<String> allowedColumns = new LinkedHashSet<>();
        for (String name : STOCK_TABLE_HEADER_VALUE.split(",")) {
            allowedColumns.add(name);
        }
        Set<String> columns = new LinkedHashSet<>();
        if (savedColumns != null) {
            for (String name : savedColumns.split(",")) {
                String column = remapPinYin(name.trim());
                if (allowedColumns.contains(column)) {
                    columns.add(column);
                }
            }
        }
        columns.addAll(allowedColumns);
        return columns.toArray(new String[0]);
    }
}
