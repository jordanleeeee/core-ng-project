package core.framework.mongo.config;

import core.framework.mongo.test.MockMongo;
import core.framework.test.inject.TestBeanFactory;
import core.framework.test.module.TestModuleContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author neo
 */
class TestMongoConfigTest {
    @Test
    void initialize() {
        TestMongoConfig config = new TestMongoConfig(new TestModuleContext(new TestBeanFactory()), null);
        assertThat(config.mongo).isInstanceOf(MockMongo.class);
    }
}
