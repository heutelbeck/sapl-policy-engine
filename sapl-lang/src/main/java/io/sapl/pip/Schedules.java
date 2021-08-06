package io.sapl.pip;

import io.sapl.api.interpreter.Val;
import lombok.Builder;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

@Slf4j
@UtilityClass
public class Schedules {

    @Setter
    @Builder
    public static class ScheduleProducer {

        private static long secondsInOneDay = Duration.ofDays(1L).getSeconds();

        private final LocalTime referenceTime;
        private final LocalTime currentTime;
        private ScheduleListener listener;

        void startScheduling() {
            try {
                var nextMidnight = LocalDate.now().plusDays(1L).atStartOfDay();
                var nextReferenceTime = currentTime.isBefore(referenceTime)
                        //if current time is before reference, the next reference is on the same day (today)
                        ? LocalDate.now().atTime(referenceTime)
                        //if referenceTime already passed today, the next reference is tomorrow
                        : LocalDate.now().plusDays(1L).atTime(referenceTime);

                var todayAtCurrentTime = LocalDate.now().atTime(currentTime);

                var delayToNextMidnight = Duration.ofSeconds(todayAtCurrentTime.until(nextMidnight, ChronoUnit.SECONDS));
                log.trace("scheduling next Midnight ({}) with a delay of {}", nextMidnight, delayToNextMidnight);
                var delayToNextReferenceTime = Duration.ofSeconds(todayAtCurrentTime.until(nextReferenceTime, ChronoUnit.SECONDS));
                log.trace("scheduling next Reference-Time  ({}) with a delay of {}", nextReferenceTime, delayToNextReferenceTime);

                initializeScheduler(delayToNextMidnight, delayToNextReferenceTime);
            } catch (Exception e) {
                e.printStackTrace();
                listener.processError(e);
            }
        }

        private void initializeScheduler(Duration delayToNextMidnight, Duration delayToNextReferenceTime) {
            //schedule onMidnight at every start of the day, beginning after an initial delay
            var midnightScheduler = Schedulers.single().schedulePeriodically(
                    () -> listener.processResultChange(referenceTime),
                    delayToNextMidnight.getSeconds(), secondsInOneDay, TimeUnit.SECONDS);

            //schedule onReferenceTime
            var referenceScheduler = Schedulers.single().schedulePeriodically(
                    () -> listener.processResultChange(referenceTime),
                    delayToNextReferenceTime.getSeconds(), secondsInOneDay, TimeUnit.SECONDS);

            if (midnightScheduler.isDisposed() && referenceScheduler.isDisposed())
                listener.schedulerDisposed();
        }
    }

    @Value
    public static class ScheduleListener {
        FluxSink<Val> sink;

        void processResultChange(LocalTime localTimeRef) {
            var currentTime = LocalTime.now();
            log.trace("{} is after {} -> {}", currentTime, localTimeRef, currentTime.isAfter(localTimeRef));
            sink.next(Val.of(currentTime.isAfter(localTimeRef)));
        }

        void schedulerDisposed() {
            sink.complete();
        }

        void processError(Throwable e) {
            sink.error(e);
        }
    }

}