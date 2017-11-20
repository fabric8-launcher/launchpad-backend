package io.fabric8.launcher.service.openshift.impl.fabric8.openshift.client;

import java.util.function.Function;

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
    public static RuntimeException throwMappedException(KubernetesClientException kce, String context) {
        for (ExceptionMapping exceptionMapping : ExceptionMapping.values()) {
            if (exceptionMapping.isMatchingException(kce)) {
                return exceptionMapping.createInstance.apply(context);
            }
        }

        return kce;
    }

    private enum ExceptionMapping {
        DUPLICATE(409, "AlreadyExists", DuplicateProjectException::new),
        QUOTA(403, "cannot create more", QuotaExceedException::new);

        private final int statusCode;

        private final String statusReason;

        private final Function<String, RuntimeException> createInstance;

        ExceptionMapping(int statusCode, String statusReason, Function<String, RuntimeException> createInstance) {
            this.statusCode = statusCode;
            this.statusReason = statusReason;
            this.createInstance = createInstance;
        }

        private boolean isMatchingException(KubernetesClientException kce) {
            return kce.getCode() == statusCode && kce.getStatus().getReason().contains(statusReason);
        }
    }
}
