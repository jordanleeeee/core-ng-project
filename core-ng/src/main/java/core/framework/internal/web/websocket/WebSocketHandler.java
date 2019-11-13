package core.framework.internal.web.websocket;

import core.framework.http.HTTPMethod;
import core.framework.internal.log.ActionLog;
import core.framework.internal.log.LogManager;
import core.framework.internal.web.bean.ResponseBeanMapper;
import core.framework.internal.web.request.RequestImpl;
import core.framework.internal.web.session.SessionManager;
import core.framework.util.Sets;
import core.framework.web.Session;
import core.framework.web.exception.BadRequestException;
import core.framework.web.exception.NotFoundException;
import core.framework.web.websocket.ChannelListener;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderMap;
import io.undertow.util.Headers;
import io.undertow.websockets.core.CloseMessage;
import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import io.undertow.websockets.core.protocol.Handshake;
import io.undertow.websockets.core.protocol.version13.Hybi13Handshake;
import io.undertow.websockets.spi.AsyncWebSocketHttpServerExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static core.framework.util.Strings.format;

/**
 * @author neo
 */
public class WebSocketHandler implements org.xnio.ChannelListener<WebSocketChannel> {
    static final String CHANNEL_KEY = "CHANNEL";
    public final WebSocketContextImpl context = new WebSocketContextImpl();
    private final Logger logger = LoggerFactory.getLogger(WebSocketHandler.class);
    private final Map<String, ChannelListener> listeners = new HashMap<>();
    private final Set<WebSocketChannel> channels = Sets.newConcurrentHashSet();
    private final Handshake handshake = new Hybi13Handshake();
    private final WebSocketMessageListener messageListener;
    private final SessionManager sessionManager;
    private final ResponseBeanMapper mapper;

    public WebSocketHandler(LogManager logManager, SessionManager sessionManager, ResponseBeanMapper mapper) {
        messageListener = new WebSocketMessageListener(logManager);
        this.sessionManager = sessionManager;
        this.mapper = mapper;
    }

    public boolean checkWebSocket(HTTPMethod method, HeaderMap headers) {
        if (method == HTTPMethod.GET && headers.getFirst(Headers.SEC_WEB_SOCKET_KEY) != null) {
            String version = headers.getFirst(Headers.SEC_WEB_SOCKET_VERSION);
            if ("13".equals(version)) return true;  // only support latest ws version
            throw new BadRequestException("only support web socket version 13, version=" + version, "INVALID_HTTP_REQUEST");
        }
        return false;
    }

    // refer to io.undertow.websockets.WebSocketProtocolHandshakeHandler
    public void handle(HttpServerExchange exchange, RequestImpl request, ActionLog actionLog) {
        String path = exchange.getRequestPath();
        String action = "ws:" + path;
        actionLog.action(action + ":open");

        ChannelListener listener = listeners.get(path);
        if (listener == null) throw new NotFoundException("not found, path=" + path, "PATH_NOT_FOUND");

        request.session = loadSession(request, actionLog);  // load session as late as possible, so for sniffer/scan request with sessionId, it won't call redis every time even for 404/405

        var webSocketExchange = new AsyncWebSocketHttpServerExchange(exchange, channels);
        exchange.upgradeChannel((connection, httpServerExchange) -> {
            WebSocketChannel channel = handshake.createChannel(webSocketExchange, connection, webSocketExchange.getBufferPool());
            channels.add(channel);
            var wrapper = new ChannelImpl(channel, context, listener, mapper);
            wrapper.action = action;
            wrapper.clientIP = request.clientIP();
            wrapper.refId = actionLog.id;   // with ws, correlationId and refId must be same as parent http action id
            actionLog.context("channel", wrapper.id);
            channel.setAttribute(CHANNEL_KEY, wrapper);
            channel.addCloseTask(this);

            listener.onConnect(request, wrapper);
            actionLog.context("room", wrapper.rooms.toArray()); // may join room onConnect

            channel.getReceiveSetter().set(messageListener);
            channel.resumeReceives();
        });
        handshake.handshake(webSocketExchange);
    }

    Session loadSession(RequestImpl request, ActionLog actionLog) {
        Session session = sessionManager.load(request, actionLog);
        if (session == null) return null;
        return new ReadOnlySession(session);
    }

    public void shutdown() {
        for (var channel : channels) {
            WebSockets.sendClose(CloseMessage.GOING_AWAY, "server is shutting down", channel, ChannelCallback.INSTANCE);
        }
    }

    @Override
    public void handleEvent(WebSocketChannel channel) { // only handle channel close event, refer to "channel.addCloseTask(this);" above
        var wrapper = (ChannelImpl) channel.getAttribute(CHANNEL_KEY);
        context.remove(wrapper);
    }

    public void add(String path, ChannelListener listener) {
        if (path.contains("/:")) throw new Error(format("web socket path must be static, path={}", path));

        Class<? extends ChannelListener> listenerClass = listener.getClass();
        if (listenerClass.isSynthetic())
            throw new Error(format("listener class must not be anonymous class or lambda, please create static class, listenerClass={}", listenerClass.getCanonicalName()));

        logger.info("ws, path={}, listener={}", path, listenerClass.getCanonicalName());
        ChannelListener previous = listeners.putIfAbsent(path, listener);
        if (previous != null) throw new Error(format("found duplicate web socket listener, path={}, previousClass={}", path, previous.getClass().getCanonicalName()));
    }
}
