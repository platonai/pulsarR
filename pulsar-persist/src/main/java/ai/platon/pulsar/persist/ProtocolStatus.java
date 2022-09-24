/**
 * Autogenerated by Avro
 * <p>
 * DO NOT EDIT DIRECTLY
 */
package ai.platon.pulsar.persist;

import ai.platon.pulsar.persist.gora.generated.GProtocolStatus;
import ai.platon.pulsar.persist.metadata.ProtocolStatusCodes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * <p>ProtocolStatus class.</p>
 *
 * NOTE: keep consistent with ResourceStatus
 *
 * @author vincent
 * @version $Id: $Id
 */
public class ProtocolStatus implements ProtocolStatusCodes {
    public static final String ARG_HTTP_CODE = "httpCode";
    public static final String ARG_REDIRECT_TO_URL = "redirectTo";
    public static final String ARG_URL = "url";
    public static final String ARG_RETRY_SCOPE = "rsp";
    public static final String ARG_RETRY_REASON = "rrs";

    /**
     * Content was not retrieved yet.
     */
    private static final short NOTFETCHED = 0;
    /**
     * Content was retrieved without errors.
     */
    private static final short SUCCESS = 1;
    /**
     * Content was not retrieved. Any further errors may be indicated in args.
     */
    private static final short FAILED = 2;

    public static final ProtocolStatus STATUS_SUCCESS = new ProtocolStatus(SUCCESS, SUCCESS_OK);
    public static final ProtocolStatus STATUS_NOTMODIFIED = new ProtocolStatus(SUCCESS, NOT_MODIFIED);
    public static final ProtocolStatus STATUS_NOTFETCHED = new ProtocolStatus(NOTFETCHED);

    public static final ProtocolStatus STATUS_PROTO_NOT_FOUND = ProtocolStatus.failed(PROTO_NOT_FOUND);
    public static final ProtocolStatus STATUS_ACCESS_DENIED = ProtocolStatus.failed(UNAUTHORIZED);
    public static final ProtocolStatus STATUS_NOTFOUND = ProtocolStatus.failed(NOT_FOUND);
    // if a task is canceled, we do not save anything, if a task is retry, all the metadata is saved
    public static final ProtocolStatus STATUS_CANCELED = ProtocolStatus.failed(CANCELED);
    public static final ProtocolStatus STATUS_EXCEPTION = ProtocolStatus.failed(EXCEPTION);

    private static final HashMap<Short, String> majorCodes = new HashMap<>();
    private static final HashMap<Integer, String> minorCodes = new HashMap<>();

    static {
        majorCodes.put(NOTFETCHED, "NotFetched");
        majorCodes.put(SUCCESS, "Success");
        majorCodes.put(FAILED, "Failed");

        minorCodes.put(SUCCESS_OK, "OK");
        minorCodes.put(CREATED, "Created");
        minorCodes.put(MOVED_PERMANENTLY, "Moved");
        minorCodes.put(MOVED_TEMPORARILY, "TempMoved");
        minorCodes.put(NOT_MODIFIED, "NotModified");

        minorCodes.put(PROTO_NOT_FOUND, "ProtoNotFound");
        minorCodes.put(UNAUTHORIZED, "AccessDenied");
        minorCodes.put(NOT_FOUND, "NotFound");
        minorCodes.put(PRECONDITION_FAILED, "PreconditionFailed");
        minorCodes.put(REQUEST_TIMEOUT, "RequestTimeout");
        minorCodes.put(GONE, "Gone");

        minorCodes.put(UNKNOWN_HOST, "UnknownHost");
        minorCodes.put(ROBOTS_DENIED, "RobotsDenied");
        minorCodes.put(EXCEPTION, "Exception");
        minorCodes.put(REDIR_EXCEEDED, "RedirExceeded");
        minorCodes.put(WOULD_BLOCK, "WouldBlock");
        minorCodes.put(BLOCKED, "Blocked");

        minorCodes.put(RETRY, "Retry");
        minorCodes.put(CANCELED, "Canceled");
        minorCodes.put(THREAD_TIMEOUT, "ThreadTimeout");
        minorCodes.put(WEB_DRIVER_TIMEOUT, "WebDriverTimeout");
        minorCodes.put(SCRIPT_TIMEOUT, "ScriptTimeout");
    }

    private GProtocolStatus protocolStatus;

    public ProtocolStatus(short majorCode) {
        this.protocolStatus = GProtocolStatus.newBuilder().build();
        setMajorCode(majorCode);
        setMinorCode(-1);
    }

    public ProtocolStatus(short majorCode, int minorCode) {
        this.protocolStatus = GProtocolStatus.newBuilder().build();
        setMajorCode(majorCode);
        setMinorCode(minorCode);
    }

    private ProtocolStatus(GProtocolStatus protocolStatus) {
        Objects.requireNonNull(protocolStatus);
        this.protocolStatus = protocolStatus;
    }

    @Nonnull
    public static ProtocolStatus box(GProtocolStatus protocolStatus) {
        return new ProtocolStatus(protocolStatus);
    }

    public static String getMajorName(int code) {
        return majorCodes.getOrDefault((short)code, "unknown");
    }

    public static String getMinorName(int code) {
        return minorCodes.getOrDefault(code, "unknown");
    }

    @Nonnull
    public static ProtocolStatus retry(RetryScope scope) {
        return failed(ProtocolStatusCodes.RETRY, ARG_RETRY_SCOPE, scope);
    }

    @Nonnull
    public static ProtocolStatus retry(RetryScope scope, Object reason) {
        String reasonString;
        if (reason instanceof Exception) {
            reasonString = ((Exception) reason).getClass().getSimpleName();
        } else {
            reasonString = reason.toString();
        }

        return failed(ProtocolStatusCodes.RETRY,
                ARG_RETRY_SCOPE, scope,
                ARG_RETRY_REASON, reasonString
        );
    }

    @Nonnull
    public static ProtocolStatus cancel(Object... args) {
        return failed(ProtocolStatusCodes.CANCELED, args);
    }

    @Nonnull
    public static ProtocolStatus failed(int minorCode) {
        return new ProtocolStatus(FAILED, minorCode);
    }

    @Nonnull
    public static ProtocolStatus failed(int minorCode, Object... args) {
        ProtocolStatus protocolStatus = new ProtocolStatus(FAILED, minorCode);

        if (args.length % 2 == 0) {
            Map<CharSequence, CharSequence> protocolStatusArgs = protocolStatus.getArgs();
            for (int i = 0; i < args.length - 1; i += 2) {
                if (args[i] != null && args[i + 1] != null) {
                    protocolStatusArgs.put(args[i].toString(), args[i + 1].toString());
                }
            }
        }

        return protocolStatus;
    }

    @Nonnull
    public static ProtocolStatus failed(Throwable e) {
        return failed(EXCEPTION, "error", e.getMessage());
    }

    public static ProtocolStatus fromMinor(int minorCode) {
        if (minorCode == SUCCESS_OK || minorCode == NOT_MODIFIED) {
            return STATUS_SUCCESS;
        } else {
            return failed(minorCode);
        }
    }

    public static boolean isTimeout(ProtocolStatus protocalStatus) {
        int code = protocalStatus.getMinorCode();
        return isTimeout(code);
    }

    public static boolean isTimeout(int code) {
        return code == REQUEST_TIMEOUT || code == THREAD_TIMEOUT || code == WEB_DRIVER_TIMEOUT || code == SCRIPT_TIMEOUT;
    }

    public GProtocolStatus unbox() {
        return protocolStatus;
    }

    public boolean isNotFetched() {
        return getMajorCode() == NOTFETCHED;
    }

    public boolean isSuccess() {
        return getMajorCode() == SUCCESS;
    }

    public boolean isFailed() {
        return getMajorCode() == FAILED;
    }

    /**
     * If a fetch task is canceled, do not update the page status
     * */
    public boolean isCanceled() {
        return getMinorCode() == CANCELED;
    }

    /**
     * the page displays "404 Not Found" or something similar,
     * the server should issue a 404 error code, but not guaranteed
     * */
    public boolean isNotFound() {
        return getMinorCode() == NOT_FOUND;
    }

    public boolean isGone() {
        return getMinorCode() == GONE;
    }

    public boolean isRetry() {
        return getMinorCode() == RETRY;
    }

    public boolean isRetry(RetryScope scope) {
        RetryScope defaultScope = RetryScope.CRAWL;
        return getMinorCode() == RETRY && getArgOrElse(ARG_RETRY_SCOPE, defaultScope.toString()).equals(scope.toString());
    }

    public boolean isRetry(RetryScope scope, Object reason) {
        String reasonString;
        if (reason instanceof Exception) {
            reasonString = ((Exception) reason).getClass().getSimpleName();
        } else if (reason instanceof Class) {
            reasonString = ((Class<?>) reason).getSimpleName();
        } else {
            reasonString = reason.toString();
        }
        return isRetry(scope) && getArgOrElse(ARG_RETRY_REASON, "").equals(reasonString);
    }

    public boolean isTempMoved() {
        return getMinorCode() == MOVED_TEMPORARILY;
    }

    public boolean isMoved() {
        return getMinorCode() == MOVED_TEMPORARILY || getMinorCode() == MOVED_PERMANENTLY;
    }

    public boolean isTimeout() {
        return isTimeout(this);
    }

    public String getMajorName() {
        return getMajorName(getMajorCode());
    }

    public short getMajorCode() {
        return protocolStatus.getMajorCode().shortValue();
    }

    public void setMajorCode(short majorCode) {
        protocolStatus.setMajorCode((int) majorCode);
    }

    public String getMinorName() {
        return getMinorName(getMinorCode());
    }

    public int getMinorCode() {
        return protocolStatus.getMinorCode();
    }

    public void setMinorCode(int minorCode) {
        protocolStatus.setMinorCode(minorCode);
    }

    public void setMinorCode(int minorCode, String message) {
        setMinorCode(minorCode);
        getArgs().put(getMinorName(), message);
    }

    public String getArgOrElse(@NotNull String name, @NotNull String defaultValue) {
        return getArgs().getOrDefault(name, defaultValue).toString();
    }

    public Map<CharSequence, CharSequence> getArgs() {
        return protocolStatus.getArgs();
    }

    public void setArgs(Map<CharSequence, CharSequence> args) {
        protocolStatus.setArgs(args);
    }

    public String getName() {
        return majorCodes.getOrDefault(getMajorCode(), "unknown") + "/"
                + minorCodes.getOrDefault(getMinorCode(), "unknown");
    }

    @Nullable
    public Object getRetryScope() {
        return getArgs().get(ARG_RETRY_SCOPE);
    }

    @Nullable
    public Object getRetryReason() {
        return getArgs().get(ARG_RETRY_REASON);
    }

    public void upgradeRetry(RetryScope scope) {
        getArgs().put(ARG_RETRY_SCOPE, scope.toString());
    }

    @Override
    public String toString() {
        String minorName = minorCodes.getOrDefault(getMinorCode(), "Unknown");
        String str = minorName + "(" + getMinorCode() + ")";
        if (!getArgs().isEmpty()) {
            List<String> keys = List.of(ARG_RETRY_SCOPE, ARG_RETRY_REASON, ARG_HTTP_CODE);
            String args = getArgs().entrySet().stream()
                    .filter(e -> keys.contains(e.getKey().toString()))
                    .map(e -> e.getKey() + ": " + e.getValue())
                    .collect(Collectors.joining(", "));
            str += " " + args;
        }
        return str;
   }
}
