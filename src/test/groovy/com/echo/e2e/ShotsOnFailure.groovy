package com.echo.e2e

import java.lang.annotation.ElementType
import java.lang.annotation.Inherited
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

import org.spockframework.runtime.extension.ExtensionAnnotation
import org.spockframework.runtime.extension.IAnnotationDrivenExtension
import org.spockframework.runtime.extension.IMethodInterceptor
import org.spockframework.runtime.extension.IMethodInvocation
import org.spockframework.runtime.model.FeatureInfo
import org.spockframework.runtime.model.SpecInfo

/**
 * Story 5653: put on an E2E base spec. When a feature fails (an assertion or an exception), the
 * page the browser is showing at that moment is saved as a full-page PNG in target/e2e-shots,
 * named after the spec, the feature and the viewport -- before cleanup() closes the browser.
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@ExtensionAnnotation(ShotsOnFailureExtension)
@interface ShotsOnFailure {}

/** Story 5653: wraps every feature method of the running spec in a try / screenshot / rethrow. */
class ShotsOnFailureExtension implements IAnnotationDrivenExtension<ShotsOnFailure> {

    /** The annotation is inherited, so a spec hierarchy may visit it more than once: wrap each feature once. */
    private static final Set<FeatureInfo> WRAPPED = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<FeatureInfo, Boolean>()))

    @Override
    void visitSpecAnnotation(ShotsOnFailure annotation, SpecInfo spec) {
        for (FeatureInfo feature : spec.bottomSpec.allFeatures) {
            if (!WRAPPED.add(feature)) continue
            feature.featureMethod.addInterceptor(new IMethodInterceptor() {
                @Override
                void intercept(IMethodInvocation invocation) throws Throwable {
                    try {
                        invocation.proceed()
                    } catch (Throwable t) {
                        Object instance = invocation.instance
                        if (instance instanceof EchoE2EBaseSpec) {
                            String name = invocation.iteration?.displayName ?: invocation.feature.name
                            ((EchoE2EBaseSpec) instance).shoot(name)
                        }
                        throw t
                    }
                }
            })
        }
    }
}
