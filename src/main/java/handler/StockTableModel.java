package handler;

import bean.StockBean;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ui.table.JBTable;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import utils.PinYinUtils;
import utils.WindowUtils;

import java.awt.FontMetrics;
import java.util.Comparator;
import java.util.List;
import java.util.Vector;

import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

public class StockTableModel extends DefaultTableModel {
    private final String[] columnNames;
    /**
     * 存放【编码】的位置，更新数据时用到
     */
    public int codeColumnIndex;

    private JTable table;
    private boolean normalMode = true;

    public StockTableModel(JTable table) {
        PropertiesComponent instance = PropertiesComponent.getInstance();
        columnNames = WindowUtils.normalizeStockColumns(instance.getValue(WindowUtils.STOCK_TABLE_HEADER_KEY));
        instance.setValue(WindowUtils.STOCK_TABLE_HEADER_KEY, String.join(",", columnNames));

        for (int i = 0; i < columnNames.length; i++) {
            if ("编码".equals(columnNames[i])) {
                codeColumnIndex = i;
            }
        }

        this.table = table;
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        // Fix tree row height
        FontMetrics metrics = table.getFontMetrics(table.getFont());
        table.setRowHeight(Math.max(table.getRowHeight(), metrics.getHeight()));
        table.setModel(this);
        refreshDisplay(!normalMode);
    }

    public void refreshDisplay(boolean normalMode) {
        if (this.normalMode == normalMode) {
            return;
        }
        this.normalMode = normalMode;
        // 刷新表头
        if (normalMode) {
            setColumnIdentifiers(columnNames);
        } else {
            setColumnIdentifiers(PinYinUtils.toPinYin(columnNames));
        }
        TableRowSorter<DefaultTableModel> rowSorter = new TableRowSorter<>(this);
        Comparator<Object> doubleComparator = new Comparator<>() {
            @Override
            public int compare(Object first, Object second) {
                Double firstValue = NumberUtils.toDouble(StringUtils.remove(String.valueOf(first), '%'));
                Double secondValue = NumberUtils.toDouble(StringUtils.remove(String.valueOf(second), '%'));
                return firstValue.compareTo(secondValue);
            }
        };
        for (String name : new String[]{"当前价", "涨跌幅", "最高价", "最低价"}) {
            int index = WindowUtils.getColumnIndexByName(columnNames, name);
            if (index >= 0) {
                rowSorter.setComparator(index, doubleComparator);
            }
        }
        table.setRowSorter(rowSorter);
    }

    public void showQuotes(List<StockBean> quotes) {
        boolean sameRows = getRowCount() == quotes.size();
        if (sameRows) {
            for (int i = 0; i < quotes.size(); i++) {
                if (!quotes.get(i).getCode().equals(getValueAt(i, codeColumnIndex))) {
                    sameRows = false;
                    break;
                }
            }
        }
        if (!sameRows) {
            clearRow();
        }
        for (StockBean quote : quotes) {
            updateData(quote);
        }
    }

    /**
     * 设置表格条纹（斑马线）<br>
     *
     * @param striped true设置条纹
     * @throws RuntimeException 如果table不是{@link JBTable}类型，请自行实现setStriped
     */
    public void setStriped(boolean striped) {
        if (table instanceof JBTable) {
            ((JBTable) table).setStriped(striped);
        } else {
            throw new RuntimeException("table不是JBTable类型，请自行实现setStriped");
        }
    }

    protected void updateData(StockBean bean) {
        if (bean.getCode() == null) {
            return;
        }
        Vector<Object> convertData = convertData(bean);
        if (convertData == null) {
            return;
        }
        // 获取行
        int index = findRowIndex(codeColumnIndex, bean.getCode());
        if (index >= 0) {
            updateRow(index, convertData);
        } else {
            addRow(convertData);
        }
    }

    /**
     * 参考源码{@link DefaultTableModel#setValueAt}，此为直接更新行，提高点效率
     *
     * @param rowIndex
     * @param rowData
     */
    protected void updateRow(int rowIndex, Vector<Object> rowData) {
        dataVector.set(rowIndex, rowData);
        // 通知listeners刷新ui
        fireTableRowsUpdated(rowIndex, rowIndex);
    }

    /**
     * 参考源码{@link DefaultTableModel#removeRow(int)}，此为直接清除全部行，提高点效率
     */
    public void clearRow() {
        int size = dataVector.size();
        if (0 < size) {
            dataVector.clear();
            // 通知listeners刷新ui
            fireTableRowsDeleted(0, size - 1);
        }
    }

    /**
     * 查找列项中的valueName所在的行
     *
     * @param columnIndex 列号
     * @param value       值
     * @return 如果不存在返回-1
     */
    protected int findRowIndex(int columnIndex, String value) {
        int rowCount = getRowCount();
        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            Object valueAt = getValueAt(rowIndex, columnIndex);
            if (StringUtils.equalsIgnoreCase(value, valueAt.toString())) {
                return rowIndex;
            }
        }
        return -1;
    }

    private Vector<Object> convertData(StockBean stockBean) {
        if (stockBean == null) {
            return null;
        }
        // 与columnNames中的元素保持一致
        Vector<Object> v = new Vector<Object>(columnNames.length);
        for (int i = 0; i < columnNames.length; i++) {
            v.addElement(stockBean.getValueByColumn(columnNames[i], normalMode));
        }
        return v;
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return false;
    }
}
