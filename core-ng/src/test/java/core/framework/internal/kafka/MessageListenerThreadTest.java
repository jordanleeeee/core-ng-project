package core.framework.internal.kafka;

import core.framework.internal.json.JSONMapper;
import core.framework.internal.log.ActionLog;
import core.framework.internal.log.LogLevel;
import core.framework.internal.log.LogManager;
import core.framework.json.JSON;
import core.framework.kafka.BulkMessageHandler;
import core.framework.kafka.Message;
import core.framework.kafka.MessageHandler;
import core.framework.util.Strings;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;

/**
 * @author neo
 */
@ExtendWith(MockitoExtension.class)
class MessageListenerThreadTest {
    @Mock
    MessageHandler<TestMessage> messageHandler;
    @Mock
    Consumer<String, byte[]> consumer;
    @Mock
    BulkMessageHandler<TestMessage> bulkMessageHandler;
    private MessageListenerThread thread;
    private LogManager logManager;

    @BeforeEach
    void createKafkaMessageListenerThread() {
        logManager = new LogManager();
        thread = new MessageListenerThread("listener-thread-1", consumer, new MessageListener(null, null, logManager, 300_000L));
    }

    @Test
    void header() {
        var headers = new RecordHeaders();
        headers.add("header", Strings.bytes("value"));
        assertThat(thread.header(headers, "header")).isEqualTo("value");
        assertThat(thread.header(headers, "nonExisted")).isNull();
    }

    @Test
    void messages() throws IOException {
        ConsumerRecord<String, byte[]> record = new ConsumerRecord<>("topic", 0, 1, "key", Strings.bytes("{}"));
        record.headers().add(MessageHeaders.HEADER_CLIENT, Strings.bytes("client"));
        record.headers().add(MessageHeaders.HEADER_REF_ID, Strings.bytes("refId"));
        record.headers().add(MessageHeaders.HEADER_CORRELATION_ID, Strings.bytes("correlationId"));
        var actionLog = new ActionLog(null, null);
        List<Message<TestMessage>> messages = thread.messages(List.of(record), actionLog, JSONMapper.reader(TestMessage.class));

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).key).isEqualTo("key");
        assertThat(actionLog.context.get("key")).containsExactly("key");
        assertThat(actionLog.clients).containsExactly("client");
        assertThat(actionLog.refIds).containsExactly("refId");
        assertThat(actionLog.correlationIds).containsExactly("correlationId");
    }

    @Test
    void checkConsumerDelay() {
        var actionLog = logManager.begin(null, null);
        thread.checkConsumerDelay(actionLog, actionLog.date.minusSeconds(5).toEpochMilli(), Duration.ofSeconds(3).toNanos());
        assertThat(actionLog.stats).containsEntry("consumer_delay", (double) Duration.ofSeconds(5).toNanos());
        assertThat(actionLog.result).isEqualTo(LogLevel.WARN);
        assertThat(actionLog.errorCode()).isEqualTo("LONG_CONSUMER_DELAY");

        actionLog = logManager.begin(null, null);
        thread.checkConsumerDelay(actionLog, actionLog.date.minus(Duration.ofMinutes(16)).toEpochMilli(), Duration.ofSeconds(30).toNanos());
        assertThat(actionLog.stats).containsEntry("consumer_delay", (double) Duration.ofMinutes(16).toNanos());
        assertThat(actionLog.result).isEqualTo(LogLevel.ERROR);
        assertThat(actionLog.errorCode()).isEqualTo("LONG_CONSUMER_DELAY");

        actionLog = logManager.begin(null, null);
        thread.checkConsumerDelay(actionLog, actionLog.date.minusSeconds(1).toEpochMilli(), Duration.ofSeconds(6).toNanos());
        assertThat(actionLog.stats).containsEntry("consumer_delay", (double) Duration.ofSeconds(1).toNanos());
        assertThat(actionLog.errorCode()).isNull();
    }

    @Test
    void handle() throws Exception {
        var key = "key";
        var message = new TestMessage();
        message.stringField = "value";
        var record = new ConsumerRecord<>("topic", 0, 0, key, Strings.bytes(JSON.toJSON(message)));
        record.headers().add(MessageHeaders.HEADER_TRACE, Strings.bytes("true"));
        record.headers().add(MessageHeaders.HEADER_CLIENT, Strings.bytes("client"));
        thread.handle("topic", new MessageProcess<>(messageHandler, null, TestMessage.class), List.of(record));

        verify(messageHandler).handle(eq(key), argThat(value -> "value".equals(value.stringField)));
    }

    @Test
    void handleWithNullKey() throws Exception {
        var message = new TestMessage();
        message.stringField = "value";
        var record = new ConsumerRecord<>("topic", 0, 0, (String) null, Strings.bytes(JSON.toJSON(message)));
        record.headers().add(MessageHeaders.HEADER_TRACE, Strings.bytes("true"));
        record.headers().add(MessageHeaders.HEADER_CLIENT, Strings.bytes("client"));
        record.headers().add(MessageHeaders.HEADER_TRACE, Strings.bytes("cascade"));
        thread.handle("topic", new MessageProcess<>(messageHandler, null, TestMessage.class), List.of(record));

        verify(messageHandler).handle(isNull(), argThat(value -> "value".equals(value.stringField)));
    }

    @Test
    void handleBulk() throws Exception {
        var key = "key";
        var message = new TestMessage();
        message.stringField = "value";
        var record = new ConsumerRecord<>("topic", 0, 0, key, Strings.bytes(JSON.toJSON(message)));
        record.headers().add(MessageHeaders.HEADER_CORRELATION_ID, Strings.bytes("correlationId"));
        record.headers().add(MessageHeaders.HEADER_REF_ID, Strings.bytes("refId"));
        thread.handleBulk("topic", new MessageProcess<>(null, bulkMessageHandler, TestMessage.class), List.of(record));

        verify(bulkMessageHandler).handle(argThat(value -> value.size() == 1
                                                           && key.equals(value.get(0).key)
                                                           && "value".equals(value.get(0).value.stringField)));
    }

    @Test
    void shutdown() {
        thread.shutdown();
        verify(consumer).wakeup();
    }
}
