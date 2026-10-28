package com.pedidos360.notify.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declara la MISMA topología que ms-pedidos360-orders (nombres, tipos y argumentos idénticos), para
 * que notify pueda arrancar antes que orders. Si un argumento difiere, RabbitMQ rechaza la declaración
 * con PRECONDITION_FAILED. Con RABBIT_DECLARE_TOPOLOGY=false se asume que otro la declaró.
 * Las colas van sin argumentos: el DLX lo aplican las policies policy.*.dlx del broker.
 */
@Configuration
@ConditionalOnProperty(name = "pedidos360.rabbit.declare-topology", havingValue = "true", matchIfMissing = true)
public class RabbitTopologyConfig {

    @Bean
    public Declarables topologiaComandos(RabbitProperties props) {
        RabbitProperties.Exchanges ex = props.exchanges();
        RabbitProperties.Destinos colas = props.queues();
        RabbitProperties.Destinos rk = props.routingKeys();
        RabbitProperties.Destinos patrones = props.topicPatterns();

        DirectExchange direct = new DirectExchange(ex.direct(), true, false);
        TopicExchange topic = new TopicExchange(ex.topic(), true, false);
        DirectExchange dlx = new DirectExchange(ex.dlx(), true, false);

        Queue email = QueueBuilder.durable(colas.email()).build();
        Queue kitchen = QueueBuilder.durable(colas.kitchen()).build();
        Queue invoice = QueueBuilder.durable(colas.invoice()).build();
        Queue emailDlq = QueueBuilder.durable(RabbitProperties.dlq(colas.email())).build();
        Queue kitchenDlq = QueueBuilder.durable(RabbitProperties.dlq(colas.kitchen())).build();
        Queue invoiceDlq = QueueBuilder.durable(RabbitProperties.dlq(colas.invoice())).build();
        RabbitProperties.Resultados res = props.resultados();
        DirectExchange eventos = new DirectExchange(res.exchange(), true, false);
        Queue resultados = QueueBuilder.durable(res.queue()).build();

        return new Declarables(
                direct, topic, dlx, eventos,
                email, kitchen, invoice, emailDlq, kitchenDlq, invoiceDlq, resultados,
                // evt.direct: resultados de notify hacia orders
                bind(resultados, eventos, res.routingKey()),
                // cmd.direct: routing key exacta
                bind(email, direct, rk.email()),
                bind(kitchen, direct, rk.kitchen()),
                bind(invoice, direct, rk.invoice()),
                // cmd.topic: patrones
                bind(email, topic, patrones.email()),
                bind(kitchen, topic, patrones.kitchen()),
                bind(invoice, topic, patrones.invoice()),
                // cmd.dead.dlx -> DLQs (con la dead-letter-routing-key de la policy de cada cola)
                bind(emailDlq, dlx, rk.email()),
                bind(kitchenDlq, dlx, rk.kitchen()),
                bind(invoiceDlq, dlx, rk.invoice()));
    }

    private static Binding bind(Queue queue, DirectExchange exchange, String routingKey) {
        return BindingBuilder.bind(queue).to(exchange).with(routingKey);
    }

    private static Binding bind(Queue queue, TopicExchange exchange, String pattern) {
        return BindingBuilder.bind(queue).to(exchange).with(pattern);
    }
}
