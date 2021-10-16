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
public class Periods {

    @Setter
    @Builder
    public static class PeriodProducer {

        //TODO provide scheduler as constructor arg

        private final boolean initiallyAuthorized;
        private final long authorizedTimeInMillis;
        private final long unauthorizedTimeInMillis;

        private final LocalTime periodStartTime;
        private final LocalTime currentTime;
        private PeriodListener listener;

        void startScheduling() {
            try {
                var nextReferenceTime = currentTime.isBefore(periodStartTime)
                        //if current time is before reference, the next reference is on the same day (today)
                        ? LocalDate.now().atTime(periodStartTime)
                        //if referenceTime already passed today, the next reference is tomorrow
                        : LocalDate.now().plusDays(1L).atTime(periodStartTime);
                var todayAtCurrentTime = LocalDate.now().atTime(currentTime);
                var delayToNextReferenceTime = Duration.ofSeconds(todayAtCurrentTime.until(nextReferenceTime, ChronoUnit.SECONDS));

                var loopDurationInMillis = authorizedTimeInMillis + unauthorizedTimeInMillis;

                //     0, 30, 35, 65, 70, 100, 105, 135, 140
                // loopDuration= authorizedTime + unauthorizedTime => 30 + 5 = 35
                // offsetAuthorized=

                // s0: 0                                        (       once after offset of 0min)
                // p1:  , 30    , 65    , 100      , 135        (every 35min after offset of 30min)
                // p2:      , 35,   , 70,    , 105,    , 140    (every 35min after offset of 35min)

                var delayToFirstPeriod =
                        delayToNextReferenceTime.plus(Duration.ofMillis(initiallyAuthorized ? authorizedTimeInMillis : unauthorizedTimeInMillis));
                var delayToSecondPeriod = delayToNextReferenceTime.plus(Duration.ofMillis(loopDurationInMillis));

                initializeScheduler(delayToFirstPeriod, delayToSecondPeriod, loopDurationInMillis);

                //emit first result immediately
                listener.publishCurrentPeriodValue(initiallyAuthorized);
            } catch (Exception e) {
                listener.processError(e);
            }
        }

        private void initializeScheduler(Duration delayToFirstPeriod, Duration delayToSecondPeriod, long loopDurationInMillis) {
            var firstPeriodScheduler = Schedulers.single().schedulePeriodically(
                    () -> listener.publishCurrentPeriodValue(initiallyAuthorized),
                    delayToFirstPeriod.toMillis(), loopDurationInMillis, TimeUnit.MILLISECONDS);

            var secondPeriodScheduler = Schedulers.single().schedulePeriodically(
                    () -> listener.publishCurrentPeriodValue(!initiallyAuthorized),
                    delayToSecondPeriod.toMillis(), loopDurationInMillis, TimeUnit.MILLISECONDS);

            if (firstPeriodScheduler.isDisposed() && secondPeriodScheduler.isDisposed())
                listener.schedulerDisposed();
        }
    }


    @Value
    public static class PeriodListener {
        FluxSink<Val> sink;

        void publishCurrentPeriodValue(boolean periodValue) {
            var currentTime = LocalTime.now();
            log.debug("new period starting at {} - authorized: {}", currentTime, periodValue);
            sink.next(Val.of(periodValue));
        }

        void schedulerDisposed() {
            sink.complete();
        }

        void processError(Throwable e) {
            sink.error(e);
        }
    }

}