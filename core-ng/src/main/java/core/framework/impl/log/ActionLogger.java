package core.framework.impl.log;

import java.io.Writer;
import java.util.LinkedList;
import java.util.List;

/**
 * @author neo
 */
class ActionLogger {
    private static final int MAX_HOLD_SIZE = 5000;

    private final LogWriter logWriter;
    private final LogForwarder logForwarder;

    final ActionLog log = new ActionLog();
    private List<LogEvent> events = new LinkedList<>();
    private Writer traceWriter;

    public ActionLogger(LogWriter logWriter, LogForwarder logForwarder) {
        this.logWriter = logWriter;
        this.logForwarder = logForwarder;
    }

    public void process(LogEvent event) {
        log.updateResult(event.level);

        if (events != null) {
            events.add(event);
            if (event.level.value >= LogLevel.WARN.value || events.size() > MAX_HOLD_SIZE) {
                flushTraceLogs();
                events = null;
            }
        } else {
            writeTraceLog(event);
        }
    }

    public void end() {
        log.end();

        if (logForwarder != null) {
            logForwarder.sendActionLog(log);
        }

        logWriter.writeActionLog(log);

        if (traceWriter != null)
            logWriter.closeTraceLogWriter(traceWriter);
    }

    private void flushTraceLogs() {
        traceWriter = logWriter.createTraceWriter(log);
        events.forEach(this::writeTraceLog);
    }

    void writeTraceLog(LogEvent event) {
        logWriter.writeTraceLog(traceWriter, event);
    }
}
