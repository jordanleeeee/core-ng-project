package core.framework.impl.redis;

import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author neo
 */
class BytesValueMapLogParam {
    private final Map<String, byte[]> values;

    BytesValueMapLogParam(Map<String, byte[]> values) {
        this.values = values;
    }

    @Override
    public String toString() {
        var builder = new StringBuilder();
        builder.append('{');
        int index = 0;
        for (Map.Entry<String, byte[]> entry : values.entrySet()) {
            if (index > 0) builder.append(", ");
            builder.append(entry.getKey())
                   .append('=')
                   .append(new String(entry.getValue(), UTF_8));
            index++;
        }
        return builder.append('}').toString();
    }
}