package org.springframework.metrics.instrument.scheduling;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.metrics.boot.EnableMetrics;
import org.springframework.metrics.instrument.LongTaskTimer;
import org.springframework.metrics.instrument.MeterRegistry;
import org.springframework.metrics.instrument.Timer;
import org.springframework.metrics.instrument.annotation.Timed;
import org.springframework.metrics.instrument.simple.SimpleMeterRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@SpringBootTest
class MetricsSchedulingAspectTest {

    static CountDownLatch observeLongTaskLatch = new CountDownLatch(1);

    @Autowired
    MeterRegistry registry;

    @Test
    void scheduledIsInstrumented() {
        assertThat(registry.findMeter(Timer.class, "beeper"))
                .hasValueSatisfying(t -> assertThat(t.count()).isEqualTo(1));

        assertThat(registry.findMeter(LongTaskTimer.class, "longBeep"))
                .hasValueSatisfying(t -> assertThat(t.activeTasks()).isEqualTo(1));

        // make sure longBeep continues running until we have a chance to observe it in the active state
        observeLongTaskLatch.countDown();

        // now the long beeper has contributed to the beep count as well
        assertThat(registry.findMeter(Timer.class, "beeper"))
                .hasValueSatisfying(t -> assertThat(t.count()).isEqualTo(2));
    }

    @SpringBootApplication
    @EnableMetrics
    @EnableScheduling
    static class MetricsApp {
        @Bean
        MeterRegistry registry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        TaskScheduler scheduler() {
            ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
            // this way, executing longBeep doesn't block the short tasks from running
            scheduler.setPoolSize(5);
            return scheduler;
        }

        @Timed("beeper")
        @Timed(value = "longBeep", longTask = true)
        @Scheduled(fixedRate = 1000)
        void longBeep() throws InterruptedException {
            observeLongTaskLatch.await();
            System.out.println("beep");
        }

        @Timed("beeper")
        @Scheduled(fixedRate = 1000)
        void beep1() {
            System.out.println("beep");
        }

        @Timed // not instrumented because @Timed lacks a metric name
        @Scheduled(fixedRate = 1000)
        void beep2() {
            System.out.println("beep");
        }

        @Scheduled(fixedRate = 1000) // not instrumented because it isn't @Timed
        void beep3() {
            System.out.println("beep");
        }
    }
}
