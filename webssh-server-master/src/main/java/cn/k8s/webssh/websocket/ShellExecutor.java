package cn.k8s.webssh.websocket;

import cn.k8s.webssh.dto.Pod;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.fabric8.kubernetes.client.Callback;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.fabric8.kubernetes.client.utils.NonBlockingInputStreamPumper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.*;

/**
 * @Author: wangxiaotong03
 * @Date: 2022/2/24 10:57
 */
public class ShellExecutor {
    private static Logger LOG = LoggerFactory.getLogger(ShellExecutor.class);
    private WebSocketSession webSocketSession;
    private ExecutorService executorService;
    private ExecWatch watch;
    private NonBlockingInputStreamPumper pump;
    private Integer pageSize=10240;
    private String[] shells = {"/bin/bash", "/bin/sh"};
    private static Logger LOGGER = LoggerFactory.getLogger(ShellExecutor.class);

    public ShellExecutor(DefaultKubernetesClient client, WebSocketSession webSocketSession, Pod pod) {
        this.webSocketSession = webSocketSession;
        this.executorService = initExecutorService();
        this.watch = connContainer(client, pod.getNamespace(), pod.getName(), pod.getContainer());
        this.pump = initExecCallBack(watch);
    }

    // 发送消息
    public void exec(String cmd) {
        try {
            watch.getInput().write(cmd.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
            TextMessage textMessage = new TextMessage("exec shell cmd error! 请重新连接!");
            try {
                webSocketSession.sendMessage(textMessage);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
    }

    // 改变行和列
    public void resize(int cols, int rows) { watch.resize(cols, rows); }

    public void close() {
        if (this.watch != null) {  watch.close(); }
        if (this.pump != null) { pump.close(); }
        if (webSocketSession != null && webSocketSession.isOpen()) {
            try {
                webSocketSession.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (executorService != null) { executorService.shutdown();}
    }

    // 链接k8
    private ExecWatch connContainer(DefaultKubernetesClient client, String namespaceName, String podName,
                                    String containerName) {
        ExecWatch watch = null;
        for (String shell : shells) {
            watch = client.pods()
                    .inNamespace(namespaceName)
                    .withName(podName)
                    .inContainer(containerName)
                    .writingInput(new PipedOutputStream())
                    .readingOutput(new PipedInputStream(this.pageSize))
                    .readingError(new PipedInputStream(this.pageSize))
                    .withTTY()
                    .exec(shell);
            try {
                byte[] buffer = new byte[819200];
                int read = watch.getOutput().read(buffer);
                if (read > 0) {
//                    LOG.info("连接信息为：" + new String(buffer));
                    webSocketSession.sendMessage(new TextMessage(new String(buffer)));
                    break;
                }
            } catch (Exception e) {
                LOGGER.error(e.getMessage());
            }
            LOGGER.warn(String.format("使用 %s 连接失败，重试下一个方式", shell));
        }
        return watch;
    }

    private NonBlockingInputStreamPumper initExecCallBack(ExecWatch watch) {
        NonBlockingInputStreamPumper pump = new NonBlockingInputStreamPumper(watch.getOutput(), new TerminalOutCallback(webSocketSession));
        executorService.submit(pump);
        return pump;
    }

    private ExecutorService initExecutorService() {
        ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat("shell-executor-pool-%d").build();
        return new ThreadPoolExecutor(2, 500, 2000L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(1024), threadFactory, new ThreadPoolExecutor.AbortPolicy());
    }

    private class TerminalOutCallback implements Callback<byte[]> {
        private WebSocketSession webSocketSession;

        public TerminalOutCallback(WebSocketSession webSocketSession) {
            this.webSocketSession = webSocketSession;
        }

        @Override
        public void call(byte[] data) {
            if (webSocketSession != null && webSocketSession.isOpen()) {
                String dataStr = new String(data);
                TextMessage textMessage;
                try {
                    textMessage = new TextMessage(dataStr);
                    webSocketSession.sendMessage(textMessage);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
