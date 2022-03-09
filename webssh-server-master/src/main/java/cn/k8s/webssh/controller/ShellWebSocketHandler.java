package cn.k8s.webssh.controller;

import cn.k8s.webssh.common.utils.URIQueryUtil;
import cn.k8s.webssh.dto.ConsoleSize;
import cn.k8s.webssh.dto.Pod;
import cn.k8s.webssh.websocket.ShellExecutor;
import cn.k8s.webssh.dto.ShellMessage;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * websocket处理类
 * 处理连接、关闭、发送消息、异常等
 */
@Slf4j
@Component
// 建立类实现WebSocketHandler接口
public class ShellWebSocketHandler implements WebSocketHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShellWebSocketHandler.class);
    private ObjectMapper mapper = new ObjectMapper();
    private Map<String, ShellExecutor> shellExecutorMap = new ConcurrentHashMap<>();

    private String k8sUrl = "";
    private String caCrt = "";
    private String certData = "";
    private String keyData = "";
    
    /**
     * 连接成功后调用
     * @param webSocketSession
     * @throws Exception
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession webSocketSession) throws Exception {
        LOGGER.info("链接成功---------------------------------------------");
        LOGGER.info("connection established..." + webSocketSession.getRemoteAddress().toString());
    }

    /**
     * 有新的websocket消息到达时调用
     * @param webSocketSession
     * @param webSocketMessage
     * @throws Exception
     */
    @Override
    public void handleMessage(WebSocketSession webSocketSession, WebSocketMessage<?> webSocketMessage) throws Exception {
        try {
            //获取前台传递的消息
            String message = webSocketMessage.getPayload().toString();
            //转换为ShellMessage
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            ShellMessage shellMessage = mapper.readValue(message, ShellMessage.class);
            Map<String, String> params = URIQueryUtil.splitQuery(webSocketSession.getUri());
            LOGGER.info("消息类型-----------------------------------------" + shellMessage.getType());
            // 根据前端传的参数类型不同，调用不同的方法
            if (StringUtils.equals(shellMessage.getType(), "connect")) {
                // 链接方法
                handlerConnMessage(webSocketSession, shellMessage);
            } else if (StringUtils.equals(shellMessage.getType(), "send")) {
                // 发送消息的方法
                handlerCmdMessage(webSocketSession, shellMessage.getCmd());
            } else if (StringUtils.equals(shellMessage.getType(), "resize")){
                // 改变行和列的方法
                String rows = shellMessage.getRows();
                String cols = shellMessage.getCols();
                ConsoleSize consoleSize = new ConsoleSize(cols, rows);
                handlerResizeMessage(webSocketSession, consoleSize);
            } else {
                sendBackMsg(webSocketSession, "msg type error!");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 处理连接信息
     *
     * @param webSocketSession webSocketSession
     */
    private void handlerConnMessage(WebSocketSession webSocketSession, ShellMessage shellMessage) {
        LOGGER.info("链接中-------------------------------------");
        ShellExecutor oldShellExecutor = shellExecutorMap.get(webSocketSession.getId());
        if (oldShellExecutor != null) {
            oldShellExecutor.close();
            shellExecutorMap.remove(webSocketSession.getId());
        }
        Config config = new ConfigBuilder()
                .withCaCertData(caCrt)
                .withMasterUrl(k8sUrl)
                .withClientCertData(certData)
                .withClientKeyData(keyData)
                .withTrustCerts(true)
                .build();
        DefaultKubernetesClient client = new DefaultKubernetesClient(config);

        Pod pod = new Pod(shellMessage.getPod(), shellMessage.getNamespace(), shellMessage.getContainer());
        ShellExecutor shellExecutor = new ShellExecutor(client, webSocketSession, pod);
        int cols = shellMessage.getCols() == null ? 80 : Integer.parseInt(shellMessage.getCols());
        int rows = shellMessage.getRows() == null ? 27 : Integer.parseInt(shellMessage.getRows());
        LOGGER.info("cols:" + cols + "-----------------------------rows:" + rows );
        shellExecutor.resize(cols, rows);
        shellExecutorMap.put(webSocketSession.getId(), shellExecutor);
        LOGGER.info("shellExecutor init...current executor count: " + shellExecutorMap.size());
    }

    /**
     * 处理cmd信息
     *
     * @param webSocketSession webSocketSession

     */
    private void handlerCmdMessage(WebSocketSession webSocketSession, String cmd) throws InterruptedException, IOException {
        ShellExecutor shellExecutor = shellExecutorMap.get(webSocketSession.getId());
        if (shellExecutor != null) {
            if (cmd != null) {
                shellExecutor.exec(cmd);
            } else {
                sendBackMsg(webSocketSession, "cmd is null error!");
            }
        } else {
            sendBackMsg(webSocketSession, "application error!");
        }
    }

    private void handlerResizeMessage(WebSocketSession webSocketSession, ConsoleSize consoleSize) throws Exception {
        ShellExecutor shellExecutor = shellExecutorMap.get(webSocketSession.getId());
        if (shellExecutor != null) {
            if (consoleSize != null) {
                int cols = consoleSize.getCols() == null ? 80 : Integer.parseInt(consoleSize.getCols());
                int rows = consoleSize.getRows() == null ? 27 : Integer.parseInt(consoleSize.getRows());
                LOGGER.info("cols:" + cols + "-----------------------------rows:" + rows );
                shellExecutor.resize(cols, rows);
            } else {
                sendBackMsg(webSocketSession, "size is null error!");
            }
        } else {
            sendBackMsg(webSocketSession, "application error!");
        }
    }

    @Override
    public void handleTransportError(WebSocketSession webSocketSession, Throwable throwable) throws Exception {
        LOGGER.info("connection error... reason: " + throwable.toString());
    }

    /**
     * 连接被关闭或者出错时调用
     * @param webSocketSession
     * @param closeStatus
     * @throws Exception
     */
    @Override
    public void afterConnectionClosed(WebSocketSession webSocketSession, CloseStatus closeStatus) throws Exception {
        ShellExecutor shellExecutor = shellExecutorMap.get(webSocketSession.getId());
        shellExecutorMap.remove(webSocketSession.getId());
        if (shellExecutor != null) {
            shellExecutor.close();
        }
        LOGGER.info("shellExecutor closed...current executor count: " + shellExecutorMap.size());
        if (webSocketSession.isOpen()) {
            webSocketSession.close();
        }
        LOGGER.info("connection closed..." + webSocketSession.getRemoteAddress().toString());
    }

    @Override
    public boolean supportsPartialMessages() { return false; }

    private void sendBackMsg(WebSocketSession webSocketSession, String msg) {
        TextMessage textMessage = new TextMessage(msg);
        try {
            webSocketSession.sendMessage(textMessage);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

