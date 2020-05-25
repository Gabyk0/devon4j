package com.devonfw.module.security.jwt.common.base.kafka;

import java.nio.charset.StandardCharsets;

import javax.inject.Inject;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;

import com.devonfw.module.security.jwt.common.api.JwtAuthenticator;
import com.devonfw.module.security.jwt.common.base.JwtConstants;

/**
 *
 */
@Aspect
@Order(100)
public class JwtTokenValidationAspect {
  private final static Logger LOG = LoggerFactory.getLogger(JwtTokenValidationAspect.class);

  @Inject
  private JwtAuthenticator jwtAuthenticator;

  /**
   * This method is used to validate the token present in the headers of {@link ConsumerRecord}. It looks for the method
   * annotated {@link JwtAuthentication} and {@link KafkaListener} and validates the token in the
   * {@link ConsumerRecord#headers()}
   *
   * @param <K> the key type
   * @param <V> the value type
   * @param call the {@link ProceedingJoinPoint}
   * @param kafkaRecord the {@link ConsumerRecord}
   * @param acknowledgment the {@link Acknowledgment}
   * @return Object
   * @throws Throwable the {@link Throwable}
   */
  @Around("@annotation(com.devonfw.module.security.jwt.common.base.kafka.JwtAuthentication) && args(kafkaRecord,..)")
  public <K, V> Object authenticateToken(ProceedingJoinPoint call, ConsumerRecord<K, V> kafkaRecord) throws Throwable {

    try {
      authenticateToken(kafkaRecord);
      return call.proceed();
    } catch (AuthenticationException ex) {
      LOG.warn("Message with invalid token received (Topic: {}, Partition: {}, Offset: {}).", ex, kafkaRecord.topic(),
          kafkaRecord.partition(), kafkaRecord.offset());
      for (Object arg : call.getArgs()) {
        if (arg instanceof Acknowledgment && arg != null) {
          LOG.debug("Acknowledging message with invalid token.");
          ((Acknowledgment) arg).acknowledge();
        }
      }
      return null;
    }

  }

  private <K, V> void authenticateToken(ConsumerRecord<K, V> kafkaRecord) {

    Headers headers = kafkaRecord.headers();

    Header authorizationHeader = headers.lastHeader(JwtConstants.HEADER_AUTHORIZATION);

    if (authorizationHeader != null && authorizationHeader.value() != null) {
      Authentication authentication = this.jwtAuthenticator
          .authenticate(new String(authorizationHeader.value(), StandardCharsets.UTF_8));
      SecurityContextHolder.getContext().setAuthentication(authentication);
    }

  }

}