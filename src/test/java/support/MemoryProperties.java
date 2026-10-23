package support;

import com.intellij.ide.util.PropertiesComponent;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MemoryProperties extends PropertiesComponent {
    private final Map<String, String> values = new HashMap<>();

    @Override
    public void unsetValue(String name) {
        values.remove(name);
    }

    @Override
    public boolean isValueSet(String name) {
        return values.containsKey(name);
    }

    @Override
    public String getValue(String name) {
        return values.get(name);
    }

    @Override
    public void setValue(String name, String value) {
        values.put(name, value);
    }

    @Override
    public void setValue(String name, String value, String defaultValue) {
        setValue(name, value);
    }

    @Override
    public void setValue(String name, float value, float defaultValue) {
        setValue(name, String.valueOf(value));
    }

    @Override
    public void setValue(String name, int value, int defaultValue) {
        setValue(name, String.valueOf(value));
    }

    @Override
    public void setValue(String name, boolean value, boolean defaultValue) {
        setValue(name, String.valueOf(value));
    }

    @Override
    public String[] getValues(String name) {
        String value = getValue(name);
        return value == null ? null : value.split(";");
    }

    @Override
    public void setValues(String name, String[] values) {
        setValue(name, String.join(";", values));
    }

    @Override
    public List<String> getList(String name) {
        String[] values = getValues(name);
        return values == null ? null : List.of(values);
    }

    @Override
    public void setList(String name, Collection<String> values) {
        setValue(name, String.join(";", values));
    }

    @Override
    public boolean updateValue(String name, boolean value) {
        boolean previous = getBoolean(name);
        setValue(name, value);
        return previous != value;
    }
}
