package io.fabric8.launcher.service.openshift.impl.fabric8.openshift.client;

import java.lang.reflect.InvocationTargetException;

import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.launcher.service.openshift.api.DuplicateProjectException;
import io.fabric8.launcher.service.openshift.api.QuotaExceedException;

/**
 * Util class that 'maps' KubernetesClientException to domain specific RuntimeException.
 */
public class ExceptionMapper {

    /**
     * For given KubernetesClientException map it to a domain specific runtime Exception,
     * if none is found return original KubernetesClientException
     *
     * @param kce     the KubernetesClientException to find a domain specific alternative for
     * @param context is used to create the domain specific exception with, be sure it has a constructor of this type
     * @return the domain specific exception or the original if none is found
     */
    public static RuntimeException throwMappedException(KubernetesClientException kce, Object context) {
        for (ExceptionMapping exceptionMapping : ExceptionMapping.values()) {
            if (isMatchingException(kce, exceptionMapping)) {
                return createException(exceptionMapping.exceptionClass, context);
            }
        }

        return kce;
    }

    private static boolean isMatchingException(KubernetesClientException kce, ExceptionMapping exceptionMapping) {
        return kce.getCode() == exceptionMapping.statusCode
                && kce.getStatus().getReason().contains(exceptionMapping.statusReason);
    }

    private static RuntimeException createException(Class<? extends RuntimeException> exceptionClass, Object context) {
        try {
            return exceptionClass.getConstructor(context.getClass()).newInstance(context);
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException | NoSuchMethodException e) {
            throw new RuntimeException("couldn't instantiate exception, does it have a the right constructor?");
        }
    }

    private enum ExceptionMapping {
        DUPLICATE(409, "AlreadyExists", DuplicateProjectException.class),
        QUOTA(403, "cannot create more", QuotaExceedException.class);

        ExceptionMapping(int statusCode, String statusReason, Class<? extends RuntimeException> exceptionClass) {
            this.statusCode = statusCode;
            this.statusReason = statusReason;
            this.exceptionClass = exceptionClass;
        }

        private final int statusCode;

        private final String statusReason;

        private final Class<? extends RuntimeException> exceptionClass;
    }
}
