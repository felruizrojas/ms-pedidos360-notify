package com.pedidos360.notify;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

/** Contexto sin RabbitMQ: no se declara la topología y los listeners no arrancan. */
@SpringBootTest
@ActiveProfiles("test")
class MsPedidos360NotifyApplicationTests {

	@Autowired
	ApplicationContext context;

	@Autowired
	RabbitListenerEndpointRegistry registry;

	@Test
	void contextLoads() {
		assertThat(context.getBeansOfType(Declarables.class)).isEmpty();
		assertThat(registry.getListenerContainerIds())
				.containsExactlyInAnyOrder("email", "kitchen", "invoice", "email-dlq", "kitchen-dlq", "invoice-dlq");
		assertThat(registry.getListenerContainers()).noneMatch(c -> c.isRunning());
	}

}
