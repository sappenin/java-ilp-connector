package org.interledger.connector.server.spring.settings.conditionals;

import org.interledger.connector.core.ConfigConstants;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;

import static org.interledger.connector.core.ConfigConstants.BTP_ENABLED;

/**
 * A Spring conditional that triggers when BTP is enabled via {@link ConfigConstants#BTP_ENABLED}.
 *
 * @deprecated Just use @ConditionalOnProperty intstead?
 */
@ConditionalOnProperty(BTP_ENABLED)
@Deprecated
public class BtpEnabledCondition implements Condition {

  /**
   * Determine if the condition matches.
   *
   * @param context  the condition context
   * @param metadata metadata of the {@link AnnotationMetadata class} or {@link MethodMetadata method} being checked
   *
   * @return {@code true} if the condition matches and the component can be registered, or {@code false} to veto the
   * annotated component's registration
   */
  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    return Boolean.TRUE.toString().equals(context.getEnvironment().getProperty(BTP_ENABLED));
  }
}
