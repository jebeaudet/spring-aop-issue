package com.jebeaudet.demo.aspect;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.*;
import io.micrometer.core.lang.Nullable;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Predicate;

public class TimedAspect1_9_15 {
    private static final Predicate<ProceedingJoinPoint> DONT_SKIP_ANYTHING = pjp -> false;

    public static final String DEFAULT_METRIC_NAME = "method.timed";

    public static final String DEFAULT_EXCEPTION_TAG_VALUE = "none";

    /**
     * Tag key for an exception.
     *
     * @since 1.1.0
     */
    public static final String EXCEPTION_TAG = "exception";

    private final MeterRegistry registry;

    private final Function<ProceedingJoinPoint, Iterable<Tag>> tagsBasedOnJoinPoint;

    private final Predicate<ProceedingJoinPoint> shouldSkip;

    /**
     * Creates a {@code TimedAspect} instance with {@link Metrics#globalRegistry}.
     *
     * @since 1.2.0
     */
    public TimedAspect1_9_15() {
        this(Metrics.globalRegistry);
    }

    /**
     * Creates a {@code TimedAspect} instance with the given {@code registry}.
     * @param registry Where we're going to register metrics.
     */
    public TimedAspect1_9_15(MeterRegistry registry) {
        this(registry, DONT_SKIP_ANYTHING);
    }

    /**
     * Creates a {@code TimedAspect} instance with the given {@code registry} and tags
     * provider function.
     * @param registry Where we're going to register metrics.
     * @param tagsBasedOnJoinPoint A function to generate tags given a join point.
     */
    public TimedAspect1_9_15(MeterRegistry registry, Function<ProceedingJoinPoint, Iterable<Tag>> tagsBasedOnJoinPoint) {
        this(registry, tagsBasedOnJoinPoint, DONT_SKIP_ANYTHING);
    }

    /**
     * Creates a {@code TimedAspect} instance with the given {@code registry} and skip
     * predicate.
     * @param registry Where we're going to register metrics.
     * @param shouldSkip A predicate to decide if creating the timer should be skipped or
     * not.
     * @since 1.7.0
     */
    public TimedAspect1_9_15(MeterRegistry registry, Predicate<ProceedingJoinPoint> shouldSkip) {
        this(registry, pjp -> Tags.of("class", pjp.getStaticPart().getSignature().getDeclaringTypeName(), "method",
                pjp.getStaticPart().getSignature().getName()), shouldSkip);
    }

    /**
     * Creates a {@code TimedAspect} instance with the given {@code registry}, tags
     * provider function and skip predicate.
     * @param registry Where we're going to register metrics.
     * @param tagsBasedOnJoinPoint A function to generate tags given a join point.
     * @param shouldSkip A predicate to decide if creating the timer should be skipped or
     * not.
     * @since 1.7.0
     */
    public TimedAspect1_9_15(MeterRegistry registry, Function<ProceedingJoinPoint, Iterable<Tag>> tagsBasedOnJoinPoint,
                             Predicate<ProceedingJoinPoint> shouldSkip) {
        this.registry = registry;
        this.tagsBasedOnJoinPoint = tagsBasedOnJoinPoint;
        this.shouldSkip = shouldSkip;
    }

    @Around("@within(io.micrometer.core.annotation.Timed)")
    @Nullable
    public Object timedClass(ProceedingJoinPoint pjp) throws Throwable {
        if (shouldSkip.test(pjp)) {
            return pjp.proceed();
        }

        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        Class<?> declaringClass = method.getDeclaringClass();
        if (!declaringClass.isAnnotationPresent(Timed.class)) {
            declaringClass = pjp.getTarget().getClass();
        }
        Timed timed = declaringClass.getAnnotation(Timed.class);

        return perform(pjp, timed, method);
    }

    @Around("execution (@io.micrometer.core.annotation.Timed * *.*(..))")
    @Nullable
    public Object timedMethod(ProceedingJoinPoint pjp) throws Throwable {
        if (shouldSkip.test(pjp)) {
            return pjp.proceed();
        }

        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        Timed timed = method.getAnnotation(Timed.class);
        if (timed == null) {
            method = pjp.getTarget().getClass().getMethod(method.getName(), method.getParameterTypes());
            timed = method.getAnnotation(Timed.class);
        }

        return perform(pjp, timed, method);
    }

    private Object perform(ProceedingJoinPoint pjp, Timed timed, Method method) throws Throwable {
        final String metricName = timed.value().isEmpty() ? DEFAULT_METRIC_NAME : timed.value();
        final boolean stopWhenCompleted = CompletionStage.class.isAssignableFrom(method.getReturnType());

        if (!timed.longTask()) {
            return processWithTimer(pjp, timed, metricName, stopWhenCompleted);
        }
        else {
            return processWithLongTaskTimer(pjp, timed, metricName, stopWhenCompleted);
        }
    }

    private Object processWithTimer(ProceedingJoinPoint pjp, Timed timed, String metricName, boolean stopWhenCompleted)
            throws Throwable {

        Timer.Sample sample = Timer.start(registry);

        if (stopWhenCompleted) {
            try {
                return ((CompletionStage<?>) pjp.proceed()).whenComplete(
                        (result, throwable) -> record(pjp, timed, metricName, sample, getExceptionTag(throwable)));
            }
            catch (Exception ex) {
                record(pjp, timed, metricName, sample, ex.getClass().getSimpleName());
                throw ex;
            }
        }

        String exceptionClass = DEFAULT_EXCEPTION_TAG_VALUE;
        try {
            return pjp.proceed();
        }
        catch (Exception ex) {
            exceptionClass = ex.getClass().getSimpleName();
            throw ex;
        }
        finally {
            record(pjp, timed, metricName, sample, exceptionClass);
        }
    }

    private void record(ProceedingJoinPoint pjp, Timed timed, String metricName, Timer.Sample sample,
                        String exceptionClass) {
        try {
            sample.stop(Timer.builder(metricName)
                    .description(timed.description().isEmpty() ? null : timed.description())
                    .tags(timed.extraTags())
                    .tags(EXCEPTION_TAG, exceptionClass)
                    .tags(tagsBasedOnJoinPoint.apply(pjp))
                    .publishPercentileHistogram(timed.histogram())
                    .publishPercentiles(timed.percentiles().length == 0 ? null : timed.percentiles())
                    .register(registry));
        }
        catch (Exception e) {
            // ignoring on purpose
        }
    }

    private String getExceptionTag(Throwable throwable) {

        if (throwable == null) {
            return DEFAULT_EXCEPTION_TAG_VALUE;
        }

        if (throwable.getCause() == null) {
            return throwable.getClass().getSimpleName();
        }

        return throwable.getCause().getClass().getSimpleName();
    }

    private Object processWithLongTaskTimer(ProceedingJoinPoint pjp, Timed timed, String metricName,
                                            boolean stopWhenCompleted) throws Throwable {

        Optional<LongTaskTimer.Sample> sample = buildLongTaskTimer(pjp, timed, metricName).map(LongTaskTimer::start);

        if (stopWhenCompleted) {
            try {
                return ((CompletionStage<?>) pjp.proceed())
                        .whenComplete((result, throwable) -> sample.ifPresent(this::stopTimer));
            }
            catch (Exception ex) {
                sample.ifPresent(this::stopTimer);
                throw ex;
            }
        }

        try {
            return pjp.proceed();
        }
        finally {
            sample.ifPresent(this::stopTimer);
        }
    }

    private void stopTimer(LongTaskTimer.Sample sample) {
        try {
            sample.stop();
        }
        catch (Exception e) {
            // ignoring on purpose
        }
    }

    /**
     * Secure long task timer creation - it should not disrupt the application flow in
     * case of exception
     */
    private Optional<LongTaskTimer> buildLongTaskTimer(ProceedingJoinPoint pjp, Timed timed, String metricName) {
        try {
            return Optional.of(LongTaskTimer.builder(metricName)
                    .description(timed.description().isEmpty() ? null : timed.description())
                    .tags(timed.extraTags())
                    .tags(tagsBasedOnJoinPoint.apply(pjp))
                    .register(registry));
        }
        catch (Exception e) {
            return Optional.empty();
        }
    }
}
