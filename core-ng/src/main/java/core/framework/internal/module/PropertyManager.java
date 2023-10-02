package core.framework.internal.module;

import core.framework.util.ASCII;
import core.framework.util.Properties;
import core.framework.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * @author neo
 */
public class PropertyManager {
    private final Logger logger = LoggerFactory.getLogger(PropertyManager.class);

    private final PropertyParser parser = new PropertyParser();
    public final Properties properties = new Properties();
    public Set<String> usedProperties = new HashSet<>();

    public Optional<String> property(String key) {
        usedProperties.add(key);
        if (!properties.containsKey(key)) return Optional.empty();   // if the key is not defined in property file, do not check env, make env more deterministic, e.g. env may define property not in property files, which make integration-test inconsistent with runtime

        String envVarName = envVarName(key);
        // use env var to override property, e.g. under docker/kubenetes, SYS_HTTP_LISTEN to override sys.http.listen
        // in kube env, ConfigMap can be bound as env variables
        String value = System.getenv(envVarName);
        if (!Strings.isBlank(value)) {
            logger.info("found overridden property by env var {}, key={}, value={}", envVarName, key, maskValue(key, value));
            return Optional.of(value);
        }
        value = System.getProperty(key);     // use system property to override property, e.g. -Dsys.http.listen=8080
        if (!Strings.isBlank(value)) {
            logger.info("found overridden property by system property -D{}, key={}, value={}", key, key, maskValue(key, value));
            return Optional.of(value);
        }

        return properties.get(key);
    }

    public Object typedProperty(Field field, String key, boolean json, char delimiter) {
        String propertyValue = property(key).orElseThrow(() -> new Error("property key not found, key=" + key));
        return parser.parseTypedProperty(field, key, propertyValue, json, delimiter);
    }

    public String maskValue(String key, String value) { // generally only password or secretKey will be put into property file
        String lowerCaseKey = ASCII.toLowerCase(key);
        if (lowerCaseKey.contains("password") || lowerCaseKey.contains("secret")) return "******";
        return value;
    }

    String envVarName(String propertyKey) {
        var builder = new StringBuilder();
        int length = propertyKey.length();
        for (int i = 0; i < length; i++) {
            char ch = propertyKey.charAt(i);
            if (ch == '.') builder.append('_');
            else builder.append(ASCII.toUpperCase(ch));
        }
        return builder.toString();
    }

    void validate() {
        List<String> notUsedKeys = new ArrayList<>();
        for (String key : properties.keys()) {
            if (!usedProperties.contains(key)) notUsedKeys.add(key);
        }
        if (!notUsedKeys.isEmpty()) {
            throw new Error("found not used properties, please remove unnecessary config, keys=" + notUsedKeys);
        }
        usedProperties = null;  // release memory
    }
}
