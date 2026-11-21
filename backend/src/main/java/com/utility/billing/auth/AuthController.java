package com.utility.billing.auth;

import com.utility.billing.common.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/auth")
public class AuthController {
 private final AuthService auth;
 private final PasswordRecoveryService recovery; private final LoginRateLimiter limiter; private final org.springframework.security.crypto.password.PasswordEncoder encoder;
 public AuthController(AuthService auth,PasswordRecoveryService recovery,LoginRateLimiter limiter,org.springframework.security.crypto.password.PasswordEncoder encoder){this.auth=auth;this.recovery=recovery;this.limiter=limiter;this.encoder=encoder;}
 @PostMapping("/admin/login") public ApiResponse<AuthService.TokenPair> admin(@Valid @RequestBody Login r,HttpServletRequest req){return login(r,PortalRole.ADMIN,req);}
 @PostMapping("/operator/login") public ApiResponse<AuthService.TokenPair> operator(@Valid @RequestBody Login r,HttpServletRequest req){return login(r,PortalRole.OPERATOR,req);}
 @PostMapping("/support/login") public ApiResponse<AuthService.TokenPair> support(@Valid @RequestBody Login r,HttpServletRequest req){return login(r,PortalRole.HELP_SUPPORT,req);}
 @PostMapping("/customer/login") public ApiResponse<AuthService.TokenPair> customer(@Valid @RequestBody Login r,HttpServletRequest req){return login(r,PortalRole.CUSTOMER,req);}
 private ApiResponse<AuthService.TokenPair> login(Login r,PortalRole role,HttpServletRequest req){limiter.check(req.getRemoteAddr()+":"+role);return ApiResponse.ok("Signed in",auth.login(r.email(),r.password(),role));}
 @PostMapping("/customer/register") @ResponseStatus(HttpStatus.CREATED) public ApiResponse<Void> register(@Valid @RequestBody Register r){auth.register(r.email(),r.password(),r.fullName(),r.phone());return ApiResponse.ok("Registration received and pending approval",null);}
 @PostMapping("/refresh") public ApiResponse<AuthService.TokenPair> refresh(@Valid @RequestBody Refresh r){return ApiResponse.ok("Token refreshed",auth.refresh(r.refreshToken()));}
 @PostMapping("/logout") public ApiResponse<Void> logout(@Valid @RequestBody Refresh r){auth.logout(r.refreshToken());return ApiResponse.ok("Signed out",null);}
 @PostMapping("/password/forgot") public ApiResponse<Void> forgot(@Valid @RequestBody Forgot r,HttpServletRequest req){recovery.request(r.email(),req.getHeader("Origin"));return ApiResponse.ok("If the account exists, a reset link has been sent",null);}
 @PostMapping("/password/reset") public ApiResponse<Void> reset(@Valid @RequestBody Reset r){recovery.reset(r.token(),r.newPassword(),encoder);return ApiResponse.ok("Password updated",null);}
 public record Login(@Email @NotBlank String email,@NotBlank String password){}
 public record Register(@Email @NotBlank String email,@NotBlank @Size(min=10,max=72) String password,@NotBlank @Size(max=160) String fullName,@Size(max=30) String phone){}
 public record Refresh(@NotBlank String refreshToken){}
 public record Forgot(@Email @NotBlank String email){}
 public record Reset(@NotBlank String token,@NotBlank @Size(min=10,max=72) String newPassword){}
}

