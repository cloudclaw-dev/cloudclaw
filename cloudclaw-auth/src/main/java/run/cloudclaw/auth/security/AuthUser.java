package run.cloudclaw.auth.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Custom annotation to mark method parameters that should be resolved
 * to the current authenticated user's ID.
 *
 * <p>Used on controller method parameters of type {@link String} to
 * automatically inject the authenticated user's ID from the security context.</p>
 *
 * <p>Example usage:</p>
 * <pre>
 * {@code
 * @GetMapping("/me")
 * public Result<UserInfo> getCurrentUser(@AuthUser String userId) {
 *     return Result.success(userService.getUserInfo(userId));
 * }
 * }
 * </pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthUser {
}
