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

    private String k8sUrl = "https://10.110.114.13:6443";
    private String caCrt = "LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCk1JSUM1ekNDQWMrZ0F3SUJBZ0lCQURBTkJna3Foa2lHOXcwQkFRc0ZBREFWTVJNd0VRWURWUVFERXdwcmRXSmwKY201bGRHVnpNQjRYRFRJeU1ERXhNVEEzTkRFME1sb1hEVE15TURFd09UQTNOREUwTWxvd0ZURVRNQkVHQTFVRQpBeE1LYTNWaVpYSnVaWFJsY3pDQ0FTSXdEUVlKS29aSWh2Y05BUUVCQlFBRGdnRVBBRENDQVFvQ2dnRUJBTTh4CkdhT1RBTG5vQjdYVFBQMmxFV0dkMlIvNU8rZVBlOHl0TW9kZTdHQ2ZJalhraFdTNlAwRXBkdE4vcUpPMng1eGkKYmlXL0N0d2pPY0hQSEIyZjlnNFZLdWtLR29lZG5zdW13SjhhZklBTEpIMzBrRGMzc09tQ1ljL3pFZjRqNTV5LwpYNmFVQU1jWHhqemtlR2ZQdnZhb1hvR3k1d1RiWWRaVm01RU9jeEN3R1NSUExrTzFzRjZGVll4ci9WdStpTHdyCmRQWWdIRXAxYXcwMEt4elFtemFlbmEwc3pZTHZza2EvdlMyNG91QXhqbXM1TkxuWkhmSjdFdUFCZWRyaExVM1oKWWxQUUdoRHJKL2FreVdSN05mUHBhUmFPcmx4M3FLWEhFSyt3SXdKdjJjY1pmMDBXcExHK0hmWXNqWitZYWk1KwpKK2xXcGJhakNhOW1XV2U1Z0JVQ0F3RUFBYU5DTUVBd0RnWURWUjBQQVFIL0JBUURBZ0trTUE4R0ExVWRFd0VCCi93UUZNQU1CQWY4d0hRWURWUjBPQkJZRUZERUNEbW41N211SXJTNmhqRHBZKzg4Q0Y2N1pNQTBHQ1NxR1NJYjMKRFFFQkN3VUFBNElCQVFBQmw0eDJUQUVVY2t3K1dvVll4SlJzM2FYVzFBV240VTg2aEs5OTF5WmZXOS9MWEM4MgpVMHB1NWliVUVpb3FUTVRteUZCR3M2NC9oczRxKzdwSk1IVHdsaXYvcStuRm1QYmhrdmhSTEpYT2dCd1ZERkxCCmIyR2NnSHpwZmViY0kyS243Qnk3TXFuY2RVa2ZXYVYzTDhDV1hpeUFhbEY4SzFlbk91N3JuWmluR24vR3lVaTQKczVlOGlaLzU5cDdZYkFEYk53clordWpLdmthU2MxcGZtYmpudXdGTFZGTUZlTXNJWXVQanJhNjlONHFNVkxBYgp4dnZwUFRZSERUQzh6ejJtaHdrek5aRXNNQ1R1RUVpaTMzNTExRjRybXlLZzQxUE12VGhKRDdTSDNSNStpREZLCnFydERVNkdDaXUyZ0tJNTUvWE56dHExUTRSbVFvOHJwdWt0VQotLS0tLUVORCBDRVJUSUZJQ0FURS0tLS0tCg==";
    private String certData = "LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCk1JSURFekNDQWZ1Z0F3SUJBZ0lJR2NlenYyUzNVbzR3RFFZSktvWklodmNOQVFFTEJRQXdGVEVUTUJFR0ExVUUKQXhNS2EzVmlaWEp1WlhSbGN6QWVGdzB5TWpBeE1URXdOelF4TkRKYUZ3MHlNekF4TVRFd056UXhORFJhTURReApGekFWQmdOVkJBb1REbk41YzNSbGJUcHRZWE4wWlhKek1Sa3dGd1lEVlFRREV4QnJkV0psY201bGRHVnpMV0ZrCmJXbHVNSUlCSWpBTkJna3Foa2lHOXcwQkFRRUZBQU9DQVE4QU1JSUJDZ0tDQVFFQXhhNWQ1ZWJ4ZFJwQ3YzUGUKRnZES05HdjRQSEIrZGtlcTc3RFRTUng1T0xJb1laL3d6TjhhSExnMHNTTjIvMnpZUHJjTUJ1M1dzdmJlYW5vZgo4RHF6dFl3OUJvdVd1SUlIUWhzOTZMbXplNE9NRWJuV3JQZG9wdFBwZkNHYys0R0FOaTF0SGVKMWpJbkZQcXBhCi91U2ljQ0YyR1VoYmloYTNOQzF5anp6WG5OcjMxQ1hteitrSTJlalBPTkJ2STdJNFVPeERUM1FER3E0cTJCUXgKS2ZvNnl4L1hYSFhSZmNvbzZwc2llUDczc3hqd0xxZm1HOVlBZVNoRW1iQ01hOXgvTkxTSXBGQTBrSW95cW40YgpKcHRuKzZIcjYycnp1THc0amVOTjdTVTJHZ3NVUUVhMFFiL21SNDc4WkVSU0o3R2I2OXQ2Vld2dkhxY2ErRktSClNvVDFmUUlEQVFBQm8wZ3dSakFPQmdOVkhROEJBZjhFQkFNQ0JhQXdFd1lEVlIwbEJBd3dDZ1lJS3dZQkJRVUgKQXdJd0h3WURWUjBqQkJnd0ZvQVVNUUlPYWZudWE0aXRMcUdNT2xqN3p3SVhydGt3RFFZSktvWklodmNOQVFFTApCUUFEZ2dFQkFFTDFOd1V6WFg0RC9FRXZ6MndocWlhaGZwbmlsOWFyZCthWW1qOGdqZXUrZ01QeU1hdmcvV1VvCm5rSmMrODlJcVdBR1M1ZVQwUHlESzU1Mjl5Y1JmS1VsQnZ3YU9RTkwwMllsZUUwK0NISC9VMzl0V3RlQXUzSmIKbGoya3htdkhRZzlHOVNqQXl2YkM3RmpoVHF6Y1d2Yk8waGc3SHEvZWU5L3VXNTVEL29FVm5KUXdSYjZ6V2ZyZAozWmtWcS9Hb2tBV2pnaXRSZk0wZ0dZY3N2ZjZGT1g4ZmN0MVIvL1VLUk1mNzFjNENPeUhZQzFMQ3JUWEN4REJaCld5cFVERDk5SGczTmJOVWpzZDQrZjk4RGJxUENaOUJCb3BpZ2lUVjlJSkNuZVRPcmlKYzZwTzk1TzBDVDdTcHcKcndyVnF5RExLNnZ4M0pVYVRuMllmbjhLS2RrSlR0Yz0KLS0tLS1FTkQgQ0VSVElGSUNBVEUtLS0tLQo=";
    private String keyData = "LS0tLS1CRUdJTiBSU0EgUFJJVkFURSBLRVktLS0tLQpNSUlFcEFJQkFBS0NBUUVBeGE1ZDVlYnhkUnBDdjNQZUZ2REtOR3Y0UEhCK2RrZXE3N0RUU1J4NU9MSW9ZWi93CnpOOGFITGcwc1NOMi8yellQcmNNQnUzV3N2YmVhbm9mOERxenRZdzlCb3VXdUlJSFFoczk2TG16ZTRPTUViblcKclBkb3B0UHBmQ0djKzRHQU5pMXRIZUoxakluRlBxcGEvdVNpY0NGMkdVaGJpaGEzTkMxeWp6elhuTnIzMUNYbQp6K2tJMmVqUE9OQnZJN0k0VU94RFQzUURHcTRxMkJReEtmbzZ5eC9YWEhYUmZjb282cHNpZVA3M3N4andMcWZtCkc5WUFlU2hFbWJDTWE5eC9OTFNJcEZBMGtJb3lxbjRiSnB0bis2SHI2MnJ6dUx3NGplTk43U1UyR2dzVVFFYTAKUWIvbVI0NzhaRVJTSjdHYjY5dDZWV3Z2SHFjYStGS1JTb1QxZlFJREFRQUJBb0lCQUFvTUljT2txRk1lWVpRZgpqSU0zKzk4TU9kTEFtUHprQ2FFaktLb0hvM1dwUUhvdHlleHZ6b0QxTCtCdEFBaGRmT1E3STRaYW9sWDRURGUzCk8xWlhkWGpkeHVCRlNnVFl2d3V5SE1SdVk5djhSS3RGK2M1U3lPUHAwMjAydkNiZ1h5Ymh5akVCcUozYkJzWjMKbkNabWNvY21mRDB1dTFCYWRUZFd0QUxwd2IrOEhvb2FXcFZyd1pGTkk1SyttaXRxOVVxTHZ1Vkh1TE9TTkkyNApPNW1vVTJ2S0VZYXFiMmRKVGFwUzlOL0ZYZDNXMGdBWDFHdy90MW5Hd1NleXB2T0h6eDNFSkpyaFptVkJRQ1dXCk9yRWttZnBpZmFnU1QyVFkwVWhpYUR4eGhrL2IxNGhFM01uOWxEWTBBSVFLeGxWZGRBUmFvUW5wa0ZQcklVQW0KUnVKK0t6VUNnWUVBMnN0N05vcnRZWVh2Mit4WElOZ2E4dmp5VFBnN0RUSk9HaEt0b2g4TUFiL1FSbm5wOHdMTwprRXhHYVJBcFIzSmIwNmh6N3NROUtrVWJKVVlqSjdNY3FwaVNPUkRsTGgwaW51UlFVdEpGaG1DSlJkbEtFeUFGCmRIZm1PaVJrb0V0clQyQWRnYTdMNDZIdHhOdFNZM01aZlJiTnR3WHE5QWFYRG9oTmkyVk9EQThDZ1lFQTUwdkUKc0hERGVYRWZZdzJZRGRBN0VaVVNoWVJ4SkdHOERNZk1yRUZzOHQ0bzlndURMZVJMaHgvYjBXaXBtaTRXV2R3MwpKNFlCaXRsSFlnQXVSb0VNZXh1ZFUwTldCZHpNWU9qUG5YNUdiS0tyTktzUjdwSmxTbnpYd1dpTXVraUFuUHJnCkxwYkVmOGdlNVAyV040MVduSlJ5YVVYdGV0MWpXRmovOHJNYUNiTUNnWUVBdVN4QjZaV0c5bVRPN2dpR2JRdTUKTXk0eWs2WDdCRjR3NzZ3ak8vU2V5dFEyUjQ5aXl3THpJL2tLRmwxUk1tQzlDdE9rMnMxSEh3RkJ5amdrQkxONApiWUdYTDZqMjdpSkdiTWU2bTMzT3piM2lNRFdJbGNzaUVzSnZIWUl6ajk2RXdiY05BZmFZSk4ybFNGaCswQ0JYCjBDQitsb254b2ZuNTJwak5XRTZ5MFUwQ2dZQTZ1Z0kyeW1yWGF1R05ST1pXbTRoajduWEZjTnRKWVlkZHQxMUEKWDF4S1RoNFlXdFB6MHdOYVM3eUVidzZoRnhLVnZsUkN0TU92Zlh1aVptbFpmV3F0MTFVYXp2NElmd2RsazdjagpSZXlicUxIUHJaL2Y4MHZFbmU0cUxXR042dHE5QVBYcTNsMEdGTW5EV3AvSmV4bUNxQzVrakZ5LzFYWmorRFVFCmFuVXlBUUtCZ1FDZjFMWTcveEhlOG9KYzJ4cnN0bm5tY3BOSzdZVTgvQzdobmRXdFptY2MxYkxnbGZuRnJvcHQKQ1BvdHJqNU9KWktJN2kxMkIrTmMzdE9EZmc5ZkJ5Lzg4Zk1yTzJ2NEF6NWdBTklHRmdXZFM3elM1QjJOTDJrWgo0TXBoZ1NFNVdudnp3Vm1MQVFlZS9FcE1LeHpqdlFiKytaOG5IUm01QURxODgxWnRzd2FkYVE9PQotLS0tLUVORCBSU0EgUFJJVkFURSBLRVktLS0tLQo=";

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

