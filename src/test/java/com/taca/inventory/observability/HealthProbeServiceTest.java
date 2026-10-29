package com.taca.inventory.observability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HealthProbeService Unit Tests")
class HealthProbeServiceTest {

    @Test
    @DisplayName("I-API-15: isKafkaReady trả về false (không ném lỗi) khi context class loader của luồng không thấy thư viện Kafka")
    void kafkaReadinessShouldNotFailWhenContextClassLoaderCannotSeeKafkaClasses() throws Exception {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.kafka.bootstrap-servers", "localhost:9998");
        HealthProbeService service = new HealthProbeService(null, environment);

        AtomicReference<Boolean> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        // Mô phỏng luồng worker trong jar đóng gói Spring Boot: context class loader không thấy BOOT-INF/lib.
        Thread worker = new Thread(() -> {
            try {
                result.set(service.isKafkaReady());
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        worker.setContextClassLoader(new URLClassLoader(new URL[0], null));
        worker.start();
        worker.join();

        assertThat(failure.get()).isNull();
        assertThat(result.get()).isFalse();
    }

    @Test
    @DisplayName("I-API-15: isKafkaReady khôi phục context class loader ban đầu sau khi kiểm tra")
    void kafkaReadinessShouldRestoreContextClassLoader() throws Exception {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.kafka.bootstrap-servers", "localhost:9998");
        HealthProbeService service = new HealthProbeService(null, environment);
        ClassLoader original = new URLClassLoader(new URL[0], null);

        AtomicReference<ClassLoader> after = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            service.isKafkaReady();
            after.set(Thread.currentThread().getContextClassLoader());
        });
        worker.setContextClassLoader(original);
        worker.start();
        worker.join();

        assertThat(after.get()).isSameAs(original);
    }
}
