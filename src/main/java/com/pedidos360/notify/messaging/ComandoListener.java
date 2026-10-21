package com.pedidos360.notify.messaging;

import java.io.IOException;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.pedidos360.notify.processing.EmailProcessor;
import com.pedidos360.notify.processing.InvoiceProcessor;
import com.pedidos360.notify.processing.KitchenTicketProcessor;
import com.rabbitmq.client.Channel;

import lombok.RequiredArgsConstructor;

/** Un @RabbitListener por cola de comandos. Reciben el Message crudo para controlar ACK/NACK. */
@Component
@RequiredArgsConstructor
public class ComandoListener {

    private final ConsumidorComandos consumidor;
    private final EmailProcessor email;
    private final KitchenTicketProcessor kitchen;
    private final InvoiceProcessor invoice;

    @RabbitListener(id = "email", queues = "${pedidos360.rabbit.queues.email}")
    public void onEmail(Message message, Channel channel) throws IOException {
        consumidor.consumir(message, channel, email);
    }

    @RabbitListener(id = "kitchen", queues = "${pedidos360.rabbit.queues.kitchen}")
    public void onKitchen(Message message, Channel channel) throws IOException {
        consumidor.consumir(message, channel, kitchen);
    }

    @RabbitListener(id = "invoice", queues = "${pedidos360.rabbit.queues.invoice}")
    public void onInvoice(Message message, Channel channel) throws IOException {
        consumidor.consumir(message, channel, invoice);
    }
}
