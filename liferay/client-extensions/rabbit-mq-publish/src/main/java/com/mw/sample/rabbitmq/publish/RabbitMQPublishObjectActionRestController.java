package com.mw.sample.rabbitmq.publish;

import com.liferay.client.extension.util.spring.boot3.BaseRestController;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/object/action/rabbit-mq-publish")
@RestController
public class RabbitMQPublishObjectActionRestController extends BaseRestController {

	@PostMapping
	public ResponseEntity<String> post(
		@AuthenticationPrincipal Jwt jwt, @RequestBody String json) {

		log(jwt, _log, json);

		_log.info("rabbitMqDemoQueueName: " + rabbitMqDemoQueueName);

		rabbitTemplate.send(rabbitMqDemoQueueName, new Message(json.getBytes()));
		
		_log.info("############################# RabbitMQ MESSAGE SENT #############################");

		return new ResponseEntity<>(json, HttpStatus.OK);
	}
	
	@Value("${RABBIT_MQ_DEMO_QUEUE_NAME}")
	private String rabbitMqDemoQueueName;

    private final RabbitTemplate rabbitTemplate;

    public RabbitMQPublishObjectActionRestController(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }	

	private static final Log _log = LogFactory.getLog(RabbitMQPublishObjectActionRestController.class);
}