package org.grit.daynomy.deployment;

import java.lang.reflect.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.scheduling.SchedulingAwareRunnable;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.TaskManagementConfigUtils;

@Configuration(proxyBeanMethods = false)
@Conditional(DeploymentSchedulingConfig.SlotEnabledCondition.class)
public class DeploymentSchedulingConfig {
  public static class SlotEnabledCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
      return System.getProperty("daynomy.deployment.slot") != null;
    }
  }

  @Bean
  static BeanDefinitionRegistryPostProcessor deploymentScheduledTasks() {
    return new BeanDefinitionRegistryPostProcessor() {
      @Override
      public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
        String name = TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME;
        if (!registry.containsBeanDefinition(name)) {
          throw new IllegalStateException("Deployment ownership requires @EnableScheduling");
        }
        // Decorate the annotated job before Spring's outcome and rescheduling wrappers.
        // Plain timers and TaskScheduler lifecycle remain untouched.
        registry.removeBeanDefinition(name);
        var definition = new RootBeanDefinition(OwnershipScheduledProcessor.class);
        definition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
        registry.registerBeanDefinition(name, definition);
      }

      @Override
      public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {}
    };
  }

  public static class OwnershipScheduledProcessor extends ScheduledAnnotationBeanPostProcessor {
    @Override
    protected Runnable createRunnable(Object target, Method method, String qualifier) {
      return new OwnedRunnable(super.createRunnable(target, method, qualifier));
    }
  }

  private record OwnedRunnable(Runnable delegate) implements SchedulingAwareRunnable {
    private static final Logger LOG = LoggerFactory.getLogger(DeploymentSchedulingConfig.class);

    @Override
    public void run() {
      DeploymentOwnership.wrap(
              () -> {
                LOG.atDebug()
                    .addKeyValue("slot", System.getProperty("daynomy.deployment.slot"))
                    .log("deployment.scheduled.started");
                try {
                  delegate.run();
                } finally {
                  LOG.atDebug()
                      .addKeyValue("slot", System.getProperty("daynomy.deployment.slot"))
                      .log("deployment.scheduled.finished");
                }
              })
          .run();
    }

    @Override
    public String getQualifier() {
      return delegate instanceof SchedulingAwareRunnable aware ? aware.getQualifier() : null;
    }

    @Override
    public boolean isLongLived() {
      return delegate instanceof SchedulingAwareRunnable aware && aware.isLongLived();
    }
  }
}
