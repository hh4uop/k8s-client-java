package cn.k8s.webssh.config;

import cn.k8s.webssh.controller.ShellWebSocketHandler;
import cn.k8s.webssh.controller.SpringWebSocketHandlerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new ShellWebSocketHandler(), "/container/terminal/shell/ws")
                .setAllowedOrigins()
                .addInterceptors(new SpringWebSocketHandlerInterceptor())
                .setAllowedOrigins("*"); // 添加允许跨域访问
              //  .withSockJS();
    }

    @Bean
    public ShellWebSocketHandler webSocketHandler(){
        return new ShellWebSocketHandler();
    }

}
