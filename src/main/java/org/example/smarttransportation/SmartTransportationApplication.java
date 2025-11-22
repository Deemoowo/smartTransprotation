package org.example.smarttransportation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

@SpringBootApplication
public class SmartTransportationApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext application = SpringApplication.run(SmartTransportationApplication.class, args);
        Environment env = application.getEnvironment();
        String port = env.getProperty("server.port");
        System.out.println("==============AiApplication启动成功，服务端口为：" + port);
    }

}
