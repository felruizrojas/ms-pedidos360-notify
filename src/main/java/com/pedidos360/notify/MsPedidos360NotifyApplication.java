package com.pedidos360.notify;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MsPedidos360NotifyApplication {

	public static void main(String[] args) {
		SpringApplication.run(MsPedidos360NotifyApplication.class, args);
	}

}
