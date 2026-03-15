package com.weekly.notification;

import com.weekly.outbox.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Verifies that the notification materializer is opt-in so it can run from a
 * dedicated worker profile instead of every API instance.
 */
class NotificationMaterializerConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withBean(OutboxEventRepository.class, () -> mock(OutboxEventRepository.class))
            .withBean(NotificationRepository.class, () -> mock(NotificationRepository.class));

    @Test
    void isDisabledByDefault() {
        contextRunner.run(context ->
                assertThat(context).doesNotHaveBean(NotificationMaterializer.class));
    }

    @Test
    void isEnabledWhenPropertyIsTrue() {
        contextRunner
                .withPropertyValues("notification.materializer.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(NotificationMaterializer.class));
    }

    @Configuration(proxyBeanMethods = false)
    @Import(NotificationMaterializer.class)
    static class TestConfig {
    }
}
